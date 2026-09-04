package com.bradj.airshift.parser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream

object ExcelRosterReader {
    private const val MAX_XLS_FILE_BYTES = 256L * 1024 * 1024
    private val oleSignature = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
    )

    /**
     * 在 IO 线程读取并解析 `.xls` / `.xlsx`。按文件签名分流，不信任扩展名或 MIME。
     * 调用方的协程被取消时结果被丢弃，不会再落库。
     */
    suspend fun read(context: Context, uri: Uri, userName: String): RosterParseResult {
        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                val buffered = BufferedInputStream(input)
                val signature = readSignature(buffered)
                when {
                    signature.contentEquals(oleSignature) -> parseXls(
                        buffered,
                        appContext.cacheDir,
                        userName,
                    )
                    signature.size >= 2 && signature[0] == 0x50.toByte() && signature[1] == 0x4B.toByte() ->
                        ExcelRosterParser.parse(buffered, userName)
                    else -> error("文件不是有效的 .xls 或 .xlsx Excel 工作簿")
                }
            } ?: error("无法打开所选 Excel 文件")
        }
    }

    private fun parseXls(input: InputStream, cacheDir: File, userName: String): RosterParseResult {
        val temporaryFile = File.createTempFile("airshift-roster-", ".xls", cacheDir)
        try {
            temporaryFile.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                var totalBytes = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    totalBytes += count
                    require(totalBytes <= MAX_XLS_FILE_BYTES) { "Excel 文件超过 256 MB，无法安全读取" }
                    output.write(buffer, 0, count)
                }
            }
            return XlsRosterParser.parse(temporaryFile, userName)
        } finally {
            temporaryFile.delete()
        }
    }

    private fun readSignature(input: BufferedInputStream): ByteArray {
        input.mark(oleSignature.size)
        val signature = ByteArray(oleSignature.size)
        var offset = 0
        while (offset < signature.size) {
            val count = input.read(signature, offset, signature.size - offset)
            if (count < 0) break
            offset += count
        }
        input.reset()
        return signature.copyOf(offset)
    }
}
