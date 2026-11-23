package com.irontrack.app.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.irontrack.app.R
import com.irontrack.core.importer.SourceLine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max

/** Résultat de l'extraction de texte, quel que soit le format d'origine. */
data class ExtractionResult(
    val lines: List<SourceLine>,
    val pageCount: Int,
    val ocrUsed: Boolean,
    val warnings: List<String>,
)

class ImportException(message: String) : Exception(message)

/**
 * Extrait le texte d'un fichier TXT, PDF (texte ou scanné) ou d'une image (JPG, PNG, WEBP, HEIC)
 * en conservant l'ordre de lecture. La reconnaissance de texte (OCR) fonctionne hors ligne.
 */
class DocumentTextExtractor(private val context: Context) {

    enum class Kind { TEXT, PDF, IMAGE, UNKNOWN }

    fun kindOf(uri: Uri, mime: String?): Kind {
        val m = mime ?: context.contentResolver.getType(uri) ?: ""
        val name = displayName(uri)?.lowercase().orEmpty()
        return when {
            m.startsWith("text/") || name.endsWith(".txt") -> Kind.TEXT
            m == "application/pdf" || name.endsWith(".pdf") -> Kind.PDF
            m.startsWith("image/") || listOf(".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif").any { name.endsWith(it) } -> Kind.IMAGE
            else -> Kind.UNKNOWN
        }
    }

    fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    /** Extrait plusieurs fichiers dans l'ordre (ex. plusieurs photos d'un même programme). */
    suspend fun extractAll(uris: List<Pair<Uri, String?>>, onProgress: (String) -> Unit): ExtractionResult {
        val all = mutableListOf<SourceLine>()
        val warnings = mutableListOf<String>()
        var pages = 0
        var ocr = false
        uris.forEachIndexed { i, (uri, mime) ->
            if (uris.size > 1) onProgress(context.getString(R.string.import_progress_file, i + 1, uris.size))
            val r = extract(uri, mime, onProgress)
            all += r.lines.map { it.copy(page = (it.page ?: 1) + pages) }
            pages += r.pageCount
            ocr = ocr || r.ocrUsed
            warnings += r.warnings
        }
        return ExtractionResult(all, pages, ocr, warnings)
    }

    suspend fun extract(uri: Uri, mime: String?, onProgress: (String) -> Unit): ExtractionResult = when (kindOf(uri, mime)) {
        Kind.TEXT -> extractText(uri)
        Kind.PDF -> extractPdf(uri, onProgress)
        Kind.IMAGE -> {
            onProgress(context.getString(R.string.import_progress_ocr))
            val bmp = decodeImage(uri)
            val lines = ocrWithRotation(bmp)
            bmp.recycle()
            ExtractionResult(lines, 1, true, if (lines.isEmpty()) listOf(context.getString(R.string.import_warning_no_text_image)) else emptyList())
        }
        Kind.UNKNOWN -> throw ImportException(context.getString(R.string.import_error_unsupported))
    }

    // ------------------------------------------------------------------ TXT

    private suspend fun extractText(uri: Uri): ExtractionResult = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw ImportException(context.getString(R.string.import_error_read))
        if (bytes.size > MAX_TEXT_BYTES) throw ImportException(context.getString(R.string.import_error_too_large))
        val text = decodeText(bytes)
        ExtractionResult(text.lines().map { SourceLine(it) }, 1, false, emptyList())
    }

    private fun decodeText(bytes: ByteArray): String {
        // UTF-8 (avec ou sans BOM), sinon Windows-1252 (fichiers créés sous Windows).
        val utf8 = Charsets.UTF_8.newDecoder()
        return runCatching { utf8.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { String(bytes, Charset.forName("windows-1252")) }
            .removePrefix("﻿")
    }

    // ------------------------------------------------------------------ PDF

    private suspend fun extractPdf(uri: Uri, onProgress: (String) -> Unit): ExtractionResult = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val tmp = File(context.cacheDir, "import_${System.currentTimeMillis()}.pdf")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: throw ImportException(context.getString(R.string.import_error_read))
            val lines = mutableListOf<SourceLine>()
            val warnings = mutableListOf<String>()
            var ocrUsed = false
            val textPerPage: List<String> = try {
                PDDocument.load(tmp).use { doc ->
                    if (doc.isEncrypted) throw ImportException(context.getString(R.string.import_error_pdf_protected))
                    val count = doc.numberOfPages
                    val stripper = PDFTextStripper()
                    stripper.setSortByPosition(true)
                    (1..count).map { p ->
                        onProgress(context.getString(R.string.import_progress_page, p, count))
                        stripper.setStartPage(p)
                        stripper.setEndPage(p)
                        stripper.getText(doc)
                    }
                }
            } catch (e: ImportException) {
                throw e
            } catch (e: Exception) {
                throw ImportException(context.getString(R.string.import_error_pdf))
            }
            // Pages sans texte exploitable : PDF scanné -> rendu de la page puis OCR.
            var renderer: PdfRenderer? = null
            var fd: ParcelFileDescriptor? = null
            try {
                textPerPage.forEachIndexed { i, txt ->
                    val meaningful = txt.count { it.isLetterOrDigit() }
                    if (meaningful >= MIN_PDF_TEXT_CHARS) {
                        lines += txt.lines().map { SourceLine(it, page = i + 1) }
                    } else {
                        if (renderer == null) {
                            fd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
                            renderer = PdfRenderer(fd!!)
                        }
                        onProgress(context.getString(R.string.import_progress_ocr_page, i + 1, textPerPage.size))
                        val page = renderer!!.openPage(i)
                        val scale = (2200f / max(page.width, page.height)).coerceIn(1f, 4f)
                        val bmp = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        val ocrLines = ocrWithRotation(bmp)
                        bmp.recycle()
                        ocrUsed = true
                        if (ocrLines.isEmpty()) warnings += context.getString(R.string.import_warning_page_empty, i + 1)
                        lines += ocrLines.map { it.copy(page = i + 1) }
                    }
                }
            } finally {
                renderer?.close()
                fd?.close()
            }
            ExtractionResult(lines, textPerPage.size, ocrUsed, warnings)
        } finally {
            tmp.delete()
        }
    }

    // ------------------------------------------------------------------ Images

    private suspend fun decodeImage(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            // ImageDecoder applique l'orientation EXIF et prend en charge HEIC/HEIF, WEBP, PNG, JPEG.
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_IMAGE_SIDE) {
                    val f = MAX_IMAGE_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
                }
            }
        } catch (e: Exception) {
            throw ImportException(context.getString(R.string.import_error_image))
        }
    }

    /** Améliore la lisibilité : niveaux de gris + contraste renforcé ; agrandit les petites images. */
    private fun preprocess(src: Bitmap): Bitmap {
        val longest = max(src.width, src.height)
        val scale = if (longest < 1200) 1200f / longest else 1f
        val w = (src.width * scale).toInt()
        val h = (src.height * scale).toInt()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val contrast = 1.35f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val cm = ColorMatrix().apply { setSaturation(0f) }
        cm.postConcat(ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f,
        )))
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.save()
        canvas.scale(scale, scale)
        canvas.drawBitmap(src, 0f, 0f, paint)
        canvas.restore()
        return out
    }

    private fun rotate(src: Bitmap, degrees: Float): Bitmap =
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(degrees) }, true)

    /**
     * OCR avec correction d'orientation : si peu de texte est reconnu, on essaie les autres rotations
     * et on garde la meilleure lecture.
     */
    private suspend fun ocrWithRotation(original: Bitmap): List<SourceLine> {
        val pre = preprocess(original)
        var best = recognize(pre)
        val bestScore = { l: List<SourceLine> -> l.sumOf { s -> s.text.count { it.isLetterOrDigit() } } }
        if (bestScore(best) < 40) {
            for (deg in listOf(90f, 270f, 180f)) {
                val r = rotate(pre, deg)
                val candidate = recognize(r)
                r.recycle()
                if (bestScore(candidate) > bestScore(best) * 1.5) best = candidate
            }
        }
        pre.recycle()
        return best
    }

    private suspend fun recognize(bmp: Bitmap): List<SourceLine> = withContext(Dispatchers.Default) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text: Text = suspendCancellableCoroutine { cont ->
                recognizer.process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            toRows(text)
        } finally {
            recognizer.close()
        }
    }

    /**
     * Reconstitue l'ordre de lecture et les tableaux : les lignes OCR alignées verticalement sont
     * regroupées en une rangée, triées de gauche à droite et séparées par des espaces multiples
     * (interprétés comme colonnes par l'analyseur).
     */
    private fun toRows(text: Text): List<SourceLine> {
        data class L(val text: String, val top: Int, val bottom: Int, val left: Int, val conf: Float?)
        val all = text.textBlocks.flatMap { b ->
            b.lines.mapNotNull { l ->
                val box = l.boundingBox ?: return@mapNotNull null
                L(l.text, box.top, box.bottom, box.left, l.confidence)
            }
        }.sortedBy { it.top }
        val rows = mutableListOf<MutableList<L>>()
        for (l in all) {
            val center = (l.top + l.bottom) / 2
            val row = rows.lastOrNull()?.takeIf { r ->
                val rc = r.map { (it.top + it.bottom) / 2 }.average()
                val h = r.map { it.bottom - it.top }.average()
                abs(center - rc) < h * 0.6
            }
            if (row != null) row += l else rows += mutableListOf(l)
        }
        return rows.map { r ->
            val sorted = r.sortedBy { it.left }
            val conf = sorted.mapNotNull { it.conf }.minOrNull()
            SourceLine(sorted.joinToString("   ") { it.text }, conf)
        }
    }

    companion object {
        private const val MAX_TEXT_BYTES = 5 * 1024 * 1024
        private const val MAX_IMAGE_SIDE = 3000
        private const val MIN_PDF_TEXT_CHARS = 20
    }
}
