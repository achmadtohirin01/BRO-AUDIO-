package com.example.audio

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.db.DspSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*
import kotlin.random.Random

class AudioCaptureService : Service() {

    companion object {
        private const val TAG = "AudioCaptureService"
        private const val NOTIFICATION_ID = 1337
        private const val CHANNEL_ID = "bro_audio_dsp_channel"
        
        // Singleton reference to bind state to UI
        @Volatile
        var instance: AudioCaptureService? = null
            private set

        // Shared flows to broadcast real-time audio statistics to Jetpack Compose at 60fps
        val liveVUMeterL = MutableStateFlow(0f)
        val liveVUMeterR = MutableStateFlow(0f)
        val livePeakL = MutableStateFlow(0f)
        val livePeakR = MutableStateFlow(0f)
        val liveGainReduction = MutableStateFlow(0f)
        val liveSpectrum = MutableStateFlow(FloatArray(32) { 0f })
        val isEngineRunning = MutableStateFlow(false)
        val isCaptureModeActive = MutableStateFlow(false)
        val audioStatusMessage = MutableStateFlow("Engine Offline")
    }

    private val binder = LocalBinder()
    val dspProcessor = DspProcessor()

    // Active DSP configuration coefficients
    @Volatile
    private var activeSettings = DspSettings()

    // Threads and state controls
    private var audioThread: Thread? = null
    private val isRunning = AtomicBoolean(false)
    
    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    inner class LocalBinder : Binder() {
        fun getService(): AudioCaptureService = this@AudioCaptureService
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Keeps service alive as foreground
        startForeground(NOTIFICATION_ID, createNotification("BRO AUDIO Engine Ready"))
        return START_STICKY
    }

    fun updateSettings(settings: DspSettings) {
        activeSettings = settings
        // Update DSP EQ coefficients directly
        dspProcessor.updateEqBands(settings.getEqList().toFloatArray())
        // Update crossover boundaries
        dspProcessor.updateCrossover(
            settings.crossoverSubLowHz,
            settings.crossoverLowMidHz,
            settings.crossoverMidHighHz
        )
    }

    /**
     * Start the DSP audio processing loop.
     * Uses media projection for global capture if provided, otherwise cascades into high-fidelity beat generator.
     */
    fun startEngine(mpManager: MediaProjectionManager? = null, projectionResultCode: Int = 0, projectionData: Intent? = null) {
        if (isRunning.get()) return

        // Dynamic status check
        val captureRequested = projectionData != null && mpManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        
        isRunning.set(true)
        isEngineRunning.value = true
        isCaptureModeActive.value = captureRequested
        dspProcessor.resetFilterBuffers()

        audioThread = Thread {
            runAudioLoop(captureRequested, mpManager, projectionResultCode, projectionData)
        }.apply {
            priority = Thread.MAX_PRIORITY // Secure real-time context
            start()
        }

        val statusMsg = if (captureRequested) "Capturing System Audio" else "Synthesizer Beat Offline"
        audioStatusMessage.value = statusMsg
        updateNotification(statusMsg)
    }

    /**
     * Terminate thread loops and discharge output tracks
     */
    fun stopEngine() {
        isRunning.set(false)
        isEngineRunning.value = false
        isCaptureModeActive.value = false
        audioStatusMessage.value = "Engine Stopped"

        try {
            audioThread?.join(500)
        } catch (e: Exception) {
            Log.e(TAG, "Failed joining audio thread: ${e.message}")
        }
        audioThread = null
        
        releaseAudioResources()
        updateNotification("BRO AUDIO Engine Offline")
    }

    private fun releaseAudioResources() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null
    }

    /**
     * Primary Low-Latency Audio Loop
     * Cascades either recorded PCM data or synthesizes a stereo beat internally to feed the DSP filter chain.
     */
    private fun runAudioLoop(
        captureMode: Boolean,
        mpManager: MediaProjectionManager?,
        projectionResultCode: Int,
        projectionData: Intent?
    ) {
        val sampleRate = DspProcessor.SAMPLE_RATE
        val channelConfigOut = AudioFormat.CHANNEL_OUT_STEREO
        val audioFormat = AudioFormat.ENCODING_PCM_FLOAT

        // Configure min buffer sizes for ultra-low latency (< 15ms)
        val minBufSizeOut = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioFormat)
        val bufferFrames = 512 // small block size to secure low latency
        val bufferSizeFloats = bufferFrames * 2 // stereo channels
        val scratchBuffer = FloatArray(bufferSizeFloats)

        // Initialize Audio Track for Master Speakers playback
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val audioFormatSpec = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfigOut)
                .setEncoding(audioFormat)
                .build()

            audioTrack = AudioTrack(
                audioAttributes,
                audioFormatSpec,
                minBufSizeOut.coerceAtLeast(bufferSizeFloats * 4),
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            ).apply {
                play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating AudioTrack: ${e.message}")
            audioStatusMessage.value = "Audio Hardware Error"
            isRunning.set(false)
            isEngineRunning.value = false
            return
        }

        // Setup System Capture via AudioPlaybackCapture API if enabled
        if (captureMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mpManager != null && projectionData != null) {
            try {
                mediaProjection = mpManager.getMediaProjection(projectionResultCode, projectionData)
                
                val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()

                val captureFormat = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build()

                val minCaptureBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
                
                audioRecord = AudioRecord.Builder()
                    .setAudioFormat(captureFormat)
                    .setBufferSizeInBytes(minCaptureBytes.coerceAtLeast(bufferSizeFloats * 4))
                    .setAudioPlaybackCaptureConfig(config)
                    .build()

                audioRecord?.startRecording()
                audioStatusMessage.value = "DSP: Captured Live System Audio"
            } catch (e: Exception) {
                Log.e(TAG, "AudioPlaybackCapture config failed. Falling back to synth beat.", e)
                audioStatusMessage.value = "Capture Error! Playing Synth Beat"
                isCaptureModeActive.value = false
                // Release problematic capture attempts
                try { audioRecord?.release() } catch (_: Exception) {}
                audioRecord = null
            }
        }

        // Beats generator tracking variables (for synthetic backing music)
        var sampleIndex = 0L
        val bpm = 125f
        val beatIntervalSamples = (60f / bpm * sampleRate).toLong()

        // Continuous streaming loop
        while (isRunning.get()) {
            val record = audioRecord
            val track = audioTrack ?: break

            if (record != null && isCaptureModeActive.value) {
                // Read from real-time system audio input stream
                val readSamples = record.read(scratchBuffer, 0, bufferSizeFloats, AudioRecord.READ_BLOCKING)
                if (readSamples <= 0) continue
            } else {
                // Synthesizer Drum & Synth back-beat loop (generates dynamic full-range sound)
                for (j in 0 until bufferSizeFloats step 2) {
                    val t = sampleIndex + (j / 2)
                    
                    // Beat cycle metrics
                    val beatSampleOffset = t % beatIntervalSamples
                    val subBeatInterval = beatIntervalSamples / 4 // 16th note subdivision
                    val subBeatOffset = t % subBeatInterval

                    // 1. Kick Drum (Sub bass sweep from 150hz down to 43hz, heavy impact)
                    val kickTrigger = (t % beatIntervalSamples) == 0L || (t % beatIntervalSamples == beatIntervalSamples / 2 && Random.nextFloat() > 0.6f)
                    val kickAgeSec = (t % beatIntervalSamples).toFloat() / sampleRate
                    var kick = 0f
                    if (kickAgeSec < 0.35f) {
                        val freqSweep = 45f + 120f * exp(-45f * kickAgeSec)
                        val angle = 2f * PI.toFloat() * freqSweep * kickAgeSec
                        kick = sin(angle) * exp(-8.5f * kickAgeSec) * 0.9f
                    }

                    // 2. Snare drum (Pulsing pinkish noise at off-beats)
                    val snareAgeSec = ((t + beatIntervalSamples / 2) % beatIntervalSamples).toFloat() / sampleRate
                    var snare = 0f
                    if (snareAgeSec < 0.18f) {
                        val noise = Random.nextFloat() * 2f - 1f
                        snare = noise * exp(-15f * snareAgeSec) * 0.45f
                    }

                    // 3. Hi-Hat ticking (16th notes fast sizzles)
                    val hatAgeSec = subBeatOffset.toFloat() / sampleRate
                    var hat = 0f
                    if (hatAgeSec < 0.03f) {
                        val highPassNoise = (Random.nextFloat() * 2f - 1f) * (if (subBeatOffset % 2 == 0L) 1f else -1f)
                        hat = highPassNoise * exp(-85f * hatAgeSec) * 0.12f
                    }

                    // 4. Synth Melodic lead arpeggiator (warm saw waves passing through chord grid)
                    val barSamples = beatIntervalSamples * 4
                    val chords = arrayOf(
                        floatArrayOf(55f, 110f, 165f, 220f), // A minor chords
                        floatArrayOf(65.4f, 130.8f, 196.2f, 261.6f), // C major
                        floatArrayOf(58.2f, 116.5f, 174.6f, 233f), // G major
                        floatArrayOf(48.9f, 97.9f, 146.9f, 195.9f)  // F major
                    )
                    val currentChordIdx = ((t / barSamples) % chords.size).toInt()
                    val bassNotes = chords[currentChordIdx]
                    
                    val noteIndex = ((t / (beatIntervalSamples / 2)) % 4).toInt()
                    val harmonicFreq = bassNotes[noteIndex]
                    
                    val angleSynth = 2f * PI.toFloat() * harmonicFreq * (t.toFloat() / sampleRate)
                    // Generate square synth wave combined with saw
                    val synthVal = (sin(angleSynth).sign * 0.4f + (angleSynth % (2f * PI.toFloat())) / PI.toFloat() - 1f) * 0.15f
                    val synthEnvelope = exp(-1.5f * (subBeatOffset.toFloat() / sampleRate))
                    val melody = synthVal * synthEnvelope

                    val mixedMono = kick + snare + hat + melody
                    
                    // Interleaved L & R signal distribution
                    scratchBuffer[j] = mixedMono
                    scratchBuffer[j + 1] = mixedMono
                }
                sampleIndex += bufferFrames
            }

            // Apply DB Routing and Effects in real-time
            val sets = activeSettings
            dspProcessor.processStereo(
                buffer = scratchBuffer,
                bufferSize = bufferSizeFloats,
                volumeL = sets.masterVolumeL,
                volumeR = sets.masterVolumeR,
                isMutedL = sets.isMuteL,
                isMutedR = sets.isMuteR,
                limiterThreshold = sets.limiterThresholdDb,
                limiterAttack = sets.limiterAttackMs,
                limiterRelease = sets.limiterReleaseMs,
                limiterKnee = sets.limiterKneeDb,
                routingOrder = sets.getRoutingList()
            )

            // Playback stream on output audiotrack
            track.write(scratchBuffer, 0, bufferSizeFloats, AudioTrack.WRITE_BLOCKING)

            // Push stats values asynchronously to StateFlow at interval ticks
            if (sampleIndex % 512 == 0L || isCaptureModeActive.value) {
                liveVUMeterL.value = dspProcessor.liveRmsL
                liveVUMeterR.value = dspProcessor.liveRmsR
                livePeakL.value = dspProcessor.livePeakL
                livePeakR.value = dspProcessor.livePeakR
                liveGainReduction.value = dspProcessor.liveGainReductionDb
                liveSpectrum.value = dspProcessor.spectrumData.clone()
            }
        }

        // Cleanup resources
        releaseAudioResources()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "BRO AUDIO DSP Engine Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        val pendingIntent = Intent(this, Class.forName("com.example.MainActivity")).let { notificationIntent ->
            PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BRO AUDIO Processing Engine")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_headset)
            .setContentIntent(pendingIntent)
            .setTicker("BRO AUDIO Engine Started")
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(content))
    }

    override fun onDestroy() {
        stopEngine()
        instance = null
        super.onDestroy()
    }
}
