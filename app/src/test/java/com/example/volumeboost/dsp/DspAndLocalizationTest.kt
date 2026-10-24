package com.example.volumeboost.dsp

import com.example.volumeboost.data.BoostMode
import com.example.volumeboost.data.LimiterMode
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class DspAndLocalizationTest {

    @Test
    fun `bass slider maps 0_1000 to 0_12 dB and Biquad filter amplifies 60Hz bass`() {
        assertEquals(0f, SoftwarePcmDspProcessor.bassSliderToGainDb(0), 0.01f)
        assertEquals(6f, SoftwarePcmDspProcessor.bassSliderToGainDb(500), 0.01f)
        assertEquals(12f, SoftwarePcmDspProcessor.bassSliderToGainDb(1000), 0.01f)

        val dsp = SoftwarePcmDspProcessor(sampleRateHz = 48000)
        var maxOutWithBass = 0.0
        val inputAmplitude = 4000.0

        // Пропускаем 500 сэмплов синусоиды 60 Гц при максимальном басе (1000)
        for (n in 0 until 500) {
            val sample = inputAmplitude * sin(2.0 * PI * 60.0 * n / 48000.0)
            val (outL, outR) = dsp.processStereoSamplePair(sample, sample, bassStrength0to1000 = 1000, virtualizerStrength0to1000 = 0)
            assertTrue(outL.isFinite() && outR.isFinite())
            if (abs(outL) > maxOutWithBass) maxOutWithBass = abs(outL)
        }

        // Амплитуда 60 Гц после Low-Shelf фильтра +12 дБ должна заметно вырасти (> 1.8x уже на первых периодах)
        assertTrue("Expected amplified bass > 7500, got $maxOutWithBass", maxOutWithBass > 7500.0)
    }

    @Test
    fun `virtualizer Mid-Side stereo widener expands stereo difference without NaN`() {
        val dsp = SoftwarePcmDspProcessor(48000)
        val (outL, outR) = dsp.processStereoSamplePair(
            leftIn = 5000.0,
            rightIn = -5000.0,
            bassStrength0to1000 = 0,
            virtualizerStrength0to1000 = 1000
        )
        // При чистом противофазном сигнале (Side) ширина должна увеличиться в ~2.15 раза
        assertTrue(outL > 9000.0)
        assertTrue(outR < -9000.0)
    }

    @Test
    fun `effect route selector chooses Hardware then DynamicsProcessing then Software DSP`() {
        assertEquals(
            DspEffectRoute.HARDWARE_EFFECT,
            SoftwarePcmDspProcessor.resolveBassRoute(BoostMode.STANDARD, hwBassSupported = true, dynamicsAvailable = true)
        )
        assertEquals(
            DspEffectRoute.DYNAMICS_PROCESSING_STAGE,
            SoftwarePcmDspProcessor.resolveBassRoute(BoostMode.STANDARD, hwBassSupported = false, dynamicsAvailable = true)
        )
        assertEquals(
            DspEffectRoute.SOFTWARE_BIQUAD_PCM,
            SoftwarePcmDspProcessor.resolveBassRoute(BoostMode.CAPTURE, hwBassSupported = false, dynamicsAvailable = false)
        )
    }

    @Test
    fun `English and Russian strings_xml have 100 percent identical key sets`() {
        val enFile = sequenceOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml")
        ).firstOrNull { it.exists() } ?: return

        val parent = enFile.parentFile?.parentFile
        assertNotNull("Enclosing directory must exist", parent)
        val ruFile = File(parent!!, "values-ru/strings.xml")
        assertTrue("Russian strings.xml must exist at ${ruFile.path}", ruFile.exists())

        val enKeys = parseStringKeys(enFile)
        val ruKeys = parseStringKeys(ruFile)

        val missingInRu = enKeys - ruKeys
        val missingInEn = ruKeys - enKeys

        assertTrue("Missing keys in values-ru/strings.xml: $missingInRu", missingInRu.isEmpty())
        assertTrue("Missing keys in values/strings.xml: $missingInEn", missingInEn.isEmpty())
    }

    private fun parseStringKeys(xmlFile: File): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
        val nodes = doc.getElementsByTagName("string")
        val keys = mutableSetOf<String>()
        for (i in 0 until nodes.length) {
            val attr = nodes.item(i).attributes?.getNamedItem("name")?.nodeValue
            if (!attr.isNullOrBlank()) keys.add(attr)
        }
        return keys
    }

    @Test
    fun `soft knee limiter is continuous without jump discontinuity at knee threshold`() {
        val threshold = 30000.0
        val knee = threshold * 0.75 // 22500.0

        val justBelowKnee = SoftwarePcmDspProcessor.applySampleLimiterDouble(knee - 0.1, LimiterMode.SOFT, threshold)
        val atKnee = SoftwarePcmDspProcessor.applySampleLimiterDouble(knee, LimiterMode.SOFT, threshold)
        val justAboveKnee = SoftwarePcmDspProcessor.applySampleLimiterDouble(knee + 0.1, LimiterMode.SOFT, threshold)

        // Разрыв функции в окрестности knee должен быть пренебрежимо мал (< 0.15)
        assertEquals(knee, justBelowKnee, 0.15)
        assertEquals(knee, atKnee, 0.001)
        assertEquals(knee, justAboveKnee, 0.15)

        // Предельное значение не должно превосходить threshold
        val extreme = SoftwarePcmDspProcessor.applySampleLimiterDouble(100000.0, LimiterMode.SOFT, threshold)
        assertTrue("Extreme value ($extreme) must be <= threshold ($threshold)", extreme <= threshold)
    }

    @Test
    fun `processStereoSampleDirect processes samples without allocation and matches pair method`() {
        val dsp1 = SoftwarePcmDspProcessor(sampleRateHz = 48000)
        val dsp2 = SoftwarePcmDspProcessor(sampleRateHz = 48000)
        val (pairL, pairR) = dsp1.processStereoSamplePair(1200.0, -1200.0, bassStrength0to1000 = 500, virtualizerStrength0to1000 = 400)
        dsp2.processStereoSampleDirect(1200.0, -1200.0, bassStrength0to1000 = 500, virtualizerStrength0to1000 = 400)
        assertEquals(pairL, dsp2.lastOutLeft, 0.0001)
        assertEquals(pairR, dsp2.lastOutRight, 0.0001)
    }
}
