package com.bradj.airshift.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import com.bradj.airshift.BuildConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.time.Clock

object OcrRosterReader {
    fun read(
        context: Context,
        uri: Uri,
        userName: String,
        onResult: (Result<RosterParseResult>) -> Unit,
    ) {
        val image = runCatching { InputImage.fromFilePath(context, uri) }
            .getOrElse {
                onResult(Result.failure(it))
                return
            }
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        recognizer.process(image)
            .addOnSuccessListener { text ->
                val tokens = text.textBlocks.flatMap { block ->
                    block.lines.flatMap { line ->
                        line.elements.flatMap { element ->
                            val symbols = element.symbols.mapNotNull { symbol ->
                                symbol.boundingBox?.let { box ->
                                    OcrToken(symbol.text, box.left, box.top, box.right, box.bottom)
                                }
                            }
                            symbols.ifEmpty {
                                listOfNotNull(element.boundingBox?.let { box ->
                                    OcrToken(element.text, box.left, box.top, box.right, box.bottom)
                                })
                            }
                        }
                    }
                }
                if (BuildConfig.DEBUG) {
                    Log.d("AirShiftOCR", "recognized=${text.text.length} chars, symbols=${tokens.size}")
                }
                onResult(
                    runCatching {
                        RosterTableParser.parse(
                            tokens = tokens,
                            imageWidth = image.width,
                            userName = userName,
                            clock = Clock.systemDefaultZone(),
                        )
                    },
                )
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
            .addOnCompleteListener { recognizer.close() }
    }
}
