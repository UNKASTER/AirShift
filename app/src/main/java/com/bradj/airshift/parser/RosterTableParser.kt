package com.bradj.airshift.parser

import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.RosterSupplement
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs

data class OcrToken(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0
}

data class RosterParseResult(
    val assignments: List<RosterAssignment>,
    val rosterDate: LocalDate,
    val supplement: RosterSupplement,
    val warnings: List<String>,
)

object RosterTableParser {
    private val headers = listOf(
        "机号",
        "机型",
        "进港航班",
        "前站",
        "预落",
        "出港航班",
        "到站",
        "计离",
        "接送机人员",
    )
    private val templateEdges = doubleArrayOf(0.000, 0.090, 0.160, 0.293, 0.394, 0.469, 0.640, 0.723, 0.778, 1.000)
    private val templateCenters = DoubleArray(headers.size) { index ->
        (templateEdges[index] + templateEdges[index + 1]) / 2
    }
    private val dateRegex = Regex("(?<!\\d)(\\d{1,2})[.。/月-](\\d{1,2})(?:日)?(?!\\d)")
    private val registrationRegex = Regex("^B[A-Z0-9]{3,5}$")
    private val flightRegex = Regex("[A-Z]{2,3}\\d{3,4}")

    fun parse(
        tokens: List<OcrToken>,
        imageWidth: Int,
        userName: String,
        clock: Clock = Clock.systemDefaultZone(),
    ): RosterParseResult {
        require(userName.isNotBlank()) { "姓名不能为空" }
        val warnings = mutableListOf<String>()
        val headerMatches = tokens.mapNotNull { token ->
            val normalized = normalizeText(token.text)
            headers.indexOfFirst { normalized.contains(it) }
                .takeIf { it >= 0 }
                ?.let { index -> index to token }
        }.distinctBy { it.first }

        val headerY = headerMatches.map { it.second.centerY }.medianOrNull()
            ?: tokens.minOfOrNull { it.centerY }
            ?: 0.0
        if (headerMatches.size in 1..4) {
            warnings += "表头识别不完整，已按固定模板位置补齐"
        }

        val geometry = fitColumnGeometry(headerMatches, imageWidth)
        val supplement = parseSupplement(tokens, geometry.edges.last(), imageWidth)
        val tableTokens = tokens.filter {
            it.centerY > headerY && it.centerX in geometry.edges.first()..geometry.edges.last()
        }
        val rows = clusterRows(tableTokens)

        if (rows.isEmpty()) {
            return RosterParseResult(
                assignments = emptyList(),
                rosterDate = resolveRosterDate(tokens, clock, warnings),
                supplement = supplement,
                warnings = warnings + "未识别到排班数据行",
            )
        }

        val rosterDate = resolveRosterDate(tokens, clock, warnings)
        val compactName = normalizeName(userName)
        val rowValues = rows.map { row ->
            val cells = Array(headers.size) { mutableListOf<OcrToken>() }
            row.forEach { token ->
                val column = columnFor(token.centerX, geometry.edges)
                if (column in cells.indices) cells[column] += token
            }
            cells.map { cell ->
                cell.sortedBy { it.left }.joinToString("") { normalizeText(it.text) }
            }
        }
        val teamSignatures = rowValues
            .map { normalizeName(it[8]) }
            .filter { it.contains(compactName) }
            .flatMap { assignees ->
                val start = assignees.indexOf(compactName)
                listOf(
                    assignees.substring(0, start),
                    assignees.substring(start + compactName.length),
                ).filter { it.length >= 3 }
            }
            .distinct()

        val assignments = rowValues.mapNotNull { values ->
            val assignees = normalizeName(values[8])
            val assignedByName = assignees.contains(compactName) || containsNearName(assignees, compactName)
            val assignedByTeam = teamSignatures.any { signature -> assignees.contains(signature) }
            if (!assignedByName && !assignedByTeam) return@mapNotNull null

            val inbound = cleanFlightNumber(values[2])
            val outbound = cleanFlightNumber(values[5])
            if (inbound == null && outbound == null) return@mapNotNull null

            RosterAssignment(
                aircraftRegistration = cleanRegistration(values[0]),
                aircraftType = values[1].takeIf { it.isNotBlank() },
                inboundFlight = inbound,
                origin = cleanLocation(values[3]),
                scheduledArrival = parseRosterTime(recoverTime(values[3], values[4]), rosterDate),
                outboundFlight = outbound,
                destination = cleanLocation(values[6]),
                scheduledDeparture = parseRosterTime(recoverTime(values[6], values[7]), rosterDate),
                assignees = values[8],
            )
        }.sortedWith(compareBy { it.scheduledArrival ?: it.scheduledDeparture })

        if (assignments.isEmpty()) warnings += "表中没有找到姓名“${userName.trim()}”对应的航班"
        return RosterParseResult(assignments, rosterDate, supplement, warnings)
    }

    fun cleanFlightNumber(raw: String): String? {
        val cleaned = raw.uppercase().replace(Regex("[^A-Z0-9]"), "")
        return flightRegex.find(cleaned)?.value
    }

    fun parseRosterTime(raw: String, date: LocalDate): LocalDateTime? {
        val normalized = raw.replace(Regex("[^0-9+]"), "")
        val digits = normalized.filter(Char::isDigit)
        if (digits.length !in 3..4) return null
        val padded = digits.padStart(4, '0')
        val hour = padded.substring(0, 2).toIntOrNull() ?: return null
        val minute = padded.substring(2, 4).toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        val actualDate = if ('+' in normalized) date.plusDays(1) else date
        return LocalDateTime.of(actualDate, LocalTime.of(hour, minute))
    }

    private fun resolveRosterDate(tokens: List<OcrToken>, clock: Clock, warnings: MutableList<String>): LocalDate {
        val today = LocalDate.now(clock)
        val joinedRows = clusterRows(tokens).map { row ->
            row.sortedBy { it.left }.joinToString("") { normalizeText(it.text) }
        }
        val match = (tokens.map { normalizeText(it.text) } + joinedRows).asSequence()
            .mapNotNull { dateRegex.find(it) }
            .firstOrNull()
        if (match == null) {
            warnings += "未识别到排班日期，暂按今天处理"
            return today
        }
        val month = match.groupValues[1].toInt()
        val day = match.groupValues[2].toInt()
        return listOf(today.year - 1, today.year, today.year + 1)
            .mapNotNull { year -> runCatching { LocalDate.of(year, month, day) }.getOrNull() }
            .minByOrNull { abs(it.toEpochDay() - today.toEpochDay()) }
            ?: today
    }

    private fun fitColumnGeometry(matches: List<Pair<Int, OcrToken>>, imageWidth: Int): ColumnGeometry {
        if (matches.size < 2) {
            val tableLeft = imageWidth * 0.032
            val tableWidth = imageWidth * 0.56
            return ColumnGeometry(
                centers = DoubleArray(headers.size) { index -> tableLeft + tableWidth * templateCenters[index] },
                edges = DoubleArray(templateEdges.size) { index -> tableLeft + tableWidth * templateEdges[index] },
            )
        }
        val points = matches.map { (index, token) -> templateCenters[index] to token.centerX }
        val meanTemplate = points.map { it.first }.average()
        val meanX = points.map { it.second }.average()
        val denominator = points.sumOf { (template, _) -> (template - meanTemplate) * (template - meanTemplate) }
        val slope = points.sumOf { (template, x) -> (template - meanTemplate) * (x - meanX) } / denominator
        val intercept = meanX - slope * meanTemplate
        val centers = DoubleArray(headers.size) { index -> intercept + slope * templateCenters[index] }
        matches.forEach { (index, token) -> centers[index] = token.centerX }
        val edges = DoubleArray(headers.size + 1)
        edges[0] = intercept + slope * templateEdges.first()
        for (index in 1 until edges.lastIndex) {
            edges[index] = (centers[index - 1] + centers[index]) / 2.0
        }
        edges[edges.lastIndex] = intercept + slope * templateEdges.last()
        return ColumnGeometry(
            centers = centers,
            edges = edges,
        )
    }

    private fun columnFor(x: Double, edges: DoubleArray): Int =
        (0 until edges.lastIndex).firstOrNull { index -> x >= edges[index] && x < edges[index + 1] } ?: -1

    private fun clusterRows(tokens: List<OcrToken>): List<List<OcrToken>> {
        if (tokens.isEmpty()) return emptyList()
        val medianHeight = tokens.map { (it.bottom - it.top).toDouble() }.medianOrNull() ?: 12.0
        val threshold = maxOf(4.0, medianHeight * 0.58)
        val clusters = mutableListOf<MutableList<OcrToken>>()
        tokens.sortedBy { it.centerY }.forEach { token ->
            val current = clusters.lastOrNull()
            val currentCenter = current?.map { it.centerY }?.average()
            if (current == null || currentCenter == null || abs(token.centerY - currentCenter) > threshold) {
                clusters += mutableListOf(token)
            } else {
                current += token
            }
        }
        return clusters
    }

    private fun parseSupplement(
        tokens: List<OcrToken>,
        tableRight: Double,
        imageWidth: Int,
    ): RosterSupplement {
        val rightMargin = maxOf(12.0, imageWidth * 0.008)
        val rightTokens = tokens.filter { it.centerX >= tableRight - rightMargin }
        val anchors = rightTokens.mapNotNull { token ->
            sectionFor(token.text)?.let { section -> SectionAnchor(section, token) }
        }.distinctBy { it.section }
        if (anchors.isEmpty()) return RosterSupplement()

        val anchorHeight = anchors.map { (it.token.bottom - it.token.top).toDouble() }
            .medianOrNull()
            ?.coerceAtLeast(1.0)
            ?: 12.0
        val sameHeaderRow = anchors.size > 1 &&
            anchors.maxOf { it.token.centerY } - anchors.minOf { it.token.centerY } <= anchorHeight * 1.5

        val values = if (sameHeaderRow) {
            collectHorizontalSections(rightTokens, anchors, imageWidth)
        } else {
            collectVerticalSections(rightTokens, anchors)
        }
        return RosterSupplement(
            vipInfo = values[SupplementSection.VIP].orEmpty(),
            earlyShift = values[SupplementSection.EARLY].orEmpty(),
            middleShift = values[SupplementSection.MIDDLE].orEmpty(),
            lateShift = values[SupplementSection.LATE].orEmpty(),
        )
    }

    private fun collectHorizontalSections(
        tokens: List<OcrToken>,
        anchors: List<SectionAnchor>,
        imageWidth: Int,
    ): Map<SupplementSection, List<String>> {
        val sortedAnchors = anchors.sortedBy { it.token.centerX }
        val leftEdge = minOf(tokens.minOfOrNull { it.left.toDouble() } ?: 0.0, sortedAnchors.first().token.left.toDouble())
        val edges = DoubleArray(sortedAnchors.size + 1)
        edges[0] = leftEdge
        for (index in 1 until sortedAnchors.size) {
            edges[index] = (sortedAnchors[index - 1].token.centerX + sortedAnchors[index].token.centerX) / 2.0
        }
        edges[sortedAnchors.size] = imageWidth.toDouble()
        val anchorTokens = anchors.map { it.token }.toSet()
        val rows = clusterRows(tokens.filterNot(anchorTokens::contains))

        return sortedAnchors.associate { anchor ->
            val index = sortedAnchors.indexOf(anchor)
            val residual = valueInsideAnchor(anchor)
            val rowValues = rows.mapNotNull { row ->
                row.filter {
                    it.centerX >= edges[index] && it.centerX < edges[index + 1] &&
                        it.centerY > anchor.token.bottom
                }.toSectionLine()
            }
            anchor.section to (listOfNotNull(residual) + rowValues).distinct()
        }
    }

    private fun collectVerticalSections(
        tokens: List<OcrToken>,
        anchors: List<SectionAnchor>,
    ): Map<SupplementSection, List<String>> {
        val sortedAnchors = anchors.sortedBy { it.token.centerY }
        val anchorTokens = anchors.map { it.token }.toSet()
        val rows = clusterRows(tokens.filterNot(anchorTokens::contains))
        return sortedAnchors.associate { anchor ->
            val index = sortedAnchors.indexOf(anchor)
            val nextTop = sortedAnchors.getOrNull(index + 1)?.token?.top?.toDouble() ?: Double.POSITIVE_INFINITY
            val residual = valueInsideAnchor(anchor)
            val candidates = rows.mapNotNull { row ->
                row.filter {
                    it.centerY >= anchor.token.centerY && it.centerY < nextTop &&
                        (it.centerY > anchor.token.bottom || it.left >= anchor.token.right)
                }.takeIf { it.isNotEmpty() }?.let { filtered ->
                    filtered.map { it.centerY }.average() to filtered.toSectionLine()
                }
            }.mapNotNull { (centerY, line) -> line?.let { centerY to it } }
            val sameLine = candidates.filter { (centerY, _) ->
                kotlin.math.abs(centerY - anchor.token.centerY) <= (anchor.token.bottom - anchor.token.top) * 0.75
            }
            val rowValues = when {
                residual != null -> emptyList()
                sameLine.isNotEmpty() -> sameLine.map { it.second }
                else -> candidates.take(1).map { it.second }
            }
            anchor.section to (listOfNotNull(residual) + rowValues).distinct()
        }
    }

    private fun List<OcrToken>.toSectionLine(): String? =
        sortedBy { it.left }
            .joinToString(" ") { it.text.trim() }
            .trim()
            .trimStart(':', '：', '-', '—')
            .trim()
            .takeIf { it.isNotBlank() && sectionFor(it) == null }

    private fun valueInsideAnchor(anchor: SectionAnchor): String? {
        val normalized = normalizeText(anchor.token.text)
        val prefix = anchor.section.aliases.sortedByDescending { it.length }
            .firstOrNull(normalized::startsWith)
            ?: return null
        val value = normalized.removePrefix(prefix)
        return value.trimStart(':', '：', '-', '—').trim().takeIf { it.isNotBlank() }
    }

    private fun sectionFor(raw: String): SupplementSection? {
        val normalized = normalizeText(raw).trimStart(':', '：', '-', '—')
        return SupplementSection.entries.firstOrNull { section ->
            section.aliases.any(normalized::startsWith)
        }
    }

    private fun cleanRegistration(raw: String): String =
        raw.uppercase().replace(Regex("[^A-Z0-9]"), "")

    private fun normalizeName(raw: String): String =
        raw.replace(Regex("[\\s\\p{P}\\p{S}]"), "")

    private fun cleanLocation(raw: String): String? =
        raw.replace(Regex("[0-9+\\s]"), "").takeIf { it.isNotBlank() }

    private fun recoverTime(precedingCell: String, timeCell: String): String {
        val timeDigits = timeCell.filter(Char::isDigit)
        if (timeDigits.length != 3) return timeCell
        val leakedLeadingDigit = precedingCell.takeLastWhile(Char::isDigit).takeLast(1)
        return leakedLeadingDigit + timeCell
    }

    private fun containsNearName(text: String, name: String): Boolean {
        if (name.length < 3 || text.length < name.length) return false
        return text.windowed(name.length).any { candidate ->
            candidate.zip(name).count { (left, right) -> left != right } <= 1
        }
    }

    private fun normalizeText(raw: String): String = raw.replace(" ", "").trim()

    private fun List<Double>.medianOrNull(): Double? {
        if (isEmpty()) return null
        val sorted = sorted()
        return if (size % 2 == 0) (sorted[size / 2 - 1] + sorted[size / 2]) / 2 else sorted[size / 2]
    }

    private data class ColumnGeometry(
        val centers: DoubleArray,
        val edges: DoubleArray,
    )

    private data class SectionAnchor(
        val section: SupplementSection,
        val token: OcrToken,
    )

    private enum class SupplementSection(val aliases: List<String>) {
        VIP(listOf("要客信息", "要客")),
        EARLY(listOf("候机早班", "早班人员", "早班")),
        MIDDLE(listOf("候机中班", "中班人员", "中班")),
        LATE(listOf("候机夜航", "候机晚班", "晚班人员", "夜航", "晚班")),
    }
}
