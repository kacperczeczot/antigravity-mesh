package com.antigravity.mesh.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseUpdateCheckerTest {

    @Test
    fun testParseManifestNewerVersion() {
        val json = """
            {
                "version": "1.1.0",
                "apkUrl": "https://github.com/kacperczeczot/antigravity-mesh/releases/download/v1.1.0/AntigravityMesh.apk",
                "notes": "Bugfixes and performance improvements"
            }
        """.trimIndent()

        val offer = ReleaseUpdateChecker.parseManifest(json, "1.0.0")
        assertNotNull(offer)
        assertEquals("1.1.0", offer?.latestVersion)
        assertEquals("1.0.0", offer?.currentVersion)
        assertEquals("https://github.com/kacperczeczot/antigravity-mesh/releases/download/v1.1.0/AntigravityMesh.apk", offer?.apkUrl)
        assertEquals("Bugfixes and performance improvements", offer?.releaseNotes)
    }

    @Test
    fun testParseManifestSameOrOlderVersion() {
        val json = """
            {
                "version": "1.0.0",
                "apkUrl": "https://github.com/kacperczeczot/antigravity-mesh/releases/download/v1.0.0/AntigravityMesh.apk"
            }
        """.trimIndent()

        val offer = ReleaseUpdateChecker.parseManifest(json, "1.0.0")
        assertNull(offer)

        val olderOffer = ReleaseUpdateChecker.parseManifest(json, "1.1.0")
        assertNull(olderOffer)
    }

    @Test
    fun testParseManifestRejectsUntrustedApkUrl() {
        val json = """
            {
                "version": "2.0.0",
                "apkUrl": "https://malicious-site.com/evil.apk"
            }
        """.trimIndent()

        val offer = ReleaseUpdateChecker.parseManifest(json, "1.0.0")
        assertNull(offer)
    }

    @Test
    fun testParseGitHubApiRelease() {
        val json = """
            {
                "tag_name": "v1.2.0",
                "body": "Major release",
                "assets": [
                    {
                        "name": "AntigravityMesh.apk",
                        "browser_download_url": "https://github.com/kacperczeczot/antigravity-mesh/releases/download/v1.2.0/AntigravityMesh.apk"
                    }
                ]
            }
        """.trimIndent()

        val offer = ReleaseUpdateChecker.parseGitHubApiRelease(json, "1.0.0")
        assertNotNull(offer)
        assertEquals("1.2.0", offer?.latestVersion)
        assertEquals("https://github.com/kacperczeczot/antigravity-mesh/releases/download/v1.2.0/AntigravityMesh.apk", offer?.apkUrl)
        assertEquals("Major release", offer?.releaseNotes)
    }
}
