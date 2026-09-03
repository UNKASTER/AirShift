package com.bradj.airshift.parser

import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.shift.ObservedShiftGroups
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

internal object ExcelRosterParser {
    private const val MAX_XML_ENTRY_BYTES = 16 * 1024 * 1024
    private const val MAX_RELEVANT_XML_BYTES = 32 * 1024 * 1024
    private const val MAX_WORKSHEETS = 64

    private val registrationRegex = Regex("B[A-Z0-9]{4}")
    private val fullDateRegex = Regex(
        "(?<!\\d)(\\d{4})\\s*[年./-]\\s*(\\d{1,2})\\s*[月./-]\\s*(\\d{1,2})(?:\\s*日)?(?!\\d)",
    )
    private val partialDateRegex = Regex(
        "(?<!\\d)(\\d{1,2})\\s*[.。/月-]\\s*(\\d{1,2})(?:\\s*日)?(?!\\d)",
    )
    private val assigneeDelimiterRegex = Regex("[\\s\\u00A0,，、;；/|]+")
    private val parentheticalNoteRegex = Regex("[（(][^）)]*[）)]")
    private val shiftGroupEntryRegex = Regex("(\\d{1,2})\\s*([^\\d]+)")

    fun parse(
        input: InputStream,
        userName: String,
        clock: Clock = Clock.systemDefaultZone(),
    ): RosterParseResult {
        require(userName.isNotBlank()) { "姓名不能为空" }
        val workbook = try {
            readWorkbookPackage(input)
        } catch (error: ZipException) {
            throw IllegalArgumentException("无法读取该文件，请选择未加密的 .xlsx Excel 文件", error)
        }
        require(workbook.hasContentTypes && workbook.workbookXml != null && workbook.worksheets.isNotEmpty()) {
            "文件不是有效的 .xlsx Excel 工作簿"
        }

        val sharedStrings = workbook.sharedStringsXml?.let(::parseSharedStrings).orEmpty()
        val uses1904DateSystem = parseUses1904DateSystem(workbook.workbookXml)
        val sheets = workbook.worksheets
            .sortedBy { worksheetSortKey(it.first) }
            .map { (_, bytes) -> parseWorksheet(bytes, sharedStrings) }
        return parseSheets(sheets, userName, clock, uses1904DateSystem)
    }

    internal fun parseSheets(
        sheets: List<ExcelSheet>,
        userName: String,
        clock: Clock = Clock.systemDefaultZone(),
        uses1904DateSystem: Boolean = false,
    ): RosterParseResult {
        require(userName.isNotBlank()) { "姓名不能为空" }
        val recognizedSheets = sheets.mapNotNull { sheet ->
            findHeader(sheet)?.let { header -> RecognizedSheet(sheet, header) }
        }
        require(recognizedSheets.isNotEmpty()) {
            "未找到可识别的排班表头，请确认文件包含机号、航班、时间和接送机人员列"
        }

        val today = LocalDate.now(clock)
        val workbookDate = recognizedSheets.asSequence()
            .mapNotNull { recognized ->
                findRosterDate(
                    rows = recognized.sheet.rows.filter { it.index <= recognized.header.rowIndex },
                    today = today,
                    uses1904DateSystem = uses1904DateSystem,
                )
            }
            .firstOrNull()
        val warnings = mutableListOf<String>()
        if (workbookDate == null) warnings += "未识别到排班日期，暂按今天处理"

        val parsedDates = mutableListOf<LocalDate>()
        val assignments = recognizedSheets.flatMap { recognized ->
            val rosterDate = findRosterDate(
                rows = recognized.sheet.rows.filter { it.index <= recognized.header.rowIndex },
                today = today,
                uses1904DateSystem = uses1904DateSystem,
            ) ?: workbookDate ?: today
            parsedDates += rosterDate
            val vipFlightNumbers = parseVipFlightNumbers(recognized.sheet.rows)
            recognized.sheet.rows.asSequence()
                .filter { it.index > recognized.header.rowIndex }
                .mapNotNull { row ->
                    parseAssignment(
                        row = row,
                        columns = recognized.header.columns,
                        rosterDate = rosterDate,
                        userName = userName,
                        vipFlightNumbers = vipFlightNumbers,
                        uses1904DateSystem = uses1904DateSystem,
                    )
                }
                .toList()
        }.distinctBy(RosterAssignment::stableId)
            .sortedWith(compareBy { it.scheduledArrival ?: it.scheduledDeparture })

        if (assignments.isEmpty()) {
            warnings += "表中没有找到姓名“${userName.trim()}”对应的航班"
        }
        return RosterParseResult(
            assignments = assignments,
            rosterDate = parsedDates.firstOrNull() ?: workbookDate ?: today,
            warnings = warnings,
            observedShiftGroups = recognizedSheets.asSequence()
                .mapNotNull { parseObservedShiftGroups(it.sheet.rows) }
                .firstOrNull(),
        )
    }

    private fun parseAssignment(
        row: ExcelRow,
        columns: Map<RosterColumn, Int>,
        rosterDate: LocalDate,
        userName: String,
        vipFlightNumbers: Set<String>,
        uses1904DateSystem: Boolean,
    ): RosterAssignment? {
        val registration = cleanRegistration(row.cell(columns[RosterColumn.REGISTRATION])?.text.orEmpty())
            ?: return null
        val assignees = row.cell(columns[RosterColumn.ASSIGNEES])?.text.orEmpty().trim()
        if (!containsAssignee(assignees, userName)) return null

        val inbound = cleanFlightNumber(row.cell(columns[RosterColumn.INBOUND_FLIGHT])?.text.orEmpty())
        val outbound = cleanFlightNumber(row.cell(columns[RosterColumn.OUTBOUND_FLIGHT])?.text.orEmpty())
        if (inbound == null && outbound == null) return null

        return RosterAssignment(
            aircraftRegistration = registration,
            aircraftType = row.cell(columns[RosterColumn.AIRCRAFT_TYPE])?.text?.trim()?.takeIf(String::isNotBlank),
            inboundFlight = inbound,
            origin = cleanLocation(row.cell(columns[RosterColumn.ORIGIN])?.text.orEmpty()),
            scheduledArrival = parseTimeCell(
                row.cell(columns[RosterColumn.ARRIVAL_TIME]),
                rosterDate,
                uses1904DateSystem,
            ),
            outboundFlight = outbound,
            destination = cleanLocation(row.cell(columns[RosterColumn.DESTINATION])?.text.orEmpty()),
            scheduledDeparture = parseTimeCell(
                row.cell(columns[RosterColumn.DEPARTURE_TIME]),
                rosterDate,
                uses1904DateSystem,
            ),
            assignees = assignees,
            inboundHasVip = inbound != null && inbound in vipFlightNumbers,
            outboundHasVip = outbound != null && outbound in vipFlightNumbers,
        )
    }

    private fun findHeader(sheet: ExcelSheet): HeaderMapping? = sheet.rows.asSequence()
        .map { row ->
            val columns = row.cells.mapNotNull { (columnIndex, cell) ->
                columnForHeader(cell.text)?.let { column -> column to columnIndex }
            }.toMap()
            HeaderMapping(row.index, columns)
        }
        .filter { mapping ->
            RosterColumn.REGISTRATION in mapping.columns &&
                RosterColumn.ASSIGNEES in mapping.columns &&
                (RosterColumn.INBOUND_FLIGHT in mapping.columns || RosterColumn.OUTBOUND_FLIGHT in mapping.columns) &&
                mapping.columns.size >= 6
        }
        .maxByOrNull { it.columns.size }

    private fun columnForHeader(raw: String): RosterColumn? {
        val normalized = normalizeHeader(raw)
        return RosterColumn.entries.firstOrNull { normalized in it.aliases }
    }

    private fun findRosterDate(
        rows: List<ExcelRow>,
        today: LocalDate,
        uses1904DateSystem: Boolean,
    ): LocalDate? {
        val cells = rows.asSequence().flatMap { it.cells.values.asSequence() }
        cells.mapNotNull { parseFullDate(it.text) }.firstOrNull()?.let { return it }

        rows.asSequence().flatMap { it.cells.values.asSequence() }
            .mapNotNull { cell -> cell.number?.let { excelSerialToDate(it, uses1904DateSystem) } }
            .firstOrNull()
            ?.let { return it }

        return rows.asSequence().flatMap { it.cells.values.asSequence() }
            .mapNotNull { parsePartialDate(it.text, today) }
            .firstOrNull()
    }

    private fun parseFullDate(raw: String): LocalDate? {
        val match = fullDateRegex.find(raw) ?: return null
        return runCatching {
            LocalDate.of(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
            )
        }.getOrNull()
    }

    private fun parsePartialDate(raw: String, today: LocalDate): LocalDate? {
        if (fullDateRegex.containsMatchIn(raw)) return null
        val match = partialDateRegex.find(raw) ?: return null
        val month = match.groupValues[1].toInt()
        val day = match.groupValues[2].toInt()
        return listOf(today.year - 1, today.year, today.year + 1)
            .mapNotNull { year -> runCatching { LocalDate.of(year, month, day) }.getOrNull() }
            .minByOrNull { abs(ChronoUnit.DAYS.between(today, it)) }
    }

    private fun parseTimeCell(
        cell: ExcelCell?,
        rosterDate: LocalDate,
        uses1904DateSystem: Boolean,
    ): LocalDateTime? {
        cell ?: return null
        val number = cell.number
        if (number != null && number.isFinite()) {
            if (number >= 20_000.0) {
                val date = excelSerialToDate(number, uses1904DateSystem) ?: return null
                return LocalDateTime.of(date, excelFractionToTime(number))
            }
            if (number >= 0.0 && number < 1.0) {
                return LocalDateTime.of(rosterDate, excelFractionToTime(number))
            }
            val rounded = number.roundToInt()
            if (abs(number - rounded) < 0.000001 && rounded in 0..2359) {
                return parseRosterTime(rounded.toString().padStart(4, '0'), rosterDate)
            }
        }
        return parseRosterTime(cell.text, rosterDate)
    }

    private fun parseRosterTime(raw: String, date: LocalDate): LocalDateTime? {
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

    private fun excelFractionToTime(serial: Double): LocalTime {
        val fraction = serial - floor(serial)
        val totalMinutes = (fraction * 24 * 60).roundToInt().mod(24 * 60)
        return LocalTime.of(totalMinutes / 60, totalMinutes % 60)
    }

    private fun excelSerialToDate(serial: Double, uses1904DateSystem: Boolean): LocalDate? {
        if (!serial.isFinite() || serial < 20_000.0 || serial > 100_000.0) return null
        val epoch = if (uses1904DateSystem) LocalDate.of(1904, 1, 1) else LocalDate.of(1899, 12, 30)
        return runCatching { epoch.plusDays(floor(serial).toLong()) }.getOrNull()
    }

    /**
     * 读取表格右侧“候机早班 / 候机中班 / 候机夜班”三行，得到当天真实的班组顺序与成员，
     * 供排班日历校正内置班组表与轮转相位。
     *
     * 与 [parseVipFlightNumbers] 一样只扫描正表之外的附加区域，不影响任务行解析。
     * 三行缺任意一行时返回 null——交接班日的半天表格本就没有这些行。
     */
    private fun parseObservedShiftGroups(rows: List<ExcelRow>): ObservedShiftGroups? {
        val lines = mutableMapOf<ShiftLineKind, List<Pair<Int, List<String>>>>()
        for (row in rows) {
            for (cell in row.cells.values) {
                val kind = shiftLineKind(cell.text) ?: continue
                if (lines.containsKey(kind)) continue
                val entries = parseShiftGroupEntries(cell.text)
                if (entries.isNotEmpty()) lines[kind] = entries
            }
        }
        val early = lines[ShiftLineKind.EARLY] ?: return null
        val mid = lines[ShiftLineKind.MID] ?: return null
        val night = lines[ShiftLineKind.NIGHT] ?: return null
        return ObservedShiftGroups(
            early = early.map { it.first },
            mid = mid.map { it.first },
            night = night.map { it.first },
            members = (early + mid + night).toMap(),
        ).takeIf(ObservedShiftGroups::isUsable)
    }

    private fun shiftLineKind(raw: String): ShiftLineKind? {
        val normalized = normalizeHeader(raw)
        return when {
            normalized.startsWith("候机早班") || normalized.startsWith("早班人员") -> ShiftLineKind.EARLY
            normalized.startsWith("候机中班") || normalized.startsWith("中班人员") -> ShiftLineKind.MID
            normalized.startsWith("候机夜班") || normalized.startsWith("候机晚班") ||
                normalized.startsWith("夜班人员") || normalized.startsWith("晚班人员") -> ShiftLineKind.NIGHT
            else -> null
        }
    }

    /**
     * 把“候机早班：1甲子 甲丑5乙子 乙丑 乙寅”解析为有序的 (组号, 成员) 列表。
     * 组号只起分隔作用，出现顺序才决定早几 / 中几 / 晚几。
     */
    private fun parseShiftGroupEntries(raw: String): List<Pair<Int, List<String>>> {
        val firstDigit = raw.indexOfFirst(Char::isDigit)
        if (firstDigit < 0) return emptyList()
        return shiftGroupEntryRegex.findAll(raw.substring(firstDigit)).mapNotNull { match ->
            val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val names = match.groupValues[2]
                .replace(parentheticalNoteRegex, " ")
                .split(assigneeDelimiterRegex)
                .filter(String::isNotBlank)
            names.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toList()
    }

    private enum class ShiftLineKind { EARLY, MID, NIGHT }

    private fun parseVipFlightNumbers(rows: List<ExcelRow>): Set<String> {
        val flights = mutableSetOf<String>()
        rows.forEachIndexed { rowPosition, row ->
            row.cells.forEach { (columnIndex, cell) ->
                val normalized = normalizeHeader(cell.text).uppercase()
                if (!normalized.startsWith("VIP信息") && !normalized.startsWith("要客信息")) return@forEach

                row.cells.filterKeys { it > columnIndex }.values
                    .mapNotNullTo(flights) { cleanFlightNumber(it.text) }
                var blankRows = 0
                for (candidate in rows.drop(rowPosition + 1).take(30)) {
                    val relevant = candidate.cells.filterKeys { it >= columnIndex }.values.toList()
                    if (relevant.isEmpty()) {
                        blankRows++
                        if (blankRows >= 2) break
                        continue
                    }
                    blankRows = 0
                    if (relevant.any { isVipStopMarker(it.text) }) break
                    relevant.mapNotNullTo(flights) { cleanFlightNumber(it.text) }
                }
            }
        }
        return flights
    }

    private fun isVipStopMarker(raw: String): Boolean {
        val normalized = normalizeHeader(raw).uppercase()
        return normalized == "CIP" ||
            normalized.startsWith("候机早班") ||
            normalized.startsWith("候机中班") ||
            normalized.startsWith("候机夜班") ||
            normalized.startsWith("候机晚班") ||
            normalized.startsWith("早班人员") ||
            normalized.startsWith("中班人员") ||
            normalized.startsWith("晚班人员")
    }

    private fun cleanRegistration(raw: String): String? {
        val normalized = raw.uppercase().replace(Regex("[^A-Z0-9]"), "")
        return registrationRegex.find(normalized)?.value
    }

    private fun cleanFlightNumber(raw: String): String? = RosterTableParser.cleanFlightNumber(raw)

    private fun cleanLocation(raw: String): String? =
        raw.replace(Regex("[0-9+\\s]"), "").takeIf(String::isNotBlank)

    private fun containsAssignee(raw: String, userName: String): Boolean {
        val compactUserName = normalizeName(userName)
        if (compactUserName.isBlank()) return false
        val withoutNotes = raw.replace(parentheticalNoteRegex, " ")
        val hasDelimiter = assigneeDelimiterRegex.containsMatchIn(withoutNotes)
        val names = withoutNotes.split(assigneeDelimiterRegex)
            .map(::normalizeName)
            .filter(String::isNotBlank)
        if (hasDelimiter) return names.any { it == compactUserName }
        val compactAssignees = normalizeName(withoutNotes)
        return compactAssignees == compactUserName ||
            (compactAssignees.length >= compactUserName.length * 2 && compactAssignees.contains(compactUserName))
    }

    private fun normalizeName(raw: String): String = raw.replace(Regex("[\\s\\p{P}\\p{S}]"), "")

    private fun normalizeHeader(raw: String): String = raw.replace(Regex("[\\s\\p{P}\\p{S}]"), "").trim()

    private fun readWorkbookPackage(input: InputStream): WorkbookPackage {
        var hasContentTypes = false
        var workbookXml: ByteArray? = null
        var sharedStringsXml: ByteArray? = null
        val worksheets = mutableListOf<Pair<String, ByteArray>>()
        var totalRelevantBytes = 0

        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                when {
                    name == "[Content_Types].xml" -> hasContentTypes = true
                    name == "xl/workbook.xml" -> {
                        val bytes = readEntry(zip, MAX_XML_ENTRY_BYTES, totalRelevantBytes)
                        workbookXml = bytes
                        totalRelevantBytes += bytes.size
                    }
                    name == "xl/sharedStrings.xml" -> {
                        val bytes = readEntry(zip, MAX_XML_ENTRY_BYTES, totalRelevantBytes)
                        sharedStringsXml = bytes
                        totalRelevantBytes += bytes.size
                    }
                    name.startsWith("xl/worksheets/") && name.endsWith(".xml") -> {
                        require(worksheets.size < MAX_WORKSHEETS) { "工作表数量过多，无法安全读取" }
                        val bytes = readEntry(zip, MAX_XML_ENTRY_BYTES, totalRelevantBytes)
                        totalRelevantBytes += bytes.size
                        worksheets += name to bytes
                    }
                }
                zip.closeEntry()
            }
        }
        return WorkbookPackage(hasContentTypes, workbookXml, sharedStringsXml, worksheets)
    }

    private fun readEntry(zip: ZipInputStream, perEntryLimit: Int, alreadyRead: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = zip.read(buffer)
            if (count < 0) break
            require(output.size() + count <= perEntryLimit) { "Excel 工作表过大，无法安全读取" }
            require(alreadyRead + output.size() + count <= MAX_RELEVANT_XML_BYTES) {
                "Excel 工作簿过大，无法安全读取"
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val handler = SharedStringsHandler()
        parseXml(bytes, handler)
        return handler.strings
    }

    private fun parseUses1904DateSystem(bytes: ByteArray): Boolean {
        val handler = WorkbookPropertiesHandler()
        parseXml(bytes, handler)
        return handler.uses1904DateSystem
    }

    private fun parseWorksheet(bytes: ByteArray, sharedStrings: List<String>): ExcelSheet {
        val handler = WorksheetHandler(sharedStrings)
        parseXml(bytes, handler)
        return ExcelSheet(handler.rows)
    }

    private fun parseXml(bytes: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        val reader = factory.newSAXParser().xmlReader
        reader.contentHandler = handler
        reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> InputSource(StringReader("")) }
        reader.parse(InputSource(ByteArrayInputStream(bytes)))
    }

    private fun worksheetSortKey(path: String): Int =
        Regex("sheet(\\d+)\\.xml$").find(path)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE

    private fun ExcelRow.cell(columnIndex: Int?): ExcelCell? = columnIndex?.let(cells::get)

    private enum class RosterColumn(val aliases: Set<String>) {
        REGISTRATION(setOf("机号", "机尾号", "飞机号", "飞机注册号")),
        AIRCRAFT_TYPE(setOf("机型", "飞机机型")),
        INBOUND_FLIGHT(setOf("进港航班", "进港航班号", "进航班号")),
        ORIGIN(setOf("前站", "始发站", "进港前站")),
        ARRIVAL_TIME(setOf("预落", "预计落地", "计划落地", "计划到达")),
        OUTBOUND_FLIGHT(setOf("出港航班", "出港航班号", "出航班号")),
        DESTINATION(setOf("到站", "后站", "目的站")),
        DEPARTURE_TIME(setOf("计离", "计划离港", "计划起飞", "预计离港")),
        ASSIGNEES(setOf("接送机人员", "接送人员", "送机人员", "保障人员", "接机人员")),
    }

    private data class WorkbookPackage(
        val hasContentTypes: Boolean,
        val workbookXml: ByteArray?,
        val sharedStringsXml: ByteArray?,
        val worksheets: List<Pair<String, ByteArray>>,
    )

    internal data class ExcelSheet(val rows: List<ExcelRow>)

    internal data class ExcelRow(
        val index: Int,
        val cells: Map<Int, ExcelCell>,
    )

    internal data class ExcelCell(
        val text: String,
        val number: Double?,
    )

    private data class HeaderMapping(
        val rowIndex: Int,
        val columns: Map<RosterColumn, Int>,
    )

    private data class RecognizedSheet(
        val sheet: ExcelSheet,
        val header: HeaderMapping,
    )

    private class SharedStringsHandler : DefaultHandler() {
        val strings = mutableListOf<String>()
        private var insideItem = false
        private var insideText = false
        private var value = StringBuilder()

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            when (elementName(localName, qName)) {
                "si" -> {
                    insideItem = true
                    value = StringBuilder()
                }
                "t" -> if (insideItem) insideText = true
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (insideText) value.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            when (elementName(localName, qName)) {
                "t" -> insideText = false
                "si" -> {
                    strings += value.toString()
                    insideItem = false
                }
            }
        }
    }

    private class WorkbookPropertiesHandler : DefaultHandler() {
        var uses1904DateSystem = false

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            if (elementName(localName, qName) != "workbookPr") return
            val value = attributes?.attribute("date1904")
            uses1904DateSystem = value == "1" || value.equals("true", ignoreCase = true)
        }
    }

    private class WorksheetHandler(private val sharedStrings: List<String>) : DefaultHandler() {
        val rows = mutableListOf<ExcelRow>()
        private var rowIndex = 0
        private var nextRowIndex = 1
        private var currentRow: MutableMap<Int, ExcelCell>? = null
        private var currentColumn = -1
        private var nextColumn = 0
        private var currentType: String? = null
        private var readingValue = false
        private var readingInlineText = false
        private var value = StringBuilder()
        private var inlineText = StringBuilder()

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            when (elementName(localName, qName)) {
                "row" -> {
                    rowIndex = attributes?.attribute("r")?.toIntOrNull() ?: nextRowIndex
                    nextRowIndex = rowIndex + 1
                    nextColumn = 0
                    currentRow = mutableMapOf()
                }
                "c" -> {
                    currentColumn = attributes?.attribute("r")?.let(::columnIndexFromReference) ?: nextColumn
                    nextColumn = currentColumn + 1
                    currentType = attributes?.attribute("t")
                    value = StringBuilder()
                    inlineText = StringBuilder()
                }
                "v" -> if (currentColumn >= 0) readingValue = true
                "t" -> if (currentColumn >= 0 && currentType == "inlineStr") readingInlineText = true
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (readingValue) value.append(ch, start, length)
            if (readingInlineText) inlineText.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            when (elementName(localName, qName)) {
                "v" -> readingValue = false
                "t" -> readingInlineText = false
                "c" -> {
                    createCell(currentType, value.toString(), inlineText.toString(), sharedStrings)?.let { cell ->
                        currentRow?.set(currentColumn, cell)
                    }
                    currentColumn = -1
                    currentType = null
                }
                "row" -> {
                    currentRow?.let { cells -> rows += ExcelRow(rowIndex, cells.toMap()) }
                    currentRow = null
                }
            }
        }
    }

    private fun createCell(
        type: String?,
        rawValue: String,
        inlineText: String,
        sharedStrings: List<String>,
    ): ExcelCell? {
        if (type == "e") return null
        val text = when (type) {
            "s" -> rawValue.trim().toIntOrNull()?.let(sharedStrings::getOrNull).orEmpty()
            "inlineStr" -> inlineText
            "b" -> if (rawValue.trim() == "1") "TRUE" else "FALSE"
            else -> rawValue
        }
        val number = if (type == null || type == "n") rawValue.trim().toDoubleOrNull() else null
        return ExcelCell(text, number).takeIf { it.text.isNotBlank() || it.number != null }
    }

    private fun columnIndexFromReference(reference: String): Int {
        var value = 0
        var foundLetter = false
        for (character in reference) {
            if (!character.isLetter()) {
                if (foundLetter) break
                continue
            }
            foundLetter = true
            value = value * 26 + (character.uppercaseChar() - 'A' + 1)
        }
        return (value - 1).coerceAtLeast(0)
    }

    private fun elementName(localName: String?, qName: String?): String =
        localName?.takeIf(String::isNotEmpty) ?: qName.orEmpty().substringAfter(':')

    private fun Attributes.attribute(name: String): String? = getValue(name) ?: getValue("", name)
}
