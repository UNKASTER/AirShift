package com.bradj.airshift

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.lifecycle.SavedStateHandle
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

internal class SharedExcelImportQueueViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val restoredState = savedStateHandle.get<Bundle>(STATE_KEY)
    private val mutablePending = MutableStateFlow(restoreEvents(restoredState))
    val pending: StateFlow<List<PendingSharedExcelImport>> = mutablePending.asStateFlow()
    private var nextId = maxOf(
        restoredState?.getLong(NEXT_ID_KEY, 1L) ?: 1L,
        (mutablePending.value.maxOfOrNull(PendingSharedExcelImport::id) ?: 0L) + 1L,
    )
    private var nextAttemptToken = (restoredState?.getLong(NEXT_ATTEMPT_KEY, 1L) ?: 1L).coerceAtLeast(1L)
    // A restored process must retry the event instead of accepting a previous attempt's callback.
    private var currentAttempt: ImportAttempt? = null

    fun enqueue(intent: Intent) {
        val parsed = SharedExcelIntentParser.parse(intent) ?: return
        val id = nextId++
        val event = when (parsed) {
            is SharedExcelIntentResult.File -> PendingSharedExcelImport.File(id, parsed.uri)
            is SharedExcelIntentResult.Rejected -> PendingSharedExcelImport.Error(id, parsed.message)
        }
        publish(mutablePending.value + event)
    }

    /** A retry owns the head event without removing it until its result has been handled. */
    fun beginAttempt(id: Long): Long? {
        if (mutablePending.value.firstOrNull()?.id != id) return null
        val token = nextAttemptToken++
        currentAttempt = ImportAttempt(id, token)
        saveState(mutablePending.value)
        return token
    }

    fun isCurrentAttempt(id: Long, token: Long): Boolean =
        currentAttempt == ImportAttempt(id, token) && mutablePending.value.firstOrNull()?.id == id

    fun consume(id: Long, token: Long): Boolean {
        if (!isCurrentAttempt(id, token)) return false
        consume(id)
        return true
    }

    fun consume(id: Long) {
        if (currentAttempt?.id == id) currentAttempt = null
        val updated = mutablePending.value.filterNot { it.id == id }
        if (updated.size != mutablePending.value.size) publish(updated)
    }

    private fun publish(events: List<PendingSharedExcelImport>) {
        saveState(events)
        mutablePending.value = events
    }

    private fun saveState(events: List<PendingSharedExcelImport>) {
        savedStateHandle[STATE_KEY] = Bundle().apply {
            putLong(NEXT_ID_KEY, nextId)
            putLong(NEXT_ATTEMPT_KEY, nextAttemptToken)
            putParcelableArrayList(EVENTS_KEY, ArrayList(events.map { event ->
                Bundle().apply {
                    putLong(EVENT_ID_KEY, event.id)
                    when (event) {
                        is PendingSharedExcelImport.File -> putString(EVENT_URI_KEY, event.uri.toString())
                        is PendingSharedExcelImport.Error -> putString(EVENT_ERROR_KEY, event.message)
                    }
                }
            }))
        }
    }

    private fun restoreEvents(state: Bundle?): List<PendingSharedExcelImport> =
        state?.let { BundleCompat.getParcelableArrayList(it, EVENTS_KEY, Bundle::class.java) }.orEmpty().map { event ->
            val id = event.getLong(EVENT_ID_KEY)
            val uri = event.getString(EVENT_URI_KEY)?.let(Uri::parse)
            if (uri != null && uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)) {
                PendingSharedExcelImport.File(id, uri)
            } else {
                PendingSharedExcelImport.Error(
                    id,
                    event.getString(EVENT_ERROR_KEY) ?: "分享的 Excel 文件无法恢复，请重新分享",
                )
            }
        }

    private data class ImportAttempt(val id: Long, val token: Long)

    private companion object {
        const val STATE_KEY = "shared_excel_import_queue"
        const val NEXT_ID_KEY = "next_id"
        const val NEXT_ATTEMPT_KEY = "next_attempt_token"
        const val EVENTS_KEY = "events"
        const val EVENT_ID_KEY = "id"
        const val EVENT_URI_KEY = "uri"
        const val EVENT_ERROR_KEY = "error"
    }
}
