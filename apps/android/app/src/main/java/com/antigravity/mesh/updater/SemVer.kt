package com.antigravity.mesh.updater

/**
 * Minimal SemVer comparison for comparing installed app version with GitHub Releases.
 * Supports `MAJOR.MINOR.PATCH` with optional leading `v` and pre-release identifiers.
 */
object SemVer {
    data class Parsed(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val prerelease: String = ""
    )

    fun parse(raw: String): Parsed? {
        val clean = raw.trim().removePrefix("v").removePrefix("V")
        if (clean.isEmpty()) return null

        val parts = clean.split("-", limit = 2)
        val mainVersion = parts[0]
        val prerelease = if (parts.size > 1) parts[1] else ""

        val digits = mainVersion.split(".")
        if (digits.size < 3) return null

        val major = digits.getOrNull(0)?.filter { it.isDigit() }?.toIntOrNull() ?: return null
        val minor = digits.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull() ?: return null
        val patch = digits.getOrNull(2)?.filter { it.isDigit() }?.toIntOrNull() ?: return null

        return Parsed(major, minor, patch, prerelease)
    }

    /**
     * Negative if [a] < [b], 0 if equal, positive if [a] > [b].
     * `null` when either side is unparseable.
     */
    fun compare(a: String, b: String): Int? {
        val left = parse(a) ?: return null
        val right = parse(b) ?: return null
        val core =
            left.major.compareTo(right.major).takeIf { it != 0 }
                ?: left.minor.compareTo(right.minor).takeIf { it != 0 }
                ?: left.patch.compareTo(right.patch)
        if (core != 0) return core
        return when {
            left.prerelease.isEmpty() && right.prerelease.isEmpty() -> 0
            left.prerelease.isEmpty() -> 1
            right.prerelease.isEmpty() -> -1
            else -> left.prerelease.compareTo(right.prerelease)
        }
    }

    /**
     * Returns true if [remoteVersion] is strictly newer than [currentVersion].
     */
    fun hostIsNewer(remoteVersion: String, currentVersion: String): Boolean {
        val cmp = compare(remoteVersion, currentVersion)
        return if (cmp != null) cmp > 0 else remoteVersion.trim() != currentVersion.trim()
    }
}
