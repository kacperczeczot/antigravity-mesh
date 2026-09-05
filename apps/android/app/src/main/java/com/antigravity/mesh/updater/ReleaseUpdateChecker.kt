package com.antigravity.mesh.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Checks GitHub Releases for new updates to Antigravity Mesh:
 * 1. Probes android-latest.json direct asset
 * 2. Fallback to GitHub Releases REST API if manifest asset is not found
 */
object ReleaseUpdateChecker {
    private val executor = Executors.newCachedThreadPool()

    const val REPO_OWNER = "kacperczeczot"
    const val REPO_NAME = "antigravity-mesh"

    const val PUBLIC_GIST_MANIFEST_URL =
        "https://gist.githubusercontent.com/$REPO_OWNER/d82255ff99003bf47ef59b8670ff4db0/raw/android-latest.json"

    const val PUBLIC_PAGES_MANIFEST_URL =
        "https://$REPO_OWNER.github.io/$REPO_NAME/android-latest.json"

    const val MANIFEST_URL =
        "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest/download/android-latest.json"

    const val RAW_MANIFEST_URL =
        "https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/main/apps/android/android-latest.json"

    const val GITHUB_API_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    data class UpdateOffer(
        val latestVersion: String,
        val currentVersion: String,
        val apkUrl: String,
        val releaseNotes: String = "",
    )

    fun check(
        currentVersion: String,
        callback: (UpdateOffer?) -> Unit,
    ) {
        executor.execute {
            callback(runCatching { checkSync(currentVersion) }.getOrNull())
        }
    }

    suspend fun checkAsync(currentVersion: String): UpdateOffer? =
        withContext(Dispatchers.IO) {
            runCatching { checkSync(currentVersion) }.getOrNull()
        }

    internal fun checkSync(currentVersion: String): UpdateOffer? {
        val urlsToTry = listOf(
            PUBLIC_GIST_MANIFEST_URL,
            PUBLIC_PAGES_MANIFEST_URL,
            MANIFEST_URL,
            RAW_MANIFEST_URL
        )

        for (url in urlsToTry) {
            val body = fetchUrl(url)
            if (!body.isNullOrBlank()) {
                val offer = parseManifest(body, currentVersion)
                if (offer != null) return offer
            }
        }

        // Fallback: GitHub Releases API
        val apiBody = fetchUrl(GITHUB_API_URL, acceptHeader = "application/vnd.github.v3+json")
        if (!apiBody.isNullOrBlank()) {
            return parseGitHubApiRelease(apiBody, currentVersion)
        }

        return null
    }

    private val versionRegex = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")
    private val apkUrlRegex = Regex("\"apkUrl\"\\s*:\\s*\"([^\"]+)\"")
    private val notesRegex = Regex("\"notes\"\\s*:\\s*\"([^\"]+)\"")
    private val tagNameRegex = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")
    private val bodyRegex = Regex("\"body\"\\s*:\\s*\"([^\"]+)\"")
    private val apkAssetRegex = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"", RegexOption.IGNORE_CASE)

    internal fun parseManifest(body: String, currentVersion: String): UpdateOffer? {
        val latest = versionRegex.find(body)?.groupValues?.get(1)?.trim().orEmpty()
        val apkUrl = apkUrlRegex.find(body)?.groupValues?.get(1)?.trim().orEmpty()
        val notes = notesRegex.find(body)?.groupValues?.get(1)?.trim().orEmpty()

        if (latest.isEmpty() || apkUrl.isEmpty()) return null
        if (!SemVer.hostIsNewer(latest, currentVersion)) return null
        if (!ApkInstaller.isAllowedApkUrl(apkUrl)) return null

        return UpdateOffer(
            latestVersion = latest,
            currentVersion = currentVersion.trim(),
            apkUrl = apkUrl,
            releaseNotes = notes
        )
    }

    internal fun parseGitHubApiRelease(body: String, currentVersion: String): UpdateOffer? {
        val tagName = tagNameRegex.find(body)?.groupValues?.get(1)?.trim().orEmpty()
        val cleanVersion = tagName.removePrefix("v").trim()
        val releaseNotes = bodyRegex.find(body)?.groupValues?.get(1)?.trim().orEmpty()

        if (cleanVersion.isEmpty()) return null
        if (!SemVer.hostIsNewer(cleanVersion, currentVersion)) return null

        val apkDownloadUrl = apkAssetRegex.find(body)?.groupValues?.get(1)?.trim()
            ?: "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/$tagName/AntigravityMesh.apk"

        if (!ApkInstaller.isAllowedApkUrl(apkDownloadUrl)) return null

        return UpdateOffer(
            latestVersion = cleanVersion,
            currentVersion = currentVersion.trim(),
            apkUrl = apkDownloadUrl,
            releaseNotes = releaseNotes
        )
    }

    private fun fetchUrl(urlStr: String, acceptHeader: String = "application/json"): String? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", acceptHeader)
            setRequestProperty("User-Agent", "AntigravityMesh-Android-UpdateCheck")
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
