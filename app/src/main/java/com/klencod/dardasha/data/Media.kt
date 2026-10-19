package com.klencod.dardasha.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.location.LocationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

object Media {
    private val cache = LruCache<String, ImageBitmap>(48)

    fun cached(path: String): ImageBitmap? = cache.get(path)

    /** تنزيل صورة خاصة من Storage (الـ buckets خاصة) مع كاش في الذاكرة. */
    suspend fun image(bucket: String, path: String): ImageBitmap? {
        cache.get(path)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val b = Repo.download(bucket, path)
                BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
            }.getOrNull()?.also { cache.put(path, it) }
        }
    }

    suspend fun audioFile(ctx: Context, path: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val f = File(ctx.cacheDir, "aud_" + path.replace('/', '_'))
            if (!f.exists() || f.length() == 0L) f.writeBytes(Repo.download("chat-audio", path))
            f
        }.getOrNull()
    }

    const val MAX_DOC_BYTES = 20L * 1024 * 1024

    /** يقرأ مستنداً اختاره المستخدم. يرجع null إن فشل، أو يرمي IllegalStateException("big") إن كان كبيراً. */
    fun readDoc(ctx: Context, uri: Uri): PickedDoc? {
        val cr = ctx.contentResolver
        var name = "ملف"
        var size = -1L
        runCatching {
            cr.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) c.getString(ni)?.let { name = it }
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        if (size > MAX_DOC_BYTES) throw IllegalStateException("big")
        val bytes = runCatching { cr.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return null
        if (bytes.size > MAX_DOC_BYTES) throw IllegalStateException("big")
        val mime = cr.getType(uri) ?: guessMime(name)
        return PickedDoc(name, mime, bytes)
    }

    fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    /** امتداد آمن للمفتاح داخل Storage (أسماء عربية/رموز قد ترفضها الـ keys). */
    fun safeExt(name: String): String {
        val e = name.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() && it.code < 128 }.take(8)
        return if (e.isEmpty()) "bin" else e
    }

    /** ينزّل المستند إلى الكاش ويفتحه بتطبيق مناسب. */
    suspend fun openFile(ctx: Context, path: String, name: String?, mime: String?): Boolean {
        val shown = (name ?: "file").replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val f = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(ctx.cacheDir, "files").apply { mkdirs() }
                val out = File(dir, "${path.hashCode().toUInt()}_$shown")
                if (!out.exists() || out.length() == 0L) out.writeBytes(Repo.download(Repo.FILES_BUCKET, path))
                out
            }.getOrNull()
        } ?: return false
        return runCatching {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            val type = mime?.takeIf { it.isNotBlank() } ?: guessMime(shown)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    }

    fun openMap(ctx: Context, g: GeoPoint): Boolean = runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:${g.lat},${g.lng}?q=${g.lat},${g.lng}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.recoverCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(geoUrl(g.lat, g.lng))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    /** يصغّر الصورة (حتى 1280px) ويصحّح الاتجاه ويضغطها JPEG. */
    fun compress(ctx: Context, uri: Uri, max: Int = 1280, quality: Int = 80): ByteArray? = runCatching {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > max * 2 || bounds.outHeight / sample > max * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val rot = cr.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val k = minOf(1f, max.toFloat() / maxOf(bmp.width, bmp.height))
        if (k < 1f || rot != 0f) {
            val m = Matrix().apply { postScale(k, k); postRotate(rot) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
    }.getOrNull()
}

/** تسجيل صوتي AAC داخل m4a (نفس صيغة audio/mp4 التي يشغّلها نسخة الويب). */
class VoiceRecorder(private val ctx: Context) {
    private var mr: MediaRecorder? = null
    private var file: File? = null
    private var t0 = 0L

    fun start(): Boolean = runCatching {
        val f = File(ctx.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(64000)
        r.setAudioSamplingRate(44100)
        r.setOutputFile(f.absolutePath)
        r.prepare()
        r.start()
        mr = r; file = f; t0 = System.currentTimeMillis()
        true
    }.getOrDefault(false)

    /** يوقف التسجيل ويرجع الملف والمدة بالثواني، أو null إذا قصير جداً/فشل. */
    fun stop(): Pair<File, Int>? {
        val r = mr ?: return null
        val f = file
        val secs = ((System.currentTimeMillis() - t0) / 1000).toInt()
        mr = null; file = null
        val ok = runCatching { r.stop(); true }.getOrDefault(false)
        runCatching { r.release() }
        if (!ok || f == null || secs < 1) { f?.delete(); return null }
        return f to secs
    }

    fun cancel() {
        val r = mr ?: return
        mr = null
        runCatching { r.stop() }
        runCatching { r.release() }
        file?.delete(); file = null
    }
}

object Loc {
    /** الموقع الحالي (يتطلب إذن الموقع مسبقاً). */
    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): Location? {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        for (p in providers) {
            val loc = withTimeoutOrNull(8000) { single(lm, p, ctx) }
            if (loc != null) return loc
        }
        return providers.firstNotNullOfOrNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
    }

    @SuppressLint("MissingPermission")
    private suspend fun single(lm: LocationManager, provider: String, ctx: Context): Location? =
        suspendCancellableCoroutine { c ->
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    val cs = android.os.CancellationSignal()
                    c.invokeOnCancellation { cs.cancel() }
                    val consumer = java.util.function.Consumer<Location> { l -> if (c.isActive) c.resume(l) }
                    lm.getCurrentLocation(provider, cs, ctx.mainExecutor, consumer)
                } else {
                    val listener = object : android.location.LocationListener {
                        override fun onLocationChanged(l: Location) { if (c.isActive) c.resume(l) }
                        override fun onProviderEnabled(p: String) {}
                        override fun onProviderDisabled(p: String) {}
                        @Deprecated("legacy")
                        override fun onStatusChanged(p: String?, status: Int, extras: android.os.Bundle?) {}
                    }
                    c.invokeOnCancellation { lm.removeUpdates(listener) }
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper())
                }
            } catch (e: Exception) {
                if (c.isActive) c.resume(null)
            }
        }
}

