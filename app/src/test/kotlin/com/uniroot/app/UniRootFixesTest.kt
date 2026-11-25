package com.uniroot.app

import com.uniroot.app.engine.BootRootState
import com.uniroot.app.engine.DeviceInfoPanel
import com.uniroot.app.engine.GitHubSupport
import com.uniroot.app.engine.KsuManagerDetector
import com.uniroot.app.engine.RmgTarget
import com.uniroot.app.newmethod.DfKsudUpdater
import com.uniroot.app.newmethod.KsudNextProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UniRootFixesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun recognizesSamsungModelsAcrossOpenIssuesAndFlagships() {
        // S-series & Note (S10e through S26)
        assertEquals("Galaxy S10e (SM-G970U)", DeviceInfoPanel.marketingNameFor("SM-G970U"))
        assertEquals("Galaxy Note 20 Ultra 5G (SM-N986U)", DeviceInfoPanel.marketingNameFor("SM-N986U"))
        assertEquals("Galaxy S21 (sm-g991u)", DeviceInfoPanel.marketingNameFor("sm-g991u"))
        assertEquals("Galaxy S21+ (SM-G996U1)", DeviceInfoPanel.marketingNameFor("SM-G996U1"))
        assertEquals("Galaxy S21 Ultra (SM-G998U)", DeviceInfoPanel.marketingNameFor("SM-G998U"))
        assertEquals("Galaxy S22+ (SM-S906U)", DeviceInfoPanel.marketingNameFor("SM-S906U"))
        assertEquals("Galaxy S22 Ultra (SM-S908U1)", DeviceInfoPanel.marketingNameFor("SM-S908U1"))
        assertEquals("Galaxy S23 (SM-S911N)", DeviceInfoPanel.marketingNameFor("SM-S911N"))
        assertEquals("Galaxy S24 FE (SM-S721B)", DeviceInfoPanel.marketingNameFor("SM-S721B"))
        assertEquals("Galaxy S24 Ultra (SM-S928B)", DeviceInfoPanel.marketingNameFor("SM-S928B"))
        assertEquals("Galaxy S25+ (SM-S936B)", DeviceInfoPanel.marketingNameFor("SM-S936B"))
        assertEquals("Galaxy S25 Ultra (SM-S938B)", DeviceInfoPanel.marketingNameFor("SM-S938B"))
        assertEquals("Galaxy S26 Ultra (SM-S948W)", DeviceInfoPanel.marketingNameFor("SM-S948W"))
        // Fold & Flip (including bare code f956n)
        assertEquals("Galaxy Z Fold 6 (f956n)", DeviceInfoPanel.marketingNameFor("f956n"))
        assertEquals("Galaxy Z Fold 7 (SM-F966U)", DeviceInfoPanel.marketingNameFor("SM-F966U"))
        assertEquals("Galaxy Z Flip 7 (SM-F766B)", DeviceInfoPanel.marketingNameFor("SM-F766B"))
        // A-series & Tab (including /DS and /DSN suffixes)
        assertEquals("Galaxy A07 (SM-A075F)", DeviceInfoPanel.marketingNameFor("SM-A075F"))
        assertEquals("Galaxy A15 4G (SM-A155F/DSN)", DeviceInfoPanel.marketingNameFor("SM-A155F/DSN"))
        assertEquals("Galaxy A15 5G (SM-A156U1)", DeviceInfoPanel.marketingNameFor("SM-A156U1"))
        assertEquals("Galaxy A16 4G (SM-A165F)", DeviceInfoPanel.marketingNameFor("SM-A165F"))
        assertEquals("Galaxy A16 5G (SM-A166B)", DeviceInfoPanel.marketingNameFor("SM-A166B"))
        assertEquals("Galaxy A35 5G (SM-A356B)", DeviceInfoPanel.marketingNameFor("SM-A356B"))
        assertEquals("Galaxy A36 5G (SM-A366B)", DeviceInfoPanel.marketingNameFor("SM-A366B"))
        assertEquals("Galaxy A56 5G (SM-A566B/DS)", DeviceInfoPanel.marketingNameFor("SM-A566B/DS"))
        assertEquals("Galaxy Tab A9+ (SM-X216B)", DeviceInfoPanel.marketingNameFor("SM-X216B"))
        assertEquals("SM-X999", DeviceInfoPanel.marketingNameFor("SM-X999"))
    }

    @Test
    fun parsesKmiFromTaggedAndUntaggedSamsungGki2ReleaseStrings() {
        assertEquals(
            "android15-6.6",
            DeviceInfoPanel.kmiFromRelease("6.6.127-android15-8-peeee058-abogkiF966USQUCCZIH-4k"),
        )
        assertEquals(
            "android16-6.12",
            DeviceInfoPanel.kmiFromRelease("6.12.38-android16-5-abA165FXXSBDZH6-4k"),
        )
        assertEquals(
            "android14-6.1",
            DeviceInfoPanel.kmiFromRelease("6.1.128-android14-11-32532371-abX216BXXSCEZG6"),
        )
        assertEquals(
            "android13-5.15",
            DeviceInfoPanel.kmiFromRelease("5.15.189-android13-8-33641424"),
        )
        // Untagged Samsung GKI 2.0 uname -r strings (S22 5.10, S23 5.15, S24 6.1)
        assertEquals("android12-5.10", DeviceInfoPanel.kmiFromRelease("5.10.236-abS908USQS4", firstApiLevel = 31))
        assertEquals("android13-5.10", DeviceInfoPanel.kmiFromRelease("5.10.245-abA346BXXU", firstApiLevel = 33))
        assertEquals("android13-5.15", DeviceInfoPanel.kmiFromRelease("5.15.167-abS918USQS5", firstApiLevel = 33))
        assertEquals("android14-5.15", DeviceInfoPanel.kmiFromRelease("5.15.202-abA556EXXU", firstApiLevel = 34))
        assertEquals("android14-6.1", DeviceInfoPanel.kmiFromRelease("6.1.99-abS928USQS4"))
        assertEquals("android15-6.6", DeviceInfoPanel.kmiFromRelease("android15-6.6"))
        // Pre-GKI 2.0 kernels (Note 20 4.19, S21 5.4) have no GKI 2.0 KMI
        assertNull(DeviceInfoPanel.kmiFromRelease("4.19.113-27102345-abN986USQS"))
        assertNull(DeviceInfoPanel.kmiFromRelease("5.4.274-qgki-30957850-abG991USQSJHZB1"))
    }

    @Test
    fun assessesCompatibilityAcrossFlagshipMidrangeAndPreGkiDevices() {
        val s22 = DeviceInfoPanel.assessDirtyFragRootability(true, "android12-5.10", kernelRelease = "5.10.226-android12-9", model = "SM-S906U")
        assertTrue(s22.rootable)
        assertTrue(s22.summary.startsWith("Yes"))

        val a155g = DeviceInfoPanel.assessDirtyFragRootability(true, "android13-5.15", kernelRelease = "5.15.189-android13-8", model = "SM-A156U1")
        assertTrue(a155g.rootable)
        assertTrue(a155g.summary.startsWith("Yes"))

        // MediaTek MT6789 devices (A15 4G / A16 4G / A07) surface an explicit compatibility warning
        val a154g = DeviceInfoPanel.assessDirtyFragRootability(
            is64Bit = true,
            kmi = "android16-6.12",
            kernelRelease = "6.12.38",
            model = "SM-A155F/DSN",
        )
        assertTrue(a154g.rootable)
        assertTrue(a154g.summary.startsWith("Experimental"))
        assertTrue(a154g.detail.contains("MT6789"))

        // Explicit Linux < 5.10 (Note 20 4.19, S21 5.4)
        val note20 = DeviceInfoPanel.assessDirtyFragRootability(
            true,
            null,
            kernelRelease = "4.19.113-27102345-abN986USQS",
            model = "SM-N986U",
        )
        assertFalse(note20.rootable)
        assertTrue(note20.summary.contains("4.19"))

        val s21 = DeviceInfoPanel.assessDirtyFragRootability(
            true,
            null,
            kernelRelease = "5.4.274-qgki-30957850-abG991USQSJHZB1",
            model = "SM-G991U",
        )
        assertFalse(s21.rootable)
        assertTrue(s21.summary.contains("5.4"))

        // Obfuscated Samsung build ID fallback (e.g. SM-G996U1 / G996U1UESJHZB1 or SM-G970U)
        val s21PlusBuildId = DeviceInfoPanel.assessDirtyFragRootability(
            true,
            null,
            kernelRelease = "G996U1UESJHZB1",
            model = "SM-G996U1",
        )
        assertFalse(s21PlusBuildId.rootable)
        assertTrue(s21PlusBuildId.summary.contains("pre-GKI"))

        val unsupportedKmi = DeviceInfoPanel.assessDirtyFragRootability(true, "android18-6.24")
        assertFalse(unsupportedKmi.rootable)
        assertFalse(DeviceInfoPanel.assessDirtyFragRootability(false, "android15-6.6").rootable)
    }

    @Test
    fun matchesRmgTargetsWithDualSimSuffixAndNorthAmericanUnlockedCodes() {
        val targets = listOf(
            RmgTarget(
                payloadId = "a56-6.6",
                displayName = "Galaxy A56",
                models = listOf("SM-A566B"),
                kernelVersions = listOf("6.6.77"),
                exploitUrl = "https://example.invalid/a",
                exploitSize = 1L,
                ksudUrl = "https://example.invalid/b",
                ksudSize = 1L,
            ),
            RmgTarget(
                payloadId = "s22u-5.10",
                displayName = "Galaxy S22 Ultra",
                models = listOf("SM-S908U"),
                kernelVersions = listOf("5.10.226"),
                exploitUrl = "https://example.invalid/c",
                exploitSize = 1L,
                ksudUrl = "https://example.invalid/d",
                ksudSize = 1L,
            ),
        )
        assertNotNull(GitHubSupport.matchTargetInList(targets, "SM-A566B/DS", "6.6.77"))
        assertNotNull(GitHubSupport.matchTargetInList(targets, "SM-S908U1", "5.10.226"))
        assertNull(GitHubSupport.matchTargetInList(targets, "SM-S908U1", "5.10.999"))
    }

    @Test
    fun bootRootStatePersistsAcrossSoftRebootsAndResetsOnFullReboot() {
        val bootIdFile = temporaryFolder.newFile("boot_id")
        bootIdFile.writeText("d9729297-384f-4d00-b748-08480315b79b\n")
        val boot1 = BootRootState.currentBootId(bootIdFile = bootIdFile)
        assertEquals("d9729297-384f-4d00-b748-08480315b79b", boot1)

        // Same kernel boot_id after zygote/surfaceflinger soft reboot -> stays rooted.
        assertTrue(BootRootState.matchesBoot(boot1, "d9729297-384f-4d00-b748-08480315b79b"))

        // Fresh UUID after full hardware reboot -> resets rooted state.
        assertFalse(BootRootState.matchesBoot(boot1, "11111111-2222-3333-4444-555555555555"))
        assertFalse(BootRootState.matchesBoot(null, boot1))
        assertFalse(BootRootState.matchesBoot("", boot1))
    }

    @Test
    fun detectsSpoofedKernelSuNextByCertificateAndFallback() {
        assertTrue(
            KsuManagerDetector.matchesCertificate(
                listOf(KsuManagerDetector.NEXT_CERT_SHA256),
                next = true,
            ),
        )
        assertFalse(
            KsuManagerDetector.matchesCertificate(
                listOf(KsuManagerDetector.NEXT_CERT_SHA256),
                next = false,
            ),
        )
        assertTrue(
            KsuManagerDetector.matchesCertificate(
                listOf(KsuManagerDetector.CLASSIC_CERT_SHA256.uppercase()),
                next = false,
            ),
        )
        assertTrue(
            KsuManagerDetector.matchesSpoofedFallback(
                next = true,
                versionName = "v3.4.2-spoofed",
                appLabel = "KernelSU-Next",
            ),
        )
        assertFalse(
            KsuManagerDetector.matchesSpoofedFallback(
                next = true,
                versionName = "v3.4.2",
                appLabel = "Unrelated App",
            ),
        )
    }

    @Test
    fun namesAndParsesDownloadedNextProfileToMatchBundledFormat() {
        assertEquals("3.4.2-kdp-samsung", DfKsudUpdater.nextProfileId("3.4.2"))
        assertEquals("3.4.2-kdp-samsung", DfKsudUpdater.nextProfileId("v3.4.2"))

        val parsed342 = KsudNextProfiles.parseProfileId("3.4.2-kdp-samsung")
        assertEquals("3.4.2", parsed342.version)
        assertEquals("kdp-samsung", parsed342.variant)
        assertEquals("3.4.2 · kdp-samsung", parsed342.label)

        val parsed340 = KsudNextProfiles.parseProfileId("3.4.0-kdp-samsung")
        assertEquals("3.4.0", parsed340.version)
        assertEquals("kdp-samsung", parsed340.variant)
        assertEquals("3.4.0 · kdp-samsung", parsed340.label)
    }
}
