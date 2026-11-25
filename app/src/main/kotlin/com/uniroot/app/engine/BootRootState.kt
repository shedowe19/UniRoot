package com.uniroot.app.engine

import android.content.Context
import android.provider.Settings
import java.io.File

/**
 * Tracks whether root is active for the CURRENT Linux kernel boot.
 *
 * Linux generates a fresh UUID in /proc/sys/kernel/random/boot_id on every
 * real hardware/kernel reboot, while soft reboots (restarting zygote and/or
 * surfaceflinger) keep the exact same kernel and boot_id — and keep the
 * late-loaded KernelSU LKM resident in memory.
 *
 * Stored in Device-Protected (DE) storage using synchronous commit() so the
 * state survives immediate soft reboots and is readable in boot receivers
 * before first unlock.
 */
object BootRootState {

    private const val PREFS = "uniroot_boot_state"
    private const val KEY_BOOT_ID = "rooted_boot_id"
    private const val KEY_FLAVOR = "rooted_flavor"
    private val DEFAULT_BOOT_ID_FILE = File("/proc/sys/kernel/random/boot_id")

    /** Unique identifier for the current kernel boot (stable across soft reboots). */
    fun currentBootId(context: Context? = null, bootIdFile: File = DEFAULT_BOOT_ID_FILE): String {
        val fromKernel = runCatching { bootIdFile.readText().trim() }.getOrDefault("")
        if (fromKernel.isNotEmpty()) return fromKernel
        if (context != null) {
            val count = runCatching {
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
            }.getOrDefault(-1)
            if (count >= 0) return "boot_count:$count"
        }
        return ""
    }

    internal fun matchesBoot(savedBootId: String?, currentBootId: String?): Boolean =
        !savedBootId.isNullOrBlank() && savedBootId == currentBootId

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun markRooted(context: Context, flavor: String? = null) {
        val id = currentBootId(context)
        if (id.isEmpty()) return
        prefs(context).edit().apply {
            putString(KEY_BOOT_ID, id)
            if (!flavor.isNullOrBlank()) putString(KEY_FLAVOR, flavor)
        }.commit()
    }

    fun isRootedThisBoot(context: Context): Boolean {
        val current = currentBootId(context)
        if (current.isEmpty()) return false
        val saved = runCatching { prefs(context).getString(KEY_BOOT_ID, null) }.getOrNull()
        return matchesBoot(saved, current)
    }

    fun rootedFlavorThisBoot(context: Context): String? {
        if (!isRootedThisBoot(context)) return null
        return runCatching { prefs(context).getString(KEY_FLAVOR, null) }.getOrNull()
    }

    fun clear(context: Context) {
        runCatching {
            prefs(context).edit().remove(KEY_BOOT_ID).remove(KEY_FLAVOR).commit()
        }
    }
}
