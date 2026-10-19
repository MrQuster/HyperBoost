package com.example.volumeboost.device

import com.example.volumeboost.data.IslandRoutingPreference
import org.junit.Assert.*
import org.junit.Test

private class FakeEnvironmentSource(
    override val manufacturer: String = "Generic",
    override val brand: String = "Generic",
    override val model: String = "GenericPhone",
    override val device: String = "generic",
    override val displayBuildId: String = "AP3A.241005.015",
    override val androidRelease: String = "15",
    override val sdkInt: Int = 35,
    override val sdkIntFull: Int = 3500000,
    override val securityPatch: String = "2026-03-01",
    override val smallestWidthDp: Int = 392,
    override val hasHingeFeature: Boolean = false,
    override val hasMultipleDisplays: Boolean = false,
    override val cutoutStatus: TriStateStatus = TriStateStatus.SUPPORTED,
    override val queriedEffectUuids: Set<String> = setOf(
        "fe3199be-aed0-413f-87bb-11260eb63cf1", // LoudnessEnhancer
        "7261676f-6d75-7369-6364-28e2fd3ac39e"  // DynamicsProcessing
    ),
    override val focusNotificationStatus: TriStateStatus = TriStateStatus.SUPPORTED,
    override val promotedNotificationsAllowed: Boolean = true,
    override val hardwareSpecs: HardwareAudioSpecs = HardwareAudioSpecs(),
    private val props: Map<String, String> = emptyMap(),
    private val packages: Set<String> = emptySet(),
    private val probeSuccess: Boolean = false
) : SystemEnvironmentSource {
    override fun getSystemProperty(key: String): String = props[key].orEmpty()
    override fun isPackageInstalled(packageName: String): Boolean = packageName in packages
    override fun probeAudioEffectCreation(effectShortName: String): Boolean = probeSuccess
}

class DeviceProfileDetectorTest {

    @Test
    fun `detects Xiaomi 17T Pro HyperOS 4 on Android 17 API 37 with EEA region and formatted SDK_INT_FULL`() {
        val fakeXiaomi = FakeEnvironmentSource(
            manufacturer = "Xiaomi",
            brand = "Xiaomi",
            model = "250917TPRO",
            device = "rotko",
            displayBuildId = "OS4.0.2.0.WNBEUXM",
            androidRelease = "17",
            sdkInt = 37,
            sdkIntFull = 3700002,
            props = mapOf(
                "ro.mi.os.version.name" to "OS4.0.2.0.WNBEUXM",
                "ro.mi.os.version.code" to "4",
                "ro.product.mod_device" to "rotko_eea_global"
            ),
            packages = setOf("com.miui.securitycenter")
        )

        val profile = DeviceProfileDetector.detect(fakeXiaomi)
        assertEquals(OsFamily.HYPER_OS, profile.osFamily)
        assertEquals("EEA (Европа)", profile.romRegion)
        assertEquals("37.2", profile.apiLevelFullFormatted)
        assertEquals(DetectionConfidence.HIGH, profile.confidence)
        assertTrue(Capability.XIAOMI_SUPER_ISLAND in profile.capabilities)
        assertTrue(Capability.ANDROID_16_LIVE_UPDATES in profile.capabilities)

        // Проверка сериализации и десериализации JSON в JVM-тесте
        val json = profile.toJsonString()
        val restored = DeviceProfile.fromJsonString(json)
        assertNotNull(restored)
        assertEquals(profile.osFamily, restored!!.osFamily)
        assertEquals(profile.apiLevelFullFormatted, restored.apiLevelFullFormatted)
    }

    @Test
    fun `does NOT claim Android 16 Live Updates on API 35`() {
        val api35Pixel = FakeEnvironmentSource(
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 9",
            sdkInt = 35,
            sdkIntFull = 3500000,
            promotedNotificationsAllowed = true
        )

        val profile = DeviceProfileDetector.detect(api35Pixel)
        assertFalse(Capability.ANDROID_16_LIVE_UPDATES in profile.capabilities)
    }

    @Test
    fun `does NOT default to CN region when Xiaomi regional properties are unreadable`() {
        val xiaomiNoRegionProps = FakeEnvironmentSource(
            manufacturer = "Xiaomi",
            brand = "Redmi",
            displayBuildId = "UKQ1.231003.002",
            props = mapOf("ro.mi.os.version.name" to "OS2.0"),
            packages = setOf("com.miui.securitycenter")
        )

        val profile = DeviceProfileDetector.detect(xiaomiNoRegionProps)
        assertEquals(OsFamily.HYPER_OS, profile.osFamily)
        assertEquals("Неизвестно", profile.romRegion)
    }

    @Test
    fun `detects custom ROM on Samsung hardware when One UI properties and packages are missing`() {
        val lineageOnSamsung = FakeEnvironmentSource(
            manufacturer = "samsung",
            brand = "samsung",
            model = "SM-S918B",
            props = emptyMap(),
            packages = emptySet()
        )

        val profile = DeviceProfileDetector.detect(lineageOnSamsung)
        assertTrue(profile.isLikelyCustomRom)
        assertEquals(DetectionConfidence.LOW, profile.confidence)
        assertFalse(Capability.SAMSUNG_LIVE_NOTIF in profile.capabilities)
    }

    @Test
    fun `manual override resets mismatched OS version and does NOT fake Xiaomi Super Island on Pixel`() {
        val pixelEnv = FakeEnvironmentSource(
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 9 Pro",
            sdkInt = 36,
            sdkIntFull = 3600001
        )

        val overriddenToXiaomi = DeviceProfileDetector.detect(pixelEnv, OsOverrideOption.XIAOMI_HYPER_OS)
        assertEquals(OsFamily.HYPER_OS, overriddenToXiaomi.osFamily)
        assertTrue(overriddenToXiaomi.isManualOverride)
        assertEquals(DetectionConfidence.MANUAL, overriddenToXiaomi.confidence)
        // На физическом Pixel даже при ручном выборе профиля Xiaomi аппаратный XIAOMI_SUPER_ISLAND не подделывается
        assertFalse(Capability.XIAOMI_SUPER_ISLAND in overriddenToXiaomi.capabilities)
    }

    @Test
    fun `handles empty and garbage properties gracefully with LOW confidence`() {
        val garbageEnv = FakeEnvironmentSource(
            manufacturer = "   ",
            brand = "???",
            model = "",
            device = "",
            sdkInt = 29,
            sdkIntFull = 2900000,
            queriedEffectUuids = emptySet(),
            props = mapOf(
                "ro.build.version.oneui" to "NOT_A_NUMBER_###",
                "ro.mi.os.version.name" to ""
            )
        )

        val profile = DeviceProfileDetector.detect(garbageEnv)
        assertEquals(OsFamily.AOSP_GENERIC, profile.osFamily)
        assertEquals(DetectionConfidence.LOW, profile.confidence)

        val plan = FeatureRegistry.resolvePlan(profile, IslandRoutingPreference.AUTO)
        assertEquals(IslandStrategy.CLASSIC_MEDIA_NOTIFICATION, plan.selectedIslandStrategy)
        assertEquals(GlassQualityTier.LIGHTWEIGHT_TRANSLUCENT, plan.glassQualityTier)
    }
}
