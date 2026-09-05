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
        /** Empty = release; otherwise pre-release identifier (e.g. "beta.1"). */
        val prerelease: String,
    )

    private val CORE =
        Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.]+))?(?:\+.*)?$""")

    fun parse(raw: String): Parsed? {
        val m = CORE.matchEntire(raw.trim()) ?: return null
        return Parsed(
            major = m.groupValues[1].toInt(),
            minor = m.groupValues[2].toInt(),
            patch = m.groupValues[3].toInt(),
            prerelease = m.groupValues[4],
        )
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
        // Release (no prerelease) > any prerelease of the same core version.
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
        val remote = remoteVersion.trim().removePrefix("v")
        val current = currentVersion.trim().removePrefix("v")
        if (remote.isEmpty() || current.isEmpty()) return false
        val cmp = compare(remote, current)
        return if (cmp != null) cmp > 0 else remote != current
    }
}
