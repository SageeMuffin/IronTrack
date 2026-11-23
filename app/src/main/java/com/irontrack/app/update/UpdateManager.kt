package com.irontrack.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.irontrack.app.BuildConfig
import com.irontrack.app.R
import com.irontrack.app.data.repo.SettingsRepository
import com.irontrack.core.util.SemVer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Version publiée sur GitHub Releases. */
data class ReleaseInfo(
    val version: SemVer,
    val tag: String,
    val name: String,
    val notes: String,
    val publishedAt: String?,
    val htmlUrl: String,
    val apkName: String?,
    val apkUrl: String?,
    val apkSize: Long?,
    val checksumUrl: String?,
)

sealed interface UpdateCheckResult {
    data class UpToDate(val latest: ReleaseInfo?) : UpdateCheckResult
    data class Available(val release: ReleaseInfo) : UpdateCheckResult
    /** Vérification automatique ignorée : déjà faite il y a moins de 24 h. */
    data object Skipped : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

class UpdateException(message: String) : Exception(message)

/**
 * Vérifie les mises à jour via l'API publique GitHub Releases, télécharge l'APK dans le répertoire
 * privé de l'application, vérifie taille, SHA-256, nom de paquet et certificat, puis ouvre
 * l'installateur officiel d'Android. Aucune installation silencieuse.
 */
class UpdateManager(private val context: Context, private val settings: SettingsRepository) {

    private val owner = BuildConfig.UPDATE_REPO_OWNER
    private val repo = BuildConfig.UPDATE_REPO_NAME
    val currentVersion: SemVer = SemVer.fromCode(BuildConfig.VERSION_CODE)
    private val updatesDir get() = File(context.filesDir, "updates").apply { mkdirs() }

    suspend fun check(manual: Boolean): UpdateCheckResult {
        val s = settings.current()
        val now = System.currentTimeMillis()
        if (!manual) {
            if (!s.autoUpdateCheck) return UpdateCheckResult.Skipped
            val last = s.lastUpdateCheckMillis
            if (last != null && now - last < CHECK_INTERVAL_MS) return UpdateCheckResult.Skipped
        }
        return try {
            val latest = fetchLatest(s.includePrereleases)
            settings.recordUpdateCheck(now, latest?.version?.toString())
            if (latest != null && latest.version.code > BuildConfig.VERSION_CODE) UpdateCheckResult.Available(latest)
            else UpdateCheckResult.UpToDate(latest)
        } catch (e: Exception) {
            UpdateCheckResult.Failed(context.getString(R.string.update_check_failed))
        }
    }

    private suspend fun fetchLatest(includePrereleases: Boolean): ReleaseInfo? = withContext(Dispatchers.IO) {
        val json = httpGetText("https://api.github.com/repos/$owner/$repo/releases?per_page=20")
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { !it.optBoolean("draft") && (includePrereleases || !it.optBoolean("prerelease")) }
            .mapNotNull { parseRelease(it) }
            .maxByOrNull { it.version.code }
    }

    private fun parseRelease(o: JSONObject): ReleaseInfo? {
        val tag = o.optString("tag_name")
        val version = SemVer.parse(tag) ?: return null
        val assets = o.optJSONArray("assets") ?: JSONArray()
        val list = (0 until assets.length()).map { assets.getJSONObject(it) }
        val apk = list.firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
        val sum = list.firstOrNull { a ->
            val n = a.optString("name").lowercase()
            n.endsWith(".apk.sha256") || n == "sha256sums" || n.endsWith(".sha256")
        }
        return ReleaseInfo(
            version = version,
            tag = tag,
            name = o.optString("name").ifBlank { tag },
            notes = o.optString("body"),
            publishedAt = o.optString("published_at").takeIf { it.isNotBlank() && it != "null" },
            htmlUrl = o.optString("html_url"),
            apkName = apk?.optString("name"),
            apkUrl = apk?.optString("browser_download_url"),
            apkSize = apk?.optLong("size")?.takeIf { it > 0 },
            checksumUrl = sum?.optString("browser_download_url"),
        )
    }

    /** Télécharge et vérifie l'APK. Renvoie le fichier prêt à installer. */
    suspend fun download(release: ReleaseInfo, onProgress: (Float?) -> Unit): File = withContext(Dispatchers.IO) {
        val url = release.apkUrl ?: throw UpdateException(context.getString(R.string.update_error_no_apk))
        val checksumUrl = release.checksumUrl ?: throw UpdateException(context.getString(R.string.update_error_no_checksum))
        val expected = Regex("\\b[a-fA-F0-9]{64}\\b").find(httpGetText(checksumUrl))?.value?.lowercase()
            ?: throw UpdateException(context.getString(R.string.update_error_no_checksum))

        updatesDir.listFiles()?.forEach { it.delete() }
        val part = File(updatesDir, "IronTrack-${release.tag}.apk.part")
        val target = File(updatesDir, "IronTrack-${release.tag}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = open(url)
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            var read = 0L
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        read += n
                        onProgress(total?.let { read.toFloat() / it })
                    }
                }
            }
            if (release.apkSize != null && read != release.apkSize) {
                part.delete(); throw UpdateException(context.getString(R.string.update_error_size))
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != expected) {
                part.delete(); throw UpdateException(context.getString(R.string.update_error_checksum))
            }
            if (!part.renameTo(target)) throw UpdateException(context.getString(R.string.update_error_download))
        } catch (e: UpdateException) {
            throw e
        } catch (e: Exception) {
            part.delete()
            throw UpdateException(context.getString(R.string.update_error_download))
        } finally {
            conn.disconnect()
        }
        verifyCompatible(target)
        target
    }

    /** Même nom de paquet, version plus récente et même certificat de signature que l'application installée. */
    private fun verifyCompatible(apk: File) {
        val pm = context.packageManager
        val archive: PackageInfo = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: run { apk.delete(); throw UpdateException(context.getString(R.string.update_error_invalid_apk)) }
        if (archive.packageName != context.packageName) {
            apk.delete(); throw UpdateException(context.getString(R.string.update_error_package))
        }
        if (archive.longVersionCode <= BuildConfig.VERSION_CODE) {
            apk.delete(); throw UpdateException(context.getString(R.string.update_error_not_newer))
        }
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val a = archive.signingInfo?.apkContentsSigners?.map { sha256(it.toByteArray()) }?.toSet()
        val b = installed.signingInfo?.apkContentsSigners?.map { sha256(it.toByteArray()) }?.toSet()
        if (a != null && b != null && a != b) {
            apk.delete(); throw UpdateException(context.getString(R.string.update_error_signature))
        }
    }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Ouvre l'installateur officiel d'Android (l'utilisateur confirme l'installation). */
    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Supprime les APK déjà installés ou obsolètes. */
    fun cleanup() {
        updatesDir.listFiles()?.forEach { f ->
            val v = SemVer.parse(f.name.removePrefix("IronTrack-").removeSuffix(".apk").removeSuffix(".apk.part"))
            if (v == null || v.code <= BuildConfig.VERSION_CODE || f.name.endsWith(".part")) f.delete()
        }
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        if (url.startsWith("https://api.github.com/")) setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "IronTrack-Android")
    }

    private fun httpGetText(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            c.disconnect()
        }
    }

    companion object {
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
