package com.hydrarsw.controlremoto.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hydrarsw.controlremoto.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class Release(val versionCode: Int, val tag: String, val assetUrl: String, val size: Long)

object ReleaseParser {
    private val TRAILING_NUMBER = Regex("(\\d+)$")

    fun parse(json: String): Release? {
        val root = JSONObject(json)
        val tag = root.optString("tag_name")
        val version = TRAILING_NUMBER.find(tag)?.value?.toIntOrNull() ?: return null
        val assets = root.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name").endsWith(".apk")) {
                return Release(version, tag, asset.getString("url"), asset.getLong("size"))
            }
        }
        return null
    }
}

object Updater {
    suspend fun findUpdate(): Release? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"
        val connection = open(url, "application/vnd.github+json", authorize = true)
        try {
            if (connection.responseCode != 200) return@withContext null
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            ReleaseParser.parse(json)?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
        } finally {
            connection.disconnect()
        }
    }

    /** Descarga el APK y se lo entrega al sistema, que comprueba la firma antes de instalar. */
    suspend fun install(context: Context, release: Release, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(release.size)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val sessionId = installer.createSession(params)
            try {
                installer.openSession(sessionId).use { session ->
                    download(release) { input ->
                        session.openWrite("base.apk", 0, release.size).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var copied = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                copied += read
                                onProgress((copied.toFloat() / release.size).coerceAtMost(1f))
                            }
                            session.fsync(output)
                        }
                    }
                    val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
                    val callback = PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(callback.intentSender)
                }
            } catch (error: Exception) {
                installer.abandonSession(sessionId)
                throw error
            }
        }

    private fun download(release: Release, consume: (java.io.InputStream) -> Unit) {
        var connection = open(release.assetUrl, "application/octet-stream", authorize = true)
        try {
            val redirect = connection.getHeaderField("Location")
            if (connection.responseCode in 300..399 && redirect != null) {
                connection.disconnect()
                // El enlace firmado rechaza encabezados de autorización.
                connection = open(redirect, "application/octet-stream", authorize = false)
            }
            if (connection.responseCode != 200) throw IOException("Descarga fallida: ${connection.responseCode}")
            connection.inputStream.use(consume)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, accept: String, authorize: Boolean): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "ControlRemoto/${BuildConfig.VERSION_NAME}")
            if (authorize && BuildConfig.UPDATE_TOKEN.isNotEmpty()) {
                setRequestProperty("Authorization", "Bearer ${BuildConfig.UPDATE_TOKEN}")
            }
        }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!applicationContext.packageManager.canRequestPackageInstalls()) return Result.success()
        return runCatching {
            Updater.findUpdate()?.let { Updater.install(applicationContext, it) {} }
        }.fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("updates", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
