package com.antigravity.mesh.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Handles secure downloading and installation of APK updates.
 */
object ApkInstaller {
    private val executor = Executors.newSingleThreadExecutor()

    const val INSTALL_STATUS_ACTION = "com.antigravity.mesh.APK_INSTALL_STATUS"

    private const val MAX_REDIRECTS = 5

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun unknownSourcesSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun isAllowedApkUrl(apkUrl: String): Boolean {
        val uri = runCatching { Uri.parse(apkUrl) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase() ?: return false

        if (scheme != "https") return false

        return host.endsWith("github.com") ||
                host.endsWith("githubusercontent.com") ||
                host.endsWith("amazonaws.com")
    }

    fun downloadThenInstall(
        context: Context,
        apkUrl: String,
        onProgress: ((progressText: String, progressFraction: Float) -> Unit)? = null,
        onError: (String) -> Unit,
        onReadyToInstall: (File) -> Unit,
    ) {
        executor.execute {
            val result = runCatching {
                if (!isAllowedApkUrl(apkUrl)) {
                    error("Niedozwolony adres URL aktualizacji")
                }
                onProgress?.invoke("Pobieranie pliku APK…", 0f)
                val dest = File(context.cacheDir, "antigravity-mesh-update.apk")
                if (dest.exists()) {
                    dest.delete()
                }

                downloadTo(apkUrl, dest, onProgress)
                onProgress?.invoke("Weryfikacja pakietu…", 1f)
                verifyApkOrThrow(context, dest)
                dest
            }

            val file = result.getOrNull()
            if (file == null) {
                onError(result.exceptionOrNull()?.message ?: "Błąd pobierania aktualizacji")
                return@execute
            }
            onReadyToInstall(file)
        }
    }

    /**
     * Installs APK using PackageInstaller API with fallback to FileProvider Intent.
     */
    fun install(context: Context, apkFile: File) {
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }

            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apkFile.length()).use { out ->
                    apkFile.inputStream().use { input -> input.copyTo(out) }
                    session.fsync(out)
                }

                val statusIntent = Intent(INSTALL_STATUS_ACTION).setPackage(context.packageName)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PendingIntent.FLAG_MUTABLE
                        } else {
                            0
                        }
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags)
                session.commit(pendingIntent.intentSender)
            }
        } catch (e: Exception) {
            // Fallback to traditional FileProvider install intent
            runCatching {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(intent)
            }.onFailure { fallbackEx ->
                Toast.makeText(
                    context,
                    "Nie udało się uruchomić instalacji: ${fallbackEx.message ?: e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    internal fun verifyApkOrThrow(context: Context, apkFile: File) {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            ?: error("Pobrany plik nie jest prawidłowym plikiem APK")

        if (archive.packageName != context.packageName) {
            error("Niewłaściwy pakiet aplikacji (${archive.packageName} != ${context.packageName})")
        }
    }

    private fun downloadTo(
        apkUrl: String,
        dest: File,
        onProgress: ((String, Float) -> Unit)?
    ) {
        var current = apkUrl.trim()
        var redirects = 0

        while (true) {
            if (!isAllowedApkUrl(current)) {
                error("Niedozwolone przekierowanie URL")
            }

            val url = URL(current)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 120_000
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "AntigravityMesh-Downloader")
            }

            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                        ?: error("Brak nagłówka Location przy przekierowaniu")
                    redirects++
                    if (redirects > MAX_REDIRECTS) error("Zbyt wiele przekierowań")
                    current = if (location.startsWith("http://") || location.startsWith("https://")) {
                        location
                    } else {
                        URL(url, location).toString()
                    }
                    continue
                }

                if (code !in 200..299) {
                    error("Błąd serwera HTTP $code")
                }

                val contentLength = conn.contentLengthLong
                var bytesRead = 0L

                conn.inputStream.use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (contentLength > 0 && onProgress != null) {
                                val frac = (bytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                val mbRead = bytesRead / (1024 * 1024)
                                val mbTotal = contentLength / (1024 * 1024)
                                onProgress("Pobrano $mbRead MB / $mbTotal MB (${(frac * 100).toInt()}%)", frac)
                            }
                        }
                    }
                }

                if (dest.length() <= 0L) error("Pusty plik aktualizacji")
                return
            } finally {
                conn.disconnect()
            }
        }
    }
}
