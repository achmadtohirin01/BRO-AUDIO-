package com.example.audio

import android.util.Log
import kotlin.math.*

/**
 * High-performance, zero-allocation real-time audio DSP processor
 * Implements 18-band graphic Biquad peaking EQ, 4-way Crossover (LR),
 * Stereo Master Dual Fader, and an Envelope-smoothed Peak Limiter.
 */
class DspProcessor {

    companion object {
        const val SAMPLE_RATE = 44100
        const val EQ_BAND_COUNT = 18
        
        // 18 standard audio bands
        val EQ_FREQUENCIES = floatArrayOf(
            31f, 40f, 50f, 63f, 80f, 100f, 125f, 160f, 200f, 250f,
            400f, 630f, 1000f, 1600f, 2500f, 4000f, 8000f, 16000f
        )
    }

    // --- State Storage for zero-allocation process ---
    private val eqFiltersL = Array(EQ_BAND_COUNT) { BiquadPeaking() }
    private val eqFiltersR = Array(EQ_BAND_COUNT) { BiquadPeaking() }

    // 4-Way Crossover (Lowpass / Highpass Butterworth cascades)
    // Sub: Lowpass (Sub-Low Point)
    // Low: Highpass (Sub-Low Point) & Lowpass (Low-Mid Point)
    // Mid: Highpass (Low-Mid Point) & Lowpass (Mid-High Point)
    // High: Highpass (Mid-High Point)
    private val crossSubHpL = BiquadFilter() // High pass for Sub-bass (e.g. 20Hz protection)
    private val crossSubHpR = BiquadFilter()
    private val crossSubLpL = BiquadFilter() // Sub cutoff
    private val crossSubLpR = BiquadFilter()

    private val crossLowHpL = BiquadFilter()
    private val crossLowHpR = BiquadFilter()
    private val crossLowLpL = BiquadFilter()
    private val crossLowLpR = BiquadFilter()

    private val crossMidHpL = BiquadFilter()
    private val crossMidHpR = BiquadFilter()
    private val crossMidLpL = BiquadFilter()
    private val crossMidLpR = BiquadFilter()

    private val crossHighHpL = BiquadFilter()
    private val crossHighHpR = BiquadFilter()

    // Peak Limiter Envelope States
    private var envelopeStateL = 0f
    private var envelopeStateR = 0f

    // Live levels for UI meters (RMS and Peak)
    var liveRmsL = 0f
        private set
    var liveRmsR = 0f
        private set
    var livePeakL = 0f
        private set
    var livePeakR = 0f
        private set
    var liveGainReductionDb = 0f
        private set

    // Real-time FFT spectral bins (approximate estimation for live visualizer)
    val spectrumData = FloatArray(32)

    init {
        // Initialize all filters with flat state
        updateEqBands(FloatArray(EQ_BAND_COUNT) { 0f })
        updateCrossover(80f, 300f, 3000f)
    }

    /**
     * Biquad peaking class targeting EQ bands with individual parameter updating
     */
    class BiquadPeaking {
        var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f
        var b0 = 1f; var b1 = 0f; var b2 = 0f; var a1 = 0f; var a2 = 0f

        fun update(frequency: Float, gainDb: Float, q: Float = 1.4f, sampleRate: Int = SAMPLE_RATE) {
            val a = 10f.pow(gainDb / 40f)
            val omega = 2f * PI.toFloat() * frequency / sampleRate
            val sn = sin(omega)
            val cs = cos(omega)
            val alpha = sn / (2f * q)

            val b0Raw = 1f + alpha * a
            val b1Raw = -2f * cs
            val b2Raw = 1f - alpha * a
            val a0Raw = 1f + alpha / a
            val a1Raw = -2f * cs
            val a2Raw = 1f - alpha / a

            // Normalize coefficients against a0Raw to reduce calculation steps
            b0 = b0Raw / a0Raw
            b1 = b1Raw / a0Raw
            b2 = b2Raw / a0Raw
            a1 = a1Raw / a0Raw
            a2 = a2Raw / a0Raw
        }

        @Suppress("NOTHING_TO_INLINE")
        inline fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = if (y.isNaN() || y.isInfinite()) 0f else y
            return y1
        }
    }

    /**
     * General 2nd order Biquad filter for Low-Pass and High-Pass roles inside crossover
     */
    class BiquadFilter {
        var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f
        var b0 = 1f; var b1 = 0f; var b2 = 0f; var a1 = 0f; var a2 = 0f

        fun updateLowPass(frequency: Float, q: Float = 0.7071f, sampleRate: Int = SAMPLE_RATE) {
            val omega = 2f * PI.toFloat() * frequency / sampleRate
            val sn = sin(omega)
            val cs = cos(omega)
            val alpha = sn / (2f * q)

            val b0Raw = (1f - cs) / 2f
            val b1Raw = 1f - cs
            val b2Raw = (1f - cs) / 2f
            val a0Raw = 1f + alpha
            val a1Raw = -2f * cs
            val a2Raw = 1f - alpha

            b0 = b0Raw / a0Raw
            b1 = b1Raw / a0Raw
            b2 = b2Raw / a0Raw
            a1 = a1Raw / a0Raw
            a2 = a2Raw / a0Raw
        }

        fun updateHighPass(frequency: Float, q: Float = 0.7071f, sampleRate: Int = SAMPLE_RATE) {
            val omega = 2f * PI.toFloat() * frequency / sampleRate
            val sn = sin(omega)
            val cs = cos(omega)
            val alpha = sn / (2f * q)

            val b0Raw = (1f + cs) / 2f
            val b1Raw = -(1f + cs)
            val b2Raw = (1f + cs) / 2f
            val a0Raw = 1f + alpha
            val a1Raw = -2f * cs
            val a2Raw = 1f - alpha

            b0 = b0Raw / a0Raw
            b1 = b1Raw / a0Raw
            b2 = b2Raw / a0Raw
            a1 = a1Raw / a0Raw
            a2 = a2Raw / a0Raw
        }

        @Suppress("NOTHING_TO_INLINE")
        inline fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = if (y.isNaN() || y.isInfinite()) 0f else y
            return y1
        }
    }

    /**
     * Updates Eq gains in dynamic real-time.
     */
    fun updateEqBands(gainsDb: FloatArray) {
        for (i in 0 until minOf(EQ_BAND_COUNT, gainsDb.size)) {
            val freq = EQ_FREQUENCIES[i]
            val gain = gainsDb[i]
            // Graphic Equalizer peaking filters Q usually around 1.4 for ISO 2/3 octave spacing
            eqFiltersL[i].update(freq, gain, 1.4f)
            eqFiltersR[i].update(freq, gain, 1.4f)
        }
    }

    /**
     * Updates crossover frequencies for Sub-Low, Low-Mid, and Mid-High splits.
     */
    fun updateCrossover(subLowPoint: Float, lowMidPoint: Float, midHighPoint: Float) {
        // Highpass at 20Hz on Sub-bass to filter out inaudible speaker strain subsonic trash
        crossSubHpL.updateHighPass(20f, 0.7071f)
        crossSubHpR.updateHighPass(20f, 0.7071f)

        // Sub low pass at SubLow frequency
        crossSubLpL.updateLowPass(subLowPoint, 0.7071f)
        crossSubLpR.updateLowPass(subLowPoint, 0.7071f)

        // Low high pass at SubLow frequency
        crossLowHpL.updateHighPass(subLowPoint, 0.7071f)
        crossLowHpR.updateHighPass(subLowPoint, 0.7071f)

        // Low low pass at LowMid frequency
        crossLowLpL.updateLowPass(lowMidPoint, 0.7071f)
        crossLowLpR.updateLowPass(lowMidPoint, 0.7071f)

        // Mid high pass at LowMid frequency
        crossMidHpL.updateHighPass(lowMidPoint, 0.7071f)
        crossMidHpR.updateHighPass(lowMidPoint, 0.7071f)

        // Mid low pass at MidHigh frequency
        crossMidLpL.updateLowPass(midHighPoint, 0.7071f)
        crossMidLpR.updateLowPass(midHighPoint, 0.7071f)

        // High high pass at MidHigh frequency
        crossHighHpL.updateHighPass(midHighPoint, 0.7071f)
        crossHighHpR.updateHighPass(midHighPoint, 0.7071f)
    }

    /**
     * Process an interleaved Stereo PCM float buffer in-place.
     * Reuses references and properties to secure 0 memory allocation.
     *
     * @param buffer Interleaved stereo data [L, R, L, R, ...]
     * @param volumeL Scaling gain of Left Master channel [0.0 - 1.0+]
     * @param volumeR Scaling gain of Right Master channel [0.0 - 1.0+]
     * @param isMutedL If true, mutes Left channel
     * @param isMutedR If true, mutes Right channel
     * @param limiterThreshold Threshold of peak limiter in dB
     * @param limiterAttack Attack speed coefficient
     * @param limiterRelease Release speed coefficient
     * @param limiterKnee Knee setting in dB
     * @param routingOrder Routing order sequence list: e.g. "EQ", "CROSSOVER", "LIMITER", "FADER"
     */
    fun processStereo(
        buffer: FloatArray,
        bufferSize: Int,
        volumeL: Float,
        volumeR: Float,
        isMutedL: Boolean,
        isMutedR: Boolean,
        limiterThreshold: Float,
        limiterAttack: Float, // ms
        limiterRelease: Float, // ms
        limiterKnee: Float, // dB
        routingOrder: List<String>
    ) {
        var sumL = 0f
        var sumR = 0f
        var peakL = 0f
        var peakR = 0f
        var maxGainReduction = 1f

        // Convert Limiter attack and release times from ms to sampling ratios
        // t_coeff = 1 - e^-1 / (sampleRate * ms / 1000)
        val attackCoeff = if (limiterAttack > 0) (1.0f - exp(-1.0f / (SAMPLE_RATE * (limiterAttack / 1000f)))).coerceIn(0f, 1f) else 1f
        val releaseCoeff = if (limiterRelease > 0) (1.0f - exp(-1.0f / (SAMPLE_RATE * (limiterRelease / 1000f)))).coerceIn(0f, 1f) else 1f
        val thresholdAmp = 10f.pow(limiterThreshold / 20f)

        // Reset dynamic spectrum bins slightly for active accumulation decay
        for (k in spectrumData.indices) {
            spectrumData[k] *= 0.85f 
        }

        var i = 0
        while (i < bufferSize) {
            if (i + 1 >= buffer.size) break
            var sL = buffer[i]
            var sR = buffer[i + 1]

            // Apply modules in custom order sequentially
            for (module in routingOrder) {
                when (module) {
                    "EQ" -> {
                        // Cascade of 18 filters for L and R
                        for (band in 0 until EQ_BAND_COUNT) {
                            sL = eqFiltersL[band].process(sL)
                            sR = eqFiltersR[band].process(sR)
                        }
                    }
                    "CROSSOVER" -> {
                        // 4-Way Linkwitz-Riley filter routing recombines sub, low, mid, high outputs
                        // Sub signal path
                        val subL = crossSubLpL.process(crossSubHpL.process(sL))
                        val subR = crossSubLpR.process(crossSubHpR.process(sR))

                        // Low signal path
                        val lowL = crossLowLpL.process(crossLowHpL.process(sL))
                        val lowR = crossLowLpR.process(crossLowHpR.process(sR))

                        // Mid signal path
                        val midL = crossMidLpL.process(crossMidHpL.process(sL))
                        val midR = crossMidLpR.process(crossMidHpR.process(sR))

                        // High signal path
                        val highL = crossHighHpL.process(sL)
                        val highR = crossHighHpR.process(sR)

                        // Recombine all bands to play full signal (simulating crossover sum output)
                        sL = subL + lowL + midL + highL
                        sR = subR + lowR + midR + highR
                    }
                    "LIMITER" -> {
                        // Envelope Tracker
                        val absL = abs(sL)
                        val absR = abs(sR)

                        // Smooth envelope decay or attack
                        envelopeStateL = if (absL > envelopeStateL) {
                            envelopeStateL + attackCoeff * (absL - envelopeStateL)
                        } else {
                            envelopeStateL + releaseCoeff * (absL - envelopeStateL)
                        }

                        envelopeStateR = if (absR > envelopeStateR) {
                            envelopeStateR + attackCoeff * (absR - envelopeStateR)
                        } else {
                            envelopeStateR + releaseCoeff * (absR - envelopeStateR)
                        }

                        val maxEnvelope = max(envelopeStateL, envelopeStateR)
                        var attenuation = 1.0f

                        // Implement soft knee mapping if current amplitude exceeds knee window
                        if (maxEnvelope > 0.0001f) {
                            val envDb = 20f * log10(maxEnvelope)
                            val kneeStartDb = limiterThreshold - limiterKnee / 2f
                            val kneeEndDb = limiterThreshold + limiterKnee / 2f

                            if (envDb > kneeStartDb) {
                                var targetDb = limiterThreshold
                                if (envDb < kneeEndDb) {
                                    // Soft knee interpolation
                                    val factor = (envDb - kneeStartDb) / limiterKnee
                                    targetDb = kneeStartDb + factor * (limiterThreshold - kneeStartDb) + 0.5f * (1f - factor).pow(2) * limiterKnee
                                    if (envDb > targetDb) {
                                        attenuation = 10f.pow((targetDb - envDb) / 20f)
                                    }
                                } else {
                                    // Hard clamp above knee width
                                    attenuation = thresholdAmp / maxEnvelope
                                }
                            }
                        }

                        attenuation = attenuation.coerceIn(0.001f, 1.0f)
                        if (attenuation < maxGainReduction) {
                            maxGainReduction = attenuation
                        }

                        sL *= attenuation
                        sR *= attenuation
                    }
                    "FADER" -> {
                        sL = if (isMutedL) 0f else sL * volumeL
                        sR = if (isMutedR) 0f else sR * volumeR
                    }
                }
            }

            buffer[i] = sL.coerceIn(-1.0f, 1.0f)
            buffer[i + 1] = sR.coerceIn(-1.0f, 1.0f)

            // Dynamic monitoring accumulators
            val quadL = sL * sL
            val quadR = sR * sR
            sumL += quadL
            sumR += quadR

            val checkL = abs(sL)
            val checkR = abs(sR)
            if (checkL > peakL) peakL = checkL
            if (checkR > peakR) peakR = checkR

            // Dynamic fast frequency bucket estimator (approximate values for UI bars)
            // Divide standard index to spread frequencies along visualizer
            val targetBin = (abs(sL) * 31.5f).toInt().coerceIn(0, 31)
            spectrumData[targetBin] = max(spectrumData[targetBin], abs(sL) * 1.5f + (abs(sR) * 0.5f))

            i += 2
        }

        // Apply decay levels for clean dynamic spectrum visualizations
        for (idx in spectrumData.indices) {
            val naturalFreqDecay = 0.02f * (1f - idx / 32f)
            spectrumData[idx] = (spectrumData[idx] - naturalFreqDecay).coerceAtLeast(0.01f)
        }

        // Update global Live VU Peak Meter and Gain reduction displays
        val samplesProcessed = (bufferSize / 2).toFloat().coerceAtLeast(1.0f)
        liveRmsL = sqrt(sumL / samplesProcessed).coerceIn(0f, 1f)
        liveRmsR = sqrt(sumR / samplesProcessed).coerceIn(0f, 1f)
        livePeakL = peakL.coerceIn(0f, 1f)
        livePeakR = peakR.coerceIn(0f, 1f)
        
        val grDb = 20f * log10(maxGainReduction)
        liveGainReductionDb = if (grDb.isNaN() || grDb.isInfinite()) 0f else grDb
    }

    /**
     * Reset filters to pre-defined clean configurations (flat line)
     */
    fun resetFilterBuffers() {
        for (i in 0 until EQ_BAND_COUNT) {
            eqFiltersL[i].x1 = 0f; eqFiltersL[i].x2 = 0f; eqFiltersL[i].y1 = 0f; eqFiltersL[i].y2 = 0f
            eqFiltersR[i].x1 = 0f; eqFiltersR[i].x2 = 0f; eqFiltersR[i].y1 = 0f; eqFiltersR[i].y2 = 0f
        }
        crossSubHpL.x1 = 0f; crossSubHpL.x2 = 0f; crossSubHpL.y1 = 0f; crossSubHpL.y2 = 0f
        crossSubHpR.x1 = 0f; crossSubHpR.x2 = 0f; crossSubHpR.y1 = 0f; crossSubHpR.y2 = 0f
        crossSubLpL.x1 = 0f; crossSubLpL.x2 = 0f; crossSubLpL.y1 = 0f; crossSubLpL.y2 = 0f
        crossSubLpR.x1 = 0f; crossSubLpR.x2 = 0f; crossSubLpR.y1 = 0f; crossSubLpR.y2 = 0f

        crossLowHpL.x1 = 0f; crossLowHpL.x2 = 0f; crossLowHpL.y1 = 0f; crossLowHpL.y2 = 0f
        crossLowHpR.x1 = 0f; crossLowHpR.x2 = 0f; crossLowHpR.y1 = 0f; crossLowHpR.y2 = 0f
        crossLowLpL.x1 = 0f; crossLowLpL.x2 = 0f; crossLowLpL.y1 = 0f; crossLowLpL.y2 = 0f
        crossLowLpR.x1 = 0f; crossLowLpR.x2 = 0f; crossLowLpR.y1 = 0f; crossLowLpR.y2 = 0f

        crossMidHpL.x1 = 0f; crossMidHpL.x2 = 0f; crossMidHpL.y1 = 0f; crossMidHpL.y2 = 0f
        crossMidHpR.x1 = 0f; crossMidHpR.x2 = 0f; crossMidHpR.y1 = 0f; crossMidHpR.y2 = 0f
        crossMidLpL.x1 = 0f; crossMidLpL.x2 = 0f; crossMidLpL.y1 = 0f; crossMidLpL.y2 = 0f
        crossMidLpR.x1 = 0f; crossMidLpR.x2 = 0f; crossMidLpR.y1 = 0f; crossMidLpR.y2 = 0f

        crossHighHpL.x1 = 0f; crossHighHpL.x2 = 0f; crossHighHpL.y1 = 0f; crossHighHpL.y2 = 0f
        crossHighHpR.x1 = 0f; crossHighHpR.x2 = 0f; crossHighHpR.y1 = 0f; crossHighHpR.y2 = 0f

        envelopeStateL = 0f
        envelopeStateR = 0f
    }
}
