package com.archite.piecesoflife.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

object ImageUtil {

    private const val MAX_IMAGE_SIZE = 8 * 1024 * 1024
    private const val MAX_DOCUMENT_SIZE = 512 * 1024
    private const val THUMBNAIL_WIDTH = 720
    private const val PREVIEW_MAX_LINES = 5

    private const val TAG = "ImageUtil"

    fun getImagesDir(context: Context): File {
        return File(context.filesDir, "images").also { it.mkdirs() }
    }

    fun getDocumentsDir(context: Context): File {
        return File(context.filesDir, "documents").also { it.mkdirs() }
    }

    fun getAvatarFile(context: Context): File {
        return File(getImagesDir(context), "avatar.jpg")
    }

    fun hasAvatar(context: Context): Boolean {
        return getAvatarFile(context).exists()
    }

    fun cropSquareTopLeft(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height)
        return Bitmap.createBitmap(source, 0, 0, size, size)
    }

    fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimEnd('.')
            .trim()
        return cleaned.ifEmpty { "untitled" }
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (_: Exception) { }
        try {
            val docId = DocumentsContract.getDocumentId(uri)
            val decoded = java.net.URLDecoder.decode(docId, "UTF-8")
            val seg = decoded.split("/", "\\").lastOrNull()
            if (!seg.isNullOrBlank() && !seg.contains(":")) return seg
        } catch (_: Exception) { }
        val seg = uri.lastPathSegment?.split("/", "\\")?.lastOrNull()
        if (!seg.isNullOrBlank()) return java.net.URLDecoder.decode(seg, "UTF-8")
        return "untitled"
    }

    fun generateFileName(originalName: String, ext: String): String {
        val now = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_DATE).toString()
        val time = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_SECOND).toString()
        val baseName = sanitizeFileName(originalName.substringBeforeLast("."))
        return "${now}_${time}_${baseName}$ext"
    }

    fun copyImageFromUri(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = inputStream.readBytes()
            inputStream.close()
            if (bytes.size > MAX_IMAGE_SIZE) return null
            val originalName = getFileNameFromUri(context, uri)
            val ext = ".jpg"
            val fileName = generateFileName(originalName, ext)
            val targetFile = File(getImagesDir(context), fileName)
            FileOutputStream(targetFile).use { it.write(bytes) }
            fileName
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            null
        }
    }

    fun copyDocumentFromUri(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = inputStream.readBytes()
            inputStream.close()
            if (bytes.size > MAX_DOCUMENT_SIZE) return null
            val originalName = getFileNameFromUri(context, uri)            
            val fileName = generateFileName(originalName, ".md")
            val targetFile = File(getDocumentsDir(context), fileName)
            FileOutputStream(targetFile).use { it.write(bytes) }
            fileName
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            null
        }
    }

    fun createEmptyDocument(context: Context): String? {
        return try {
            val now = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_DATE).toString()
            val time = TimeUtil.getTimeInt(TimeUtil.TIME_TYPE_SECOND).toString()
            val fileName = "${now}_${time}_untitled.md"
            val targetFile = File(getDocumentsDir(context), fileName)
            targetFile.writeText("")
            fileName
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            null
        }
    }

    fun deleteFile(context: Context, relativePath: String) {
        val file = File(context.filesDir, relativePath)
        if (file.exists()) file.delete()
    }

    fun renameFile(context: Context, oldRelativePath: String, newName: String): String? {
        return try {
            val safeName = sanitizeFileName(newName)
            if (safeName.isEmpty()) return null
            val oldFile = File(context.filesDir, oldRelativePath)
            if (!oldFile.exists()) return null
            val parentDir = oldFile.parentFile ?: return null
            val ext = oldFile.extension
            val newFileName = "${safeName}.$ext"
            val newFile = File(parentDir, newFileName)
            if (oldFile.renameTo(newFile)) {
                "${parentDir.name}/$newFileName"
            } else null
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            null
        }
    }

    fun generateThumbnail(context: Context, imageFileName: String): String? {
        return try {
            val sourceFile = File(getImagesDir(context), imageFileName)
            if (!sourceFile.exists()) return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceFile.absolutePath, options)
            val scaleFactor = if (options.outWidth > THUMBNAIL_WIDTH) {
                options.outWidth / THUMBNAIL_WIDTH
            } else 1
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scaleFactor
            }
            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions) ?: return null
            val scaled = if (bitmap.width > THUMBNAIL_WIDTH) {
                val ratio = THUMBNAIL_WIDTH.toFloat() / bitmap.width
                bitmap.scale(THUMBNAIL_WIDTH, (bitmap.height * ratio).toInt())
            } else bitmap
            val thumbName = "thumb_$imageFileName"
            val thumbFile = File(getImagesDir(context), thumbName)
            FileOutputStream(thumbFile).use { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            if (bitmap !== scaled) scaled.recycle()
            bitmap.recycle()
            thumbName
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            null
        }
    }

    fun loadThumbnail(context: Context, imageFileName: String): Bitmap? {
        val thumbName = "thumb_$imageFileName"
        val thumbFile = File(getImagesDir(context), thumbName)
        if (thumbFile.exists()) {
            return BitmapFactory.decodeFile(thumbFile.absolutePath)
        }
        val sourceFile = File(getImagesDir(context), imageFileName)
        return if (sourceFile.exists()) BitmapFactory.decodeFile(sourceFile.absolutePath) else null
    }

    fun drawRoundCornerBitmap(bitmap: Bitmap, radius: Float): Bitmap {
        val output = createBitmap(bitmap.width, bitmap.height)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rectF = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        canvas.drawRoundRect(rectF, radius, radius, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    fun extractPreview(fileContent: String): String {
        val lines = fileContent.lines()
        val nonEmpty = lines.filter { it.isNotBlank() }
        val previewLines = nonEmpty.take(PREVIEW_MAX_LINES)
        val result = previewLines.joinToString("\n")
        return if (nonEmpty.size > PREVIEW_MAX_LINES) "$result\n..." else result
    }

    fun readDocumentContent(context: Context, documentFileName: String): String? {
        return try {
            val file = File(getDocumentsDir(context), documentFileName)
            if (!file.exists()) return null
            file.readText()
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            null
        }
    }

    fun writeDocumentContent(context: Context, documentFileName: String, content: String): Boolean {
        return try {
            val file = File(getDocumentsDir(context), documentFileName)
            file.writeText(content)
            true
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            false
        }
    }

    fun renderMarkdownToSpannable(markdown: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        val lines = markdown.split("\n")
        for ((index, line) in lines.withIndex()) {
            if (index > 0) sb.append("\n")
            val start = sb.length
            when {
                line.startsWith("### ") -> {
                    sb.append(line.removePrefix("### "))
                    sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, sb.length, 0)
                    sb.setSpan(RelativeSizeSpan(1.1f), start, sb.length, 0)
                }
                line.startsWith("## ") -> {
                    sb.append(line.removePrefix("## "))
                    sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, sb.length, 0)
                    sb.setSpan(RelativeSizeSpan(1.2f), start, sb.length, 0)
                }
                line.startsWith("# ") -> {
                    sb.append(line.removePrefix("# "))
                    sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, sb.length, 0)
                    sb.setSpan(RelativeSizeSpan(1.3f), start, sb.length, 0)
                }
                line.startsWith("```") -> {
                    sb.append(line.removePrefix("```"))
                    sb.setSpan(ForegroundColorSpan(Color.rgb(100, 100, 100)), start, sb.length, 0)
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    sb.append("  ${line.substring(2)}")
                }
                else -> {
                    sb.append(line)
                }
            }
        }
        return sb
    }
}
