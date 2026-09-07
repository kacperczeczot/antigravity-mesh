package com.antigravity.mesh.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkInstallerTest {

    @Test
    fun testIsAllowedApkUrlHttpsGithub() {
        assertTrue(ApkInstaller.isAllowedApkUrl("https://github.com/kacperczeczot/antigravity-mesh/releases/download/v2.3.2/AntigravityMesh.apk"))
        assertTrue(ApkInstaller.isAllowedApkUrl("https://github-releases.githubusercontent.com/123/AntigravityMesh.apk"))
        assertTrue(ApkInstaller.isAllowedApkUrl("https://s3.amazonaws.com/mesh-apks/AntigravityMesh.apk"))
    }

    @Test
    fun testIsAllowedApkUrlRejectsInsecureOrUnknownHosts() {
        assertFalse(ApkInstaller.isAllowedApkUrl("http://github.com/insecure.apk"))
        assertFalse(ApkInstaller.isAllowedApkUrl("ftp://github.com/insecure.apk"))
        assertFalse(ApkInstaller.isAllowedApkUrl("https://evil-hacker.com/malicious.apk"))
        assertFalse(ApkInstaller.isAllowedApkUrl("not-a-url"))
        assertFalse(ApkInstaller.isAllowedApkUrl(""))
    }

    @Test
    fun testRunOnMainFallbackDoesNotThrow() {
        var executed = false
        ApkInstaller.runOnMain {
            executed = true
        }
        assertTrue("runOnMain musi bezpiecznie wykonać blok kodu nawet bez środowiska Android Looper", executed)
    }
}
