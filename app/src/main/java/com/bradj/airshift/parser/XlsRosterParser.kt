package com.bradj.airshift.parser

import org.apache.poi.hssf.eventusermodel.FormatTrackingHSSFListener
import org.apache.poi.hssf.eventusermodel.HSSFEventFactory
import org.apache.poi.hssf.eventusermodel.HSSFListener
import org.apache.poi.hssf.eventusermodel.HSSFRequest
import org.apache.poi.hssf.record.BOFRecord
import org.apache.poi.hssf.record.BoolErrRecord
import org.apache.poi.hssf.record.DateWindow1904Record
import org.apache.poi.hssf.record.FormulaRecord
import org.apache.poi.hssf.record.LabelRecord
import org.apache.poi.hssf.record.LabelSSTRecord
import org.apache.poi.hssf.record.MulRKRecord
import org.apache.poi.hssf.record.NumberRecord
import org.apache.poi.hssf.record.RKRecord
import org.apache.poi.hssf.record.Record
import org.apache.poi.hssf.record.SSTRecord
import org.apache.poi.hssf.record.StringRecord
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.ss.usermodel.CellType
import java.io.File
import java.math.BigDecimal
import java.time.Clock
import java.util.Locale

internal object XlsRosterParser {
    private const val MAX_SHEETS = 64
    private const val MAX_ROWS_PER_SHEET = 10_000
    private const val MAX_CELLS_PER_SHEET = 100_000

    fun parse(
        file: File,
        userName: String,
        clock: Clock = Clock.systemDefaultZone(),
    ): RosterParseResult {
        require(userName.isNotBlank()) { "姓名不能为空" }
        val workbook = readWorkbook(file)
        return ExcelRosterParser.parseSheets(
            sheets = workbook.sheets,
            userName = userName,
            clock = clock,
            uses1904DateSystem = workbook.uses1904DateSystem,
        )
    }

    private fun readWorkbook(file: File): XlsWorkbook {
        val collector = BiffWorkbookCollector()
        val formatListener = FormatTrackingHSSFListener(collector, Locale.CHINA)
        collector.formatListener = formatListener
        val request = HSSFRequest().apply { addListenerForAllRecords(formatListener) }

        try {
            POIFSFileSystem(file, true).use { fileSystem ->
                HSSFEventFactory().processWorkbookEvents(request, fileSystem)
            }
        } catch (error: Exception) {
            throw IllegalArgumentException("无法读取该文件，请选择未加密且格式完整的 .xls Excel 文件", error)
        }

        return XlsWorkbook(
            sheets = collector.toSheets(),
            uses1904DateSystem = collector.uses1904DateSystem,
        )
    }

    private data class XlsWorkbook(
        val sheets: List<ExcelRosterParser.ExcelSheet>,
        val uses1904DateSystem: Boolean,
    )

    private class BiffWorkbookCollector : HSSFListener {
        lateinit var formatListener: FormatTrackingHSSFListener
        var uses1904DateSystem: Boolean = false
            private set

        private val sheetRows = mutableListOf<MutableMap<Int, MutableMap<Int, ExcelRosterParser.ExcelCell>>>()
        private var currentRows: MutableMap<Int, MutableMap<Int, ExcelRosterParser.ExcelCell>>? = null
        private var currentCellCount = 0
        private var sharedStrings: SSTRecord? = null
        private var pendingFormulaString: CellPosition? = null

        override fun processRecord(record: Record) {
            when (record) {
                is BOFRecord -> beginSubstream(record)
                is DateWindow1904Record -> uses1904DateSystem = record.windowing.toInt() == 1
                is SSTRecord -> sharedStrings = record
                is LabelSSTRecord -> {
                    val text = sharedStrings?.getString(record.sstIndex)?.string.orEmpty()
                    addCell(record.row, record.column.toInt(), text, null)
                }
                is LabelRecord -> addCell(record.row, record.column.toInt(), record.value, null)
                is NumberRecord -> addNumber(record.row, record.column.toInt(), record.value, record)
                is RKRecord -> addNumber(record.row, record.column.toInt(), record.rkNumber, record)
                is MulRKRecord -> addMultipleRk(record)
                is FormulaRecord -> addFormula(record)
                is StringRecord -> {
                    pendingFormulaString?.let { position ->
                        addCell(position.row, position.column, record.string, null)
                    }
                    pendingFormulaString = null
                }
                is BoolErrRecord -> if (record.isBoolean) {
                    addCell(record.row, record.column.toInt(), record.booleanValue.toString(), null)
                }
            }
        }

        fun toSheets(): List<ExcelRosterParser.ExcelSheet> = sheetRows.map { rows ->
            ExcelRosterParser.ExcelSheet(
                rows.entries.sortedBy(Map.Entry<Int, *>::key).map { (rowIndex, cells) ->
                    ExcelRosterParser.ExcelRow(rowIndex, cells.toMap())
                },
            )
        }

        private fun beginSubstream(record: BOFRecord) {
            pendingFormulaString = null
            if (record.type != BOFRecord.TYPE_WORKSHEET) {
                currentRows = null
                return
            }
            require(sheetRows.size < MAX_SHEETS) { "工作表数量过多，无法安全读取" }
            currentRows = mutableMapOf<Int, MutableMap<Int, ExcelRosterParser.ExcelCell>>().also(sheetRows::add)
            currentCellCount = 0
        }

        private fun addMultipleRk(record: MulRKRecord) {
            repeat(record.numColumns) { offset ->
                val numberRecord = NumberRecord().apply {
                    row = record.row
                    column = (record.firstColumn + offset).toShort()
                    xfIndex = record.getXFAt(offset)
                    value = record.getRKNumberAt(offset)
                }
                addNumber(numberRecord.row, numberRecord.column.toInt(), numberRecord.value, numberRecord)
            }
        }

        private fun addFormula(record: FormulaRecord) {
            pendingFormulaString = null
            when (record.cachedResultTypeEnum) {
                CellType.STRING -> pendingFormulaString = CellPosition(record.row, record.column.toInt())
                CellType.NUMERIC -> addNumber(record.row, record.column.toInt(), record.value, record)
                CellType.BOOLEAN -> addCell(
                    record.row,
                    record.column.toInt(),
                    record.cachedBooleanValue.toString(),
                    null,
                )
                else -> Unit
            }
        }

        private fun addNumber(
            row: Int,
            column: Int,
            number: Double,
            record: org.apache.poi.hssf.record.CellValueRecordInterface,
        ) {
            val formatted = runCatching { formatListener.formatNumberDateCell(record) }
                .getOrElse { BigDecimal.valueOf(number).stripTrailingZeros().toPlainString() }
            addCell(row, column, formatted, number)
        }

        private fun addCell(row: Int, column: Int, text: String, number: Double?) {
            val rows = currentRows ?: return
            if (row !in 0 until MAX_ROWS_PER_SHEET || column !in 0..255) return
            val cells = rows.getOrPut(row + 1) { mutableMapOf() }
            if (column !in cells) {
                require(currentCellCount < MAX_CELLS_PER_SHEET) { "Excel 工作表单元格过多，无法安全读取" }
                currentCellCount++
            }
            cells[column] = ExcelRosterParser.ExcelCell(text, number)
        }
    }

    private data class CellPosition(val row: Int, val column: Int)
}
