package com.bradj.airshift

import android.content.ClipData
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedExcelIntentParserInstrumentedTest {
    @Test
    fun manifestRegistersStandardExcelShareTargets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        listOf(XLS_MIME_TYPE, XLSX_MIME_TYPE).forEach { mimeType ->
            val matches = context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    addCategory(Intent.CATEGORY_DEFAULT)
                },
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )

            val activity = matches.firstOrNull {
                it.activityInfo.packageName == context.packageName &&
                    it.activityInfo.name == MainActivity::class.java.name
            }?.activityInfo
            assertEquals(
                ActivityInfo.LAUNCH_SINGLE_TOP,
                requireNotNull(activity).launchMode,
            )
        }
    }

    @Test
    fun acceptsStandardXlsAndXlsxShares() {
        listOf(XLS_MIME_TYPE, XLSX_MIME_TYPE).forEach { mimeType ->
            val uri = Uri.parse("content://com.example.rosters/roster-${mimeType.hashCode()}")
            val parsed = SharedExcelIntentParser.parse(sharedIntent(mimeType, uri))

            assertEquals(SharedExcelIntentResult.File(uri), parsed)
        }
    }

    @Test
    fun ignoresLauncherWrongActionAndMultipleShareActions() {
        assertNull(SharedExcelIntentParser.parse(Intent(Intent.ACTION_MAIN)))
        assertNull(
            SharedExcelIntentParser.parse(
                Intent(Intent.ACTION_VIEW, Uri.parse("content://com.example.rosters/roster.xlsx")).apply {
                    type = XLSX_MIME_TYPE
                },
            ),
        )
        assertNull(
            SharedExcelIntentParser.parse(
                Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = XLSX_MIME_TYPE },
            ),
        )
    }

    @Test
    fun rejectsMissingStreamUnsupportedMimeAndUnsupportedUri() {
        assertRejected(
            SharedExcelIntentParser.parse(Intent(Intent.ACTION_SEND).apply { type = XLSX_MIME_TYPE }),
        )
        assertRejected(
            SharedExcelIntentParser.parse(
                sharedIntent("text/plain", Uri.parse("content://com.example.rosters/roster.xlsx")),
            ),
        )
        assertRejected(
            SharedExcelIntentParser.parse(
                sharedIntent(XLSX_MIME_TYPE, Uri.parse("file:///sdcard/roster.xlsx")),
            ),
        )
    }

    @Test
    fun rejectsMultipleClipItemsEvenForActionSend() {
        val first = Uri.parse("content://com.example.rosters/first.xlsx")
        val second = Uri.parse("content://com.example.rosters/second.xlsx")
        val intent = sharedIntent(XLSX_MIME_TYPE, first).apply {
            clipData = ClipData.newRawUri("first", first).apply {
                addItem(ClipData.Item(second))
            }
        }

        assertRejected(SharedExcelIntentParser.parse(intent))
    }

    @Test
    fun queuePreservesRepeatedSharesAndConsumesEachEventOnce() {
        val queue = SharedExcelImportQueueViewModel(SavedStateHandle())
        val uri = Uri.parse("content://com.example.rosters/repeated.xlsx")
        val intent = sharedIntent(XLSX_MIME_TYPE, uri)

        queue.enqueue(intent)
        queue.enqueue(intent)

        assertEquals(2, queue.pending.value.size)
        val firstId = queue.pending.value.first().id
        val secondId = queue.pending.value.last().id
        queue.consume(firstId)
        queue.consume(firstId)

        assertEquals(listOf(secondId), queue.pending.value.map(PendingSharedExcelImport::id))
    }

    @Test
    fun queueRestoresFilesErrorsAndRepeatedUrisInOrder() {
        val handle = SavedStateHandle()
        val queue = SharedExcelImportQueueViewModel(handle)
        val uri = Uri.parse("content://com.example.rosters/repeated.xlsx")
        queue.enqueue(sharedIntent(XLSX_MIME_TYPE, uri))
        queue.enqueue(sharedIntent("text/plain", uri))
        queue.enqueue(sharedIntent(XLSX_MIME_TYPE, uri))
        val expected = queue.pending.value

        val restored = SharedExcelImportQueueViewModel(restoreHandle(handle))

        assertEquals(expected, restored.pending.value)
        restored.enqueue(sharedIntent(XLSX_MIME_TYPE, uri))
        assertTrue(restored.pending.value.last().id > expected.last().id)
        assertEquals(4, restored.pending.value.size)
    }

    @Test
    fun restoredQueueDoesNotReplayConsumedEventsOrReuseTheirIds() {
        val handle = SavedStateHandle()
        val queue = SharedExcelImportQueueViewModel(handle)
        val intent = sharedIntent(XLS_MIME_TYPE, Uri.parse("content://com.example.rosters/roster.xls"))
        queue.enqueue(intent)
        val consumedId = queue.pending.value.single().id
        queue.consume(consumedId)

        val restored = SharedExcelImportQueueViewModel(restoreHandle(handle))

        assertTrue(restored.pending.value.isEmpty())
        restored.enqueue(intent)
        assertTrue(restored.pending.value.single().id > consumedId)
        restored.consume(consumedId)
        assertEquals(1, restored.pending.value.size)
    }

    @Test
    fun attemptsKeepEventsPendingAndRejectStaleCallbacks() {
        val queue = SharedExcelImportQueueViewModel(SavedStateHandle())
        val intent = sharedIntent(XLSX_MIME_TYPE, Uri.parse("content://com.example.rosters/roster.xlsx"))
        queue.enqueue(intent)
        queue.enqueue(intent)
        val firstId = queue.pending.value.first().id
        val secondId = queue.pending.value.last().id

        assertNull(queue.beginAttempt(secondId))
        val firstToken = requireNotNull(queue.beginAttempt(firstId))
        assertEquals(2, queue.pending.value.size)
        val retryToken = requireNotNull(queue.beginAttempt(firstId))

        assertTrue(retryToken > firstToken)
        assertFalse(queue.isCurrentAttempt(firstId, firstToken))
        assertTrue(queue.isCurrentAttempt(firstId, retryToken))
        assertFalse(queue.consume(firstId, firstToken))
        assertEquals(2, queue.pending.value.size)
        assertTrue(queue.consume(firstId, retryToken))
        assertFalse(queue.isCurrentAttempt(firstId, retryToken))
        assertFalse(queue.consume(firstId, retryToken))
        assertEquals(listOf(secondId), queue.pending.value.map(PendingSharedExcelImport::id))
    }

    @Test
    fun restorationKeepsInFlightEventButRequiresANewAttempt() {
        val handle = SavedStateHandle()
        val queue = SharedExcelImportQueueViewModel(handle)
        queue.enqueue(sharedIntent(XLSX_MIME_TYPE, Uri.parse("content://com.example.rosters/roster.xlsx")))
        val id = queue.pending.value.single().id
        val oldToken = requireNotNull(queue.beginAttempt(id))

        val restored = SharedExcelImportQueueViewModel(restoreHandle(handle))

        assertEquals(queue.pending.value, restored.pending.value)
        assertFalse(restored.isCurrentAttempt(id, oldToken))
        val newToken = requireNotNull(restored.beginAttempt(id))
        assertTrue(newToken > oldToken)
        assertFalse(restored.consume(id, oldToken))
        assertTrue(restored.consume(id, newToken))
        assertTrue(restored.pending.value.isEmpty())
    }

    private fun restoreHandle(handle: SavedStateHandle): SavedStateHandle {
        val state = Bundle().apply {
            handle.keys().forEach { key -> putBundle(key, handle.get<Bundle>(key)) }
        }
        val writer = Parcel.obtain()
        val bytes = try {
            writer.writeBundle(state)
            writer.marshall()
        } finally {
            writer.recycle()
        }
        val reader = Parcel.obtain()
        val restored = try {
            reader.unmarshall(bytes, 0, bytes.size)
            reader.setDataPosition(0)
            requireNotNull(reader.readBundle(Bundle::class.java.classLoader))
        } finally {
            reader.recycle()
        }
        return SavedStateHandle(restored.keySet().associateWith { key -> restored.getBundle(key) })
    }

    private fun sharedIntent(mimeType: String, uri: Uri) = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
    }

    private fun assertRejected(result: SharedExcelIntentResult?) {
        assertTrue(result is SharedExcelIntentResult.Rejected)
    }
}
