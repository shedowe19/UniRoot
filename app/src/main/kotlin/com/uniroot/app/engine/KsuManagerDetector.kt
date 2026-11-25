package com.uniroot.app.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.security.MessageDigest

data class KsuManagerMatch(
    val packageName: String,
    val launcherActivity: String?,
    val spoofed: Boolean,
)

/**
 * Detects installed KernelSU / KernelSU Next managers, including official
 * `-spoofed` builds whose package ID is randomized per release by matching the
 * manager APK's signing certificate SHA-256 (the exact same signature the kernel
 * module validates in throne_tracker).
 */
object KsuManagerDetector {

    /** Official KernelSU Next release signing certificate SHA-256 (standard + spoofed). */
    internal const val NEXT_CERT_SHA256 =
        "79e590113c4c4c0c222978e413a5faa801666957b1212a328e46c00c69821bf7"

    /** Official KernelSU classic release signing certificate SHA-256. */
    internal const val CLASSIC_CERT_SHA256 =
        "c371061b19d8c7d7d6133c6a9bafe198fa944e50c1b31c9d8daa8d7f1fc2d2d6"

    private val NEXT_STANDARD_PACKAGES = listOf("com.rifsxd.ksunext", "com.rifsxd.ksunext.debug")
    private val CLASSIC_STANDARD_PACKAGES = listOf("me.weishu.kernelsu", "me.weishu.kernelsu.pr")

    internal fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    internal fun matchesCertificate(certHashes: Collection<String>, next: Boolean): Boolean {
        val expected = if (next) NEXT_CERT_SHA256 else CLASSIC_CERT_SHA256
        return certHashes.any { it.equals(expected, ignoreCase = true) }
    }

    internal fun matchesSpoofedFallback(
        next: Boolean,
        versionName: String?,
        appLabel: String?,
    ): Boolean {
        if (!next) return false
        val ver = versionName.orEmpty()
        val label = appLabel.orEmpty()
        return ver.contains("spoofed", ignoreCase = true) &&
            (label.contains("KernelSU", ignoreCase = true) || label.contains("KSU", ignoreCase = true))
    }

    fun find(context: Context, next: Boolean): KsuManagerMatch? {
        val pm = context.packageManager
        val standardPackages = if (next) NEXT_STANDARD_PACKAGES else CLASSIC_STANDARD_PACKAGES
        for (pkg in standardPackages) {
            if (runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess) {
                val activity = pm.getLaunchIntentForPackage(pkg)?.component?.className
                return KsuManagerMatch(pkg, activity, spoofed = false)
            }
        }

        // Scan launchable apps to locate spoofed manager APKs (e.g. v3.4.2-spoofed).
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching {
            pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())

        var bestMatch: Pair<Long, KsuManagerMatch>? = null
        val seen = HashSet<String>()
        for (ri in resolved) {
            val ai = ri.activityInfo ?: continue
            val pkg = ai.packageName ?: continue
            if (!seen.add(pkg)) continue
            val pkgInfo = runCatching {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            }.getOrNull() ?: continue

            val hashes = signerHashes(pkgInfo)
            val label = runCatching { ri.loadLabel(pm)?.toString() }.getOrNull()
            val matched = matchesCertificate(hashes, next) ||
                matchesSpoofedFallback(next, pkgInfo.versionName, label)
            if (matched) {
                val versionCode = pkgInfo.longVersionCode
                val candidate = KsuManagerMatch(pkg, ai.name, spoofed = pkg !in standardPackages)
                if (bestMatch == null || versionCode > bestMatch.first) {
                    bestMatch = versionCode to candidate
                }
            }
        }
        return bestMatch?.second
    }

    private fun signerHashes(pkgInfo: PackageInfo): List<String> {
        val signingInfo = pkgInfo.signingInfo ?: return emptyList()
        val signers = if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        } ?: return emptyList()
        return signers.map { sha256Hex(it.toByteArray()) }
    }
}
