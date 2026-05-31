package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dsp_settings")
data class DspSettings(
    @PrimaryKey val id: Int = 1,
    // Theme selection: Elegant Dark, Cyber Obsidian, Golden Amber, Atomic Lime, Ruby Crimson, Electric Cyan
    val themeName: String = "Elegant Dark",
    
    // 18 Bands of EQ stored as a comma-separated float string
    val eqSliderValues: String = "0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0",
    
    // Crossover cut frequencies inside LR Filter
    val crossoverSubLowHz: Float = 80f,
    val crossoverLowMidHz: Float = 300f,
    val crossoverMidHighHz: Float = 3000f,
    
    // Slope Types
    val crossoverSubSlope: String = "LR24",
    val crossoverLowSlope: String = "LR24",
    val crossoverMidSlope: String = "LR24",
    val crossoverHighSlope: String = "LR24",

    // Independent Stereo Master Volume and Mute
    val masterVolumeL: Float = 0.8f,
    val masterVolumeR: Float = 0.8f,
    val isMuteL: Boolean = false,
    val isMuteR: Boolean = false,

    // Crossover channel-specific fader columns matching professional boards (SUB, LOW, MID, HIGH)
    val crossoverVolSub: Float = 1.0f,
    val crossoverVolLow: Float = 1.0f,
    val crossoverVolMid: Float = 1.0f,
    val crossoverVolHigh: Float = 1.0f,

    val crossoverMuteSub: Boolean = false,
    val crossoverMuteLow: Boolean = false,
    val crossoverMuteMid: Boolean = false,
    val crossoverMuteHigh: Boolean = false,

    val crossoverSoloSub: Boolean = false,
    val crossoverSoloLow: Boolean = false,
    val crossoverSoloMid: Boolean = false,
    val crossoverSoloHigh: Boolean = false,

    val crossoverPhaseSub: Boolean = false,
    val crossoverPhaseLow: Boolean = false,
    val crossoverPhaseMid: Boolean = false,
    val crossoverPhaseHigh: Boolean = false,

    // Peak Limiter Controls
    val limiterAttackMs: Float = 5.0f,
    val limiterReleaseMs: Float = 100.0f,
    val limiterThresholdDb: Float = -2.0f,
    val limiterKneeDb: Float = 3.0f,

    // DSP Processing Routing Chain config list
    val dspRoutingOrder: String = "EQ,CROSSOVER,LIMITER,FADER"
) {
    // Utility functions to parse lists
    fun getEqList(): List<Float> {
        return eqSliderValues.split(",").map { it.toFloatOrNull() ?: 0.0f }
    }

    fun getRoutingList(): List<String> {
        return dspRoutingOrder.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
