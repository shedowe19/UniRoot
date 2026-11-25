package com.uniroot.app.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File

/**
 * Real device information for the home screen — everything readable WITHOUT
 * any privilege (plus the root-state block that uses grant-independent
 * signals so it also works rooted-but-no-su).
 */
object DeviceInfoPanel {

    data class Rootability(
        val rootable: Boolean,
        val summary: String,
        val detail: String,
    )

    private val SAMSUNG_NAMES = mapOf(
        // Galaxy S26 series (2026)
        "SM-S948" to "Galaxy S26 Ultra", "SM-S947" to "Galaxy S26 Edge",
        "SM-S946" to "Galaxy S26+", "SM-S943" to "Galaxy S26 Edge",
        "SM-S941" to "Galaxy S26",
        // Galaxy S25 series (2025)
        "SM-S938" to "Galaxy S25 Ultra", "SM-S937" to "Galaxy S25 Edge",
        "SM-S936" to "Galaxy S25+", "SM-S931" to "Galaxy S25",
        "SM-S731" to "Galaxy S25 FE",
        // Galaxy S24 series (2024)
        "SM-S928" to "Galaxy S24 Ultra", "SM-S926" to "Galaxy S24+",
        "SM-S921" to "Galaxy S24", "SM-S721" to "Galaxy S24 FE",
        // Galaxy S23 series (2023)
        "SM-S918" to "Galaxy S23 Ultra", "SM-S916" to "Galaxy S23+",
        "SM-S911" to "Galaxy S23", "SM-S711" to "Galaxy S23 FE",
        // Galaxy S22 series (2022)
        "SM-S908" to "Galaxy S22 Ultra", "SM-S906" to "Galaxy S22+",
        "SM-S901" to "Galaxy S22",
        // Galaxy S21 series (2021)
        "SM-G998" to "Galaxy S21 Ultra", "SM-G996" to "Galaxy S21+",
        "SM-G991" to "Galaxy S21", "SM-G990" to "Galaxy S21 FE",
        // Galaxy Note 20 & S20 series (2020)
        "SM-N986" to "Galaxy Note 20 Ultra 5G", "SM-N985" to "Galaxy Note 20 Ultra",
        "SM-N981" to "Galaxy Note 20 5G", "SM-N980" to "Galaxy Note 20",
        "SM-G988" to "Galaxy S20 Ultra", "SM-G986" to "Galaxy S20+ 5G",
        "SM-G985" to "Galaxy S20+", "SM-G981" to "Galaxy S20 5G",
        "SM-G980" to "Galaxy S20", "SM-G781" to "Galaxy S20 FE 5G",
        "SM-G780" to "Galaxy S20 FE",
        // Galaxy Note 10 & S10 series (2019)
        "SM-N976" to "Galaxy Note 10+ 5G", "SM-N975" to "Galaxy Note 10+",
        "SM-N971" to "Galaxy Note 10 5G", "SM-N970" to "Galaxy Note 10",
        "SM-G977" to "Galaxy S10 5G", "SM-G975" to "Galaxy S10+",
        "SM-G973" to "Galaxy S10", "SM-G970" to "Galaxy S10e",
        // Galaxy Z Fold -> 8
        "SM-F976" to "Galaxy Z Fold 8", "SM-F971" to "Galaxy Z Fold 8",
        "SM-F966" to "Galaxy Z Fold 7", "SM-F956" to "Galaxy Z Fold 6",
        "SM-F946" to "Galaxy Z Fold 5", "SM-F936" to "Galaxy Z Fold 4",
        "SM-F926" to "Galaxy Z Fold 3", "SM-F916" to "Galaxy Z Fold 2",
        "SM-F907" to "Galaxy Fold 5G", "SM-F900" to "Galaxy Fold",
        // Galaxy Z Flip -> 7
        "SM-F766" to "Galaxy Z Flip 7", "SM-F736" to "Galaxy Z Flip 7",
        "SM-F761" to "Galaxy Z Flip 7 FE", "SM-F741" to "Galaxy Z Flip 6",
        "SM-F731" to "Galaxy Z Flip 5", "SM-F721" to "Galaxy Z Flip 4",
        "SM-F711" to "Galaxy Z Flip 3", "SM-F707" to "Galaxy Z Flip 5G",
        "SM-F700" to "Galaxy Z Flip",
        // Galaxy A / M / E series
        "SM-A566" to "Galaxy A56 5G", "SM-A556" to "Galaxy A55 5G",
        "SM-A546" to "Galaxy A54 5G", "SM-A536" to "Galaxy A53 5G",
        "SM-A528" to "Galaxy A52s 5G", "SM-A366" to "Galaxy A36 5G",
        "SM-A356" to "Galaxy A35 5G", "SM-A346" to "Galaxy A34 5G",
        "SM-A336" to "Galaxy A33 5G", "SM-A266" to "Galaxy A26 5G",
        "SM-A256" to "Galaxy A25 5G", "SM-A245" to "Galaxy A24",
        "SM-A166" to "Galaxy A16 5G", "SM-A165" to "Galaxy A16 4G",
        "SM-A156" to "Galaxy A15 5G", "SM-A155" to "Galaxy A15 4G",
        "SM-A146" to "Galaxy A14 5G", "SM-A145" to "Galaxy A14",
        "SM-A076" to "Galaxy A07 5G", "SM-A075" to "Galaxy A07",
        "SM-A066" to "Galaxy A06 5G", "SM-A065" to "Galaxy A06",
        "SM-A057" to "Galaxy A05s", "SM-A055" to "Galaxy A05",
        "SM-M556" to "Galaxy M55 5G", "SM-M356" to "Galaxy M35 5G",
        "SM-E736" to "Galaxy E56",
        // Galaxy Tab S & Tab A series
        "SM-X926" to "Galaxy Tab S10 Ultra", "SM-X920" to "Galaxy Tab S10 Ultra",
        "SM-X826" to "Galaxy Tab S10+", "SM-X820" to "Galaxy Tab S10+",
        "SM-X916" to "Galaxy Tab S9 Ultra", "SM-X910" to "Galaxy Tab S9 Ultra",
        "SM-X816" to "Galaxy Tab S9+", "SM-X810" to "Galaxy Tab S9+",
        "SM-X716" to "Galaxy Tab S9", "SM-X710" to "Galaxy Tab S9",
        "SM-X616" to "Galaxy Tab S9 FE+", "SM-X610" to "Galaxy Tab S9 FE+",
        "SM-X516" to "Galaxy Tab S9 FE", "SM-X510" to "Galaxy Tab S9 FE",
        "SM-X218" to "Galaxy Tab A9+", "SM-X216" to "Galaxy Tab A9+",
        "SM-X210" to "Galaxy Tab A9+", "SM-X115" to "Galaxy Tab A9",
        "SM-X110" to "Galaxy Tab A9",
    )

    private val PRE_GKI_MODEL_PREFIXES = listOf(
        "SM-G99", "SM-N98", "SM-G98", "SM-G78", "SM-N97", "SM-G97",
        "SM-F926", "SM-F916", "SM-F907", "SM-F900",
        "SM-F711", "SM-F707", "SM-F700", "SM-A528",
    )

    private val MT6789_MODEL_PREFIXES = listOf(
        "SM-A155", "SM-A165", "SM-A075", "SM-A245", "SM-X110", "SM-X115",
    )

    private val BUNDLED_DF_KMIS = setOf(
        "android12-5.10", "android13-5.10", "android13-5.15", "android14-5.15",
        "android14-6.1", "android15-6.6", "android16-6.12", "android17-6.18",
    )

    internal fun normalizeSamsungModel(rawModel: String): String {
        val trimmed = rawModel.trim()
        val base = trimmed.substringBefore('/').uppercase()
        return if (Regex("^[A-Z]\\d{3}[A-Z0-9]*$").matches(base)) "SM-$base" else base
    }

    internal fun marketingNameFor(model: String, fallbackDevice: String = "?"): String {
        val trimmed = model.trim()
        val normalized = normalizeSamsungModel(trimmed)
        SAMSUNG_NAMES.forEach { (k, v) ->
            if (normalized.startsWith(k, ignoreCase = true)) {
                val displayCode = trimmed.ifBlank { normalized }
                return "$v ($displayCode)"
            }
        }
        return trimmed.ifBlank { fallbackDevice.ifBlank { "?" } }
    }

    fun marketingName(): String = marketingNameFor(Build.MODEL ?: "", Build.DEVICE ?: "?")

    private fun prop(name: String): String? = runCatching {
        val sp = Class.forName("android.os.SystemProperties")
        sp.getMethod("get", String::class.java).invoke(null, name) as? String
    }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }

    internal fun shippingApiLevel(): Int? =
        prop("ro.product.first_api_level")?.toIntOrNull()
            ?: prop("ro.board.first_api_level")?.toIntOrNull()

    private fun fmtBytes(b: Long): String {
        val gb = b / (1024.0 * 1024 * 1024)
        return if (gb >= 10) String.format("%.0f GB", gb) else String.format("%.1f GB", gb)
    }

    internal fun majorMinorKernel(release: String): Pair<Int, Int>? {
        val m = Regex("^\\s*(\\d+)\\.(\\d+)").find(release) ?: return null
        val major = m.groupValues[1].toIntOrNull() ?: return null
        val minor = m.groupValues[2].toIntOrNull() ?: return null
        return major to minor
    }

    /**
     * Kernel KMI parsed from a Linux release string:
     * - "6.6.127-android15-8-peeee058-abogkiF966USQUCCZIH-4k" -> "android15-6.6"
     * - "android15-6.6" -> "android15-6.6"
     * - Samsung GKI 2.0 kernels omitting `-androidXX` in uname -r (common on
     *   S22 5.10, S23 5.15, S24 6.1) -> resolved via major.minor + shipping API.
     */
    internal fun kmiFromRelease(release: String, firstApiLevel: Int? = null): String? {
        Regex("(\\d+\\.\\d+)\\.\\d+-android(\\d+)").find(release)?.let { m ->
            return "android${m.groupValues[2]}-${m.groupValues[1]}"
        }
        Regex("android(\\d+)-(\\d+\\.\\d+)").find(release)?.let { m ->
            return "android${m.groupValues[1]}-${m.groupValues[2]}"
        }
        val (major, minor) = majorMinorKernel(release) ?: return null
        return when ("$major.$minor") {
            "5.10" -> if (firstApiLevel != null && firstApiLevel >= 33) "android13-5.10" else "android12-5.10"
            "5.15" -> if (firstApiLevel != null && firstApiLevel >= 34) "android14-5.15" else "android13-5.15"
            "6.1" -> "android14-6.1"
            "6.6" -> "android15-6.6"
            "6.12" -> "android16-6.12"
            "6.18" -> "android17-6.18"
            else -> null
        }
    }

    /** Kernel KMI parsed from the running kernel release string (e.g. android15-6.6). */
    fun kmi(): String? = kmiFromRelease(kernelRelease(), shippingApiLevel())

    fun kernelRelease(): String = System.getProperty("os.version") ?: Build.VERSION.INCREMENTAL ?: "?"

    fun is64Bit(): Boolean = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()

    internal fun isPreGkiSamsung(model: String, firstApiLevel: Int? = null): Boolean {
        if (firstApiLevel != null && firstApiLevel in 1..30) return true
        val norm = normalizeSamsungModel(model)
        return PRE_GKI_MODEL_PREFIXES.any { norm.startsWith(it, ignoreCase = true) }
    }

    internal fun isMt6789Platform(model: String, socModel: String = ""): Boolean {
        if (socModel.contains("MT6789", ignoreCase = true) || socModel.contains("G99", ignoreCase = true)) {
            return true
        }
        val norm = normalizeSamsungModel(model)
        return MT6789_MODEL_PREFIXES.any { norm.startsWith(it, ignoreCase = true) }
    }

    internal fun assessDirtyFragRootability(
        is64Bit: Boolean,
        kmi: String?,
        bundledKmis: Set<String> = BUNDLED_DF_KMIS,
        kernelRelease: String = "",
        model: String = "",
        socModel: String = "",
        firstApiLevel: Int? = null,
    ): Rootability {
        if (!is64Bit) {
            return Rootability(
                rootable = false,
                summary = "No — 64-bit ARM required",
                detail = "Not rootable — DirtyFrag requires a 64-bit ARM kernel",
            )
        }
        if (kmi != null && kmi in bundledKmis) {
            if (isMt6789Platform(model, socModel)) {
                return Rootability(
                    rootable = true,
                    summary = "Experimental — MediaTek MT6789 ($kmi)",
                    detail = "Experimental on MediaTek MT6789 ($kmi) — bundled Samsung KDP module targets Snapdragon/Exynos",
                )
            }
            return Rootability(
                rootable = true,
                summary = "Yes — DirtyFrag ($kmi)",
                detail = "Rootable via DirtyFrag (KMI: $kmi)",
            )
        }
        if (kmi != null) {
            return Rootability(
                rootable = false,
                summary = "No — unsupported KMI ($kmi)",
                detail = "Not rootable — no bundled DirtyFrag module for $kmi",
            )
        }
        val mm = majorMinorKernel(kernelRelease)
        if (mm != null && (mm.first < 5 || (mm.first == 5 && mm.second < 10))) {
            val ver = "${mm.first}.${mm.second}"
            return Rootability(
                rootable = false,
                summary = "No — Linux $ver (< 5.10 non-GKI; needs custom boot.img)",
                detail = "Not rootable via LKM — Linux $ver is pre-GKI 2.0 (LKM starts at S22 / 5.10+; requires custom boot.img)",
            )
        }
        if (isPreGkiSamsung(model, firstApiLevel)) {
            return Rootability(
                rootable = false,
                summary = "No — pre-GKI kernel (< 5.10; needs custom boot.img)",
                detail = "Not rootable via LKM — pre-GKI 2.0 Samsung generation (< 5.10; requires custom boot.img)",
            )
        }
        return Rootability(
            rootable = false,
            summary = "Unknown — non-GKI kernel",
            detail = "Could not detect GKI 2.0 KMI from kernel release",
        )
    }

    fun bundledDirtyFragKmis(context: Context): Set<String> {
        val fromAssets = runCatching {
            context.assets.list("df/ghostsam/payloads/dirtyfrag")
                ?.mapNotNull { name ->
                    if (name.startsWith("dfr_lkm-") && name.endsWith(".ko")) {
                        name.removePrefix("dfr_lkm-").removeSuffix(".ko")
                    } else null
                }?.toSet()
        }.getOrNull().orEmpty()
        return if (fromAssets.isNotEmpty()) fromAssets else BUNDLED_DF_KMIS
    }

    fun dirtyFragRootability(context: Context): Rootability =
        assessDirtyFragRootability(
            is64Bit = is64Bit(),
            kmi = kmi(),
            bundledKmis = bundledDirtyFragKmis(context),
            kernelRelease = kernelRelease(),
            model = Build.MODEL ?: "",
            socModel = runCatching { Build.SOC_MODEL }.getOrNull().orEmpty(),
            firstApiLevel = shippingApiLevel(),
        )

    fun oneUiVersion(): String? = prop("ro.build.version.oneui")?.let { v ->
        // Samsung encodes e.g. 80100 -> 8.1.0
        if (v.length >= 5) "${v[0]}.${v[1]}.${v[2]}" else v
    }

    fun knoxState(): String? {
        val warranty = prop("ro.boot.warranty_bit") ?: prop("ro.warranty_bit") ?: return null
        val knox = prop("ro.boot.warrantybit") ?: prop("knox") ?: warranty
        return if (warranty == "1" || knox == "1") "Tripped (0x1)" else "Intact (0x0)"
    }

    /** All non-privileged info rows, ready for the home card. */
    fun rows(context: Context): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        rows += "Device" to marketingName()
        rows += "SoC" to listOfNotNull(
            runCatching { Build.SOC_MANUFACTURER }.getOrNull(),
            runCatching { Build.SOC_MODEL }.getOrNull(),
        ).joinToString(" ").ifBlank { Build.HARDWARE ?: "?" }
        rows += "CPU" to "${Runtime.getRuntime().availableProcessors()} cores · ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"} · ${if (is64Bit()) "64-bit" else "32-bit"}"
        rows += "Android" to listOfNotNull(
            Build.VERSION.RELEASE,
            "patch ${Build.VERSION.SECURITY_PATCH}",
            oneUiVersion()?.let { "One UI $it" },
        ).joinToString(" · ")
        rows += "Kernel" to listOfNotNull(kernelRelease(), kmi()).joinToString(" · ")
        rows += "Rootable" to dirtyFragRootability(context).summary
        rows += "Fingerprint" to (Build.FINGERPRINT ?: Build.DISPLAY ?: "?")
        val am = context.getSystemService(ActivityManager::class.java)
        runCatching {
            val mi = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(mi)
            rows += "RAM" to "${fmtBytes(mi.totalMem)} (${fmtBytes(mi.availMem)} free)"
        }
        runCatching {
            val st = StatFs(Environment.getDataDirectory().absolutePath)
            val total = st.totalBytes
            val free = st.availableBytes
            rows += "Storage" to "${fmtBytes(total - free)} used of ${fmtBytes(total)}"
        }
        runCatching {
            val wm = context.getSystemService(android.view.WindowManager::class.java) ?: return@runCatching
            val m = wm.maximumWindowMetrics
            val b = m.bounds
            val refresh = context.display.refreshRate
            rows += "Display" to "${b.width()}×${b.height()} @ ${"%.0f".format(refresh)} Hz"
        }
        runCatching {
            val batt = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batt != null) {
                val level = batt.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batt.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    val pct = level * 100 / scale
                    val status = batt.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val state = when (status) {
                        BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                        BatteryManager.BATTERY_STATUS_FULL -> "full"
                        else -> "on battery"
                    }
                    rows += "Battery" to "$pct% ($state)"
                }
            }
        }
        knoxState()?.let { rows += "Knox warranty" to it }
        rows += "Uptime" to fmtUptime(android.os.SystemClock.elapsedRealtime())
        return rows
    }

    private fun fmtUptime(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    /**
     * Live root state for the current kernel boot:
     * 1. Working `su -c id` (uid=0) -> confirmed Rooted (and stamped for this boot_id)
     * 2. Same kernel boot_id as a successful UniRoot run -> Rooted (persists
     *    across zygote / surfaceflinger soft reboots even when UniRoot itself
     *    has not been granted su)
     * 3. Otherwise Not rooted.
     */
    fun rootState(context: Context? = null): Triple<String, String, String> {
        val su = runCatching {
            val p = ProcessBuilder("su", "-c", "id").start()
            val done = p.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)
            if (!done) { runCatching { p.destroy() }; "" }
            else p.inputStream.bufferedReader().use { it.readText() }
        }.getOrDefault("")
        if (su.contains("uid=0")) {
            if (context != null) BootRootState.markRooted(context)
            return Triple("Rooted", "", "su uid=0 — KernelSU active")
        }
        if (context != null && BootRootState.isRootedThisBoot(context)) {
            return Triple("Rooted", "", "Active this boot (persists across soft reboot)")
        }
        val modules = runCatching { File("/proc/modules").readText() }.getOrDefault("")
        return when {
            modules.contains("kernelsu", ignoreCase = true) ->
                Triple("Not rooted", "", "module loaded but su is down — run Root again")
            else -> Triple("Not rooted", "", "no KernelSU module loaded")
        }
    }

    fun flavor(context: Context): String {
        val next = KsuManagerDetector.find(context, next = true)
        val classic = KsuManagerDetector.find(context, next = false)
        return when {
            next != null && classic != null -> "KernelSU Next + classic installed"
            next != null -> if (next.spoofed) "KernelSU Next (spoofed)" else "KernelSU Next"
            classic != null -> if (classic.spoofed) "KernelSU (spoofed)" else "KernelSU"
            else -> "no manager installed"
        }
    }
}
