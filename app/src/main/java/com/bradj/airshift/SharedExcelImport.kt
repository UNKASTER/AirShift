package com.bradj.airshift

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val XLS_MIME_TYPE = "application/vnd.ms-excel"
internal const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
internal val SUPPORTED_EXCEL_MIME_TYPES = listOf(XLS_MIME_TYPE, XLSX_MIME_TYPE)

internal sealed interface SharedExcelIntentResult {
    data class File(val uri: Uri) : SharedExcelIntentResult
    data class Rejected(val message: String) : SharedExcelIntentResult
}

internal object SharedExcelIntentParser {
    fun parse(intent: Intent): SharedExcelIntentResult? {
        if (intent.action != Intent.ACTION_SEND) return null
        if (intent.type !in SUPPORTED_EXCEL_MIME_TYPES) {
            return SharedExcelIntentResult.Rejected("仅支持分享 .xls 或 .xlsx Excel 文件")
        }
        if ((intent.clipData?.itemCount ?: 0) > 1) {
            return SharedExcelIntentResult.Rejected("一次只能导入一个 Excel 文件")
        }

        val uri = runCatching {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        }.getOrNull() ?: return SharedExcelIntentResult.Rejected("分享内容中没有可读取的 Excel 文件")

        if (!uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)) {
            return SharedExcelIntentResult.Rejected("分享的 Excel 文件地址不受支持")
        }
        return SharedExcelIntentResult.File(uri)
    }
}

internal sealed interface PendingSharedExcelImport {
    val id: Long

    data class File(
        override val id: Long,
        val uri: Uri,
    ) : PendingSharedExcelImport

    data class Error(
        override val id: Long,
        val message: String,
    ) : PendingSharedExcelImport
}

internal class SharedExcelImportQueueViewModel : ViewModel() {
    private val mutablePending = MutableStateFlow<List<PendingSharedExcelImport>>(emptyList())
    val pending: StateFlow<List<PendingSharedExcelImport>> = mutablePending.asStateFlow()
    private var nextId = 1L

    fun enqueue(intent: Intent) {
        val parsed = SharedExcelIntentParser.parse(intent) ?: return
        val id = nextId++
        val event = when (parsed) {
            is SharedExcelIntentResult.File -> PendingSharedExcelImport.File(id, parsed.uri)
            is SharedExcelIntentResult.Rejected -> PendingSharedExcelImport.Error(id, parsed.message)
        }
        publish(mutablePending.value + event)
    }

    fun consume(id: Long) {
        val updated = mutablePending.value.filterNot { it.id == id }
        if (updated.size != mutablePending.value.size) publish(updated)
    }

    private fun publish(events: List<PendingSharedExcelImport>) {
        mutablePending.value = events
    }
}
