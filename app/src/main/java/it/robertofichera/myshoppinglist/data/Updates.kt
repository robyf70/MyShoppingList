package it.robertofichera.myshoppinglist.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import it.robertofichera.myshoppinglist.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The APK asset of a GitHub release, carrying the version its tag names. */
data class Release(val versionName: String, val apkUrl: String)

/**
 * Compares dotted numeric versions, ignoring a leading "v" and padding the shorter
 * one with zeroes, so "1.2" beats "1.1.9" and "1.2.1" beats "1.2". A tag that is not
 * dotted numbers loses: an unreadable version is not worth offering as an update.
 */
internal fun isNewerVersion(candidate: String, current: String): Boolean {
    val new = versionParts(candidate) ?: return false
    val old = versionParts(current) ?: return true
    for (i in 0 until maxOf(new.size, old.size)) {
        val a = new.getOrElse(i) { 0 }
        val b = old.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

private fun versionParts(version: String): List<Int>? =
    version.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: return null }

/**
 * The newer release, or null when there isn't one *and* when anything at all goes wrong.
 * A phone with no connection must not be told the check failed on every launch.
 * Blocking: call it off the main thread.
 */
fun fetchLatestRelease(apiUrl: String, currentVersion: String): Release? = runCatching {
    val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        setRequestProperty("Accept", "application/vnd.github+json")
    }
    val body = try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
        connection.inputStream.bufferedReader().readText()
    } finally {
        connection.disconnect()
    }

    val release = JSONObject(body)
    val tag = release.getString("tag_name")
    if (!isNewerVersion(tag, currentVersion)) return null

    val assets = release.getJSONArray("assets")
    val apk = (0 until assets.length())
        .map { assets.getJSONObject(it) }
        .firstOrNull { it.getString("name").endsWith(".apk", ignoreCase = true) }
        ?: return null

    Release(versionName = tag.removePrefix("v"), apkUrl = apk.getString("browser_download_url"))
}.getOrNull()

/**
 * Downloads into the public Downloads folder, which is what makes [downloadedApk] hand back a
 * content:// URI the system installer can be granted — an app-private copy would need a provider.
 */
fun enqueueDownload(context: Context, release: Release): Long =
    context.downloadManager().enqueue(
        DownloadManager.Request(release.apkUrl.toUri())
            .setTitle(context.getString(R.string.app_name))
            .setDescription(context.getString(R.string.update_available, release.versionName))
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "${context.packageName}-${release.versionName}.apk",
            )
    )

/** The finished APK, null while still running and on failure. */
fun downloadedApk(context: Context, downloadId: Long): DownloadResult {
    val manager = context.downloadManager()
    manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
        if (!cursor.moveToFirst()) return DownloadResult.Failed
        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        return when (status) {
            DownloadManager.STATUS_SUCCESSFUL ->
                manager.getUriForDownloadedFile(downloadId)
                    ?.let { DownloadResult.Done(it) }
                    ?: DownloadResult.Failed

            DownloadManager.STATUS_FAILED -> DownloadResult.Failed
            else -> DownloadResult.Running
        }
    }
}

sealed interface DownloadResult {
    data object Running : DownloadResult
    data object Failed : DownloadResult
    data class Done(val apk: Uri) : DownloadResult
}

/** The system installer takes it from here, including asking to trust this app as a source. */
fun installIntent(apk: Uri): Intent = Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(apk, APK_MIME)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

private fun Context.downloadManager(): DownloadManager =
    getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

private const val APK_MIME = "application/vnd.android.package-archive"
private const val TIMEOUT_MS = 10_000
