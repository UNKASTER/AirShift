package com.bradj.airshift

import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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

            assertTrue(
                matches.any {
                    it.activityInfo.packageName == context.packageName &&
                        it.activityInfo.name == MainActivity::class.java.name
                },
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
        val queue = SharedExcelImportQueueViewModel()
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

    private fun sharedIntent(mimeType: String, uri: Uri) = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
    }

    private fun assertRejected(result: SharedExcelIntentResult?) {
        assertTrue(result is SharedExcelIntentResult.Rejected)
    }
}
