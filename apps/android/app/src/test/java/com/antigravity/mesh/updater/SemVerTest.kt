package com.antigravity.mesh.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {

    @Test
    fun testParseStandardVersion() {
        val parsed = SemVer.parse("1.2.3")
        assertNotNull(parsed)
        assertEquals(1, parsed?.major)
        assertEquals(2, parsed?.minor)
        assertEquals(3, parsed?.patch)
        assertEquals("", parsed?.prerelease)
    }

    @Test
    fun testParseLeadingV() {
        val parsed = SemVer.parse("v2.0.4")
        assertNotNull(parsed)
        assertEquals(2, parsed?.major)
        assertEquals(0, parsed?.minor)
        assertEquals(4, parsed?.patch)
    }

    @Test
    fun testParsePrerelease() {
        val parsed = SemVer.parse("1.0.0-beta.1")
        assertNotNull(parsed)
        assertEquals(1, parsed?.major)
        assertEquals(0, parsed?.minor)
        assertEquals(0, parsed?.patch)
        assertEquals("beta.1", parsed?.prerelease)
    }

    @Test
    fun testParseInvalid() {
        assertNull(SemVer.parse("invalid"))
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse("1.0"))
    }

    @Test
    fun testCompare() {
        assertTrue(SemVer.compare("1.0.1", "1.0.0")!! > 0)
        assertTrue(SemVer.compare("1.1.0", "1.0.9")!! > 0)
        assertTrue(SemVer.compare("2.0.0", "1.99.99")!! > 0)
        assertEquals(0, SemVer.compare("1.0.0", "1.0.0"))
        assertTrue(SemVer.compare("1.0.0", "1.0.1")!! < 0)
        // Release > Prerelease
        assertTrue(SemVer.compare("1.0.0", "1.0.0-alpha")!! > 0)
    }

    @Test
    fun testHostIsNewer() {
        assertTrue(SemVer.hostIsNewer("1.0.1", "1.0.0"))
        assertTrue(SemVer.hostIsNewer("v1.0.1", "1.0.0"))
        assertTrue(SemVer.hostIsNewer("1.0.1", "v1.0.0"))
        assertTrue(SemVer.hostIsNewer("v1.1.0", "v1.0.9"))
        assertFalse(SemVer.hostIsNewer("1.0.0", "1.0.0"))
        assertFalse(SemVer.hostIsNewer("v1.0.0", "1.0.0"))
        assertFalse(SemVer.hostIsNewer("1.0.0", "1.0.1"))
    }
}
