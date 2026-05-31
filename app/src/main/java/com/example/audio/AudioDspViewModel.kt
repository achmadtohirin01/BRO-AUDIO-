package com.example.audio

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.db.DspDatabase
import com.example.db.DspRepository
import com.example.db.DspSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AudioDspViewModel(application: Application) : AndroidViewModel(application) {

    private val db = DspDatabase.getDatabase(application)
    private val repository = DspRepository(db.dspSettingsDao())

    // UI state sourced directly from local SQLite Room Database
    val dspSettings: StateFlow<DspSettings> = repository.dspSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DspSettings()
        )

    // Binds of live hardware measurements from background Audio Thread at 60fps
    val liveVUMeterL: StateFlow<Float> = AudioCaptureService.liveVUMeterL
    val liveVUMeterR: StateFlow<Float> = AudioCaptureService.liveVUMeterR
    val livePeakL: StateFlow<Float> = AudioCaptureService.livePeakL
    val livePeakR: StateFlow<Float> = AudioCaptureService.livePeakR
    val liveGainReduction: StateFlow<Float> = AudioCaptureService.liveGainReduction
    val liveSpectrum: StateFlow<FloatArray> = AudioCaptureService.liveSpectrum
    val isEngineRunning: StateFlow<Boolean> = AudioCaptureService.isEngineRunning
    val isCaptureModeActive: StateFlow<Boolean> = AudioCaptureService.isCaptureModeActive
    val audioStatusMessage: StateFlow<String> = AudioCaptureService.audioStatusMessage

    init {
        // Automatically push active DB settings into the service whenever they change
        viewModelScope.launch {
            dspSettings.collect { settings ->
                AudioCaptureService.instance?.updateSettings(settings)
            }
        }
        
        // Populate initial values if DB is empty
        viewModelScope.launch(Dispatchers.IO) {
            val current = repository.getSettingsDirect()
            if (current == DspSettings()) {
                repository.updateSettings(DspSettings())
            }
        }
    }

    fun setTheme(themeName: String) {
        updateDbSettings { it.copy(themeName = themeName) }
    }

    fun updateEqPreset(presetName: String) {
        val gains = when (presetName) {
            "Bass Boost" -> "6.0,5.5,4.5,3.0,1.5,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,2.0,3.5,4.5,5.0"
            "Vocal Boost" -> "-3.0,-2.5,-1.5,-0.5,0.5,1.5,2.0,3.0,4.0,4.5,4.0,3.5,3.0,2.0,1.0,0.0,-1.0,-2.0"
            "Treble Boost" -> "-4.0,-3.5,-3.0,-2.5,-2.0,-1.0,0.0,0.5,1.0,1.5,2.0,3.0,4.5,5.5,6.5,7.5,8.0,8.0"
            "Flat" -> "0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0"
            "Electronic" -> "5.0,4.0,2.0,0.0,-1.5,-2.5,-3.0,-2.0,-1.0,0.0,1.5,2.5,3.5,4.0,5.0,5.5,6.0,6.5"
            "Acoustic" -> "1.5,2.0,1.0,1.5,0.0,0.5,1.0,1.5,1.0,2.0,2.5,3.0,3.5,3.0,4.0,4.5,3.0,2.0"
            else -> "0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0"
        }
        updateDbSettings { it.copy(eqSliderValues = gains) }
    }

    fun updateEqBandValue(bandIndex: Int, valueDb: Float) {
        updateDbSettings { settings ->
            val bands = settings.getEqList().toMutableList()
            if (bandIndex in bands.indices) {
                bands[bandIndex] = valueDb
            }
            settings.copy(eqSliderValues = bands.joinToString(",") { String.format("%.1f", it) })
        }
    }

    fun updateCrossoverPoints(subLow: Float, lowMid: Float, midHigh: Float) {
        updateDbSettings { settings ->
            settings.copy(
                crossoverSubLowHz = subLow.coerceIn(40f, 250f),
                crossoverLowMidHz = lowMid.coerceIn(subLow + 20f, 1000f),
                crossoverMidHighHz = midHigh.coerceIn(lowMid + 200f, 12000f)
            )
        }
    }

    fun updateMasterVolumes(volL: Float, volR: Float) {
        updateDbSettings { settings ->
            settings.copy(
                masterVolumeL = volL.coerceIn(0f, 1.5f),
                masterVolumeR = volR.coerceIn(0f, 1.5f)
            )
        }
    }

    fun toggleMute(leftChannel: Boolean) {
        updateDbSettings { settings ->
            if (leftChannel) {
                settings.copy(isMuteL = !settings.isMuteL)
            } else {
                settings.copy(isMuteR = !settings.isMuteR)
            }
        }
    }

    fun updateLimiterParams(attackMs: Float, releaseMs: Float, thresholdDb: Float, kneeDb: Float) {
        updateDbSettings { settings ->
            settings.copy(
                limiterAttackMs = attackMs.coerceIn(0.1f, 50f),
                limiterReleaseMs = releaseMs.coerceIn(10f, 1000f),
                limiterThresholdDb = thresholdDb.coerceIn(-40f, 0f),
                limiterKneeDb = kneeDb.coerceIn(0f, 12f)
            )
        }
    }

    fun updateRoutingOrder(newOrder: List<String>) {
        updateDbSettings { settings ->
            settings.copy(dspRoutingOrder = newOrder.joinToString(","))
        }
    }

    fun resetAll() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.resetSettings()
        }
    }

    /**
     * Start/Stop commands routing to foreground service
     */
    fun toggleAudioPlayback(
        context: Context,
        mpManager: MediaProjectionManager? = null,
        resultCode: Int = 0,
        resultData: Intent? = null
    ) {
        val serviceClass = AudioCaptureService::class.java
        val isRunningNow = isEngineRunning.value

        if (!isRunningNow) {
            // Spawn foreground service to register projection states
            val intent = Intent(context, serviceClass)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }

            // Fire engine audio loop launch
            viewModelScope.launch {
                // Yield thread to assure binding registers
                kotlinx.coroutines.delay(100)
                AudioCaptureService.instance?.startEngine(mpManager, resultCode, resultData)
            }
        } else {
            AudioCaptureService.instance?.stopEngine()
            context.stopService(Intent(context, serviceClass))
        }
    }

    private fun updateDbSettings(modifier: (DspSettings) -> DspSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = repository.getSettingsDirect()
            repository.updateSettings(modifier(current))
        }
    }
}
