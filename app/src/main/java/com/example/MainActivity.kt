package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audio.AudioDspViewModel
import com.example.audio.DspProcessor
import com.example.db.DspSettings
import com.example.ui.theme.DspThemeSelector
import com.example.ui.theme.MyApplicationTheme
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private val viewModel: AudioDspViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val dspSettings by viewModel.dspSettings.collectAsState()
            val theme = DspThemeSelector.getTheme(dspSettings.themeName)

            MyApplicationTheme(dspTheme = theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BroAudioControlCenter(viewModel = viewModel, dspSettings = dspSettings)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BroAudioControlCenter(
    viewModel: AudioDspViewModel,
    dspSettings: DspSettings
) {
    val context = LocalContext.current
    val currentThemeName = dspSettings.themeName
    val activeTheme = DspThemeSelector.getTheme(currentThemeName)

    // Collect flow states reactively from the processor loop
    val liveVUL by viewModel.liveVUMeterL.collectAsState()
    val liveVUR by viewModel.liveVUMeterR.collectAsState()
    val livePeakL by viewModel.livePeakL.collectAsState()
    val livePeakR by viewModel.livePeakR.collectAsState()
    val liveGR by viewModel.liveGainReduction.collectAsState()
    val liveSpec by viewModel.liveSpectrum.collectAsState()
    val isRunning by viewModel.isEngineRunning.collectAsState()
    val isCaptured by viewModel.isCaptureModeActive.collectAsState()
    val statusMsg by viewModel.audioStatusMessage.collectAsState()

    var activeTab by remember { mutableStateOf("EQUALIZER") }

    // Media projection launcher setup for live Android loop capturing
    val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    
    val captureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null && mpManager != null) {
            viewModel.toggleAudioPlayback(context, mpManager, result.resultCode, result.data)
        } else {
            Toast.makeText(context, "System audio stream fallback: Synthesizer Active", Toast.LENGTH_SHORT).show()
            viewModel.toggleAudioPlayback(context)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mpManager != null) {
                try {
                    captureLauncher.launch(mpManager.createScreenCaptureIntent())
                } catch (e: Exception) {
                    viewModel.toggleAudioPlayback(context)
                }
            } else {
                viewModel.toggleAudioPlayback(context)
            }
        } else {
            Toast.makeText(context, "Permission denied. Outputting micro beats.", Toast.LENGTH_SHORT).show()
            viewModel.toggleAudioPlayback(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- Header Panel with Premium Typography and Theme Selectors ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "BRO AUDIO",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                letterSpacing = 2.sp,
                                shadow = Shadow(
                                    color = activeTheme.primaryAccent,
                                    blurRadius = 12f
                                )
                            ),
                            color = activeTheme.primaryAccent
                        )
                        Text(
                            text = "SYSTEM AUDIO CAPTURE & AUDIO CROSSOVER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Theme selector triggers inside header
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DspThemeSelector.themes.forEach { themeOpt ->
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(themeOpt.primaryAccent)
                                    .border(
                                        width = if (currentThemeName == themeOpt.themeName) 2.dp else 0.dp,
                                        color = if (currentThemeName == themeOpt.themeName) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.setTheme(themeOpt.themeName) }
                            )
                        }
                    }
                }

                // Divider line highlighting selected theme accents
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(activeTheme.primaryAccent, activeTheme.secondaryAccent)
                            )
                        )
                )

                // Live system states and engine launcher button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) activeTheme.primaryAccent else Color.Gray)
                        )
                        Text(
                            text = statusMsg,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isRunning) activeTheme.primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            if (!isRunning) {
                                // Request dynamic recording permission is API level >= 29
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mpManager != null) {
                                        try {
                                            captureLauncher.launch(mpManager.createScreenCaptureIntent())
                                        } catch (e: Exception) {
                                            viewModel.toggleAudioPlayback(context)
                                        }
                                    } else {
                                        viewModel.toggleAudioPlayback(context)
                                    }
                                }
                            } else {
                                viewModel.toggleAudioPlayback(context)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) Color(0xFFC33D3D) else activeTheme.primaryAccent,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.testTag("engine_toggle_btn"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.PowerSettingsNew else Icons.Default.PlayArrow,
                            contentDescription = "Power Trigger",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isRunning) "OFFLINE" else "ONLINE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // --- Real-time Visualizer Panel: VU meters and Canvas spectrum ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "REAL-TIME SPECTRUM & VU MONITOR (<15ms Latency)",
                    style = MaterialTheme.typography.labelSmall,
                    color = activeTheme.primaryAccent,
                    fontFamily = FontFamily.Monospace
                )

                // Stereo VU Meter Bars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("CH L", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${String.format("%.1f", 20f * log10(liveVUL.coerceAtLeast(0.0001f)))} dB", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = activeTheme.primaryAccent)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(activeTheme.trackBackground)
                        ) {
                            // VU Level
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = liveVUL.coerceIn(0f, 1f))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(activeTheme.primaryAccent, activeTheme.secondaryAccent)
                                        )
                                    )
                            )
                            // Peak dot
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(3.dp)
                                    .align(Alignment.CenterStart)
                                    .absoluteOffset(x = (livePeakL * 240).dp) // visual approximation mapping
                                    .background(Color.White)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("CH R", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${String.format("%.1f", 20f * log10(liveVUR.coerceAtLeast(0.0001f)))} dB", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = activeTheme.primaryAccent)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(activeTheme.trackBackground)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = liveVUR.coerceIn(0f, 1f))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(activeTheme.primaryAccent, activeTheme.secondaryAccent)
                                        )
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(3.dp)
                                    .align(Alignment.CenterStart)
                                    .absoluteOffset(x = (livePeakR * 240).dp)
                                    .background(Color.White)
                            )
                        }
                    }
                }

                // Dynamic Canvas FFT Spectrum visualizer
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(85.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF07080A))
                        .border(1.dp, Color(0xFF1E2129), RoundedCornerShape(8.dp))
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    
                    val barSpacing = 4f
                    val barCount = 32
                    val itemWidth = (canvasWidth - (barSpacing * (barCount - 1))) / barCount

                    // Draw center timeline grid
                    drawLine(
                        color = Color(0xFF1B1E26),
                        start = Offset(0f, canvasHeight / 2f),
                        end = Offset(canvasWidth, canvasHeight / 2f),
                        strokeWidth = 1f
                    )

                    // Wave form bar loops
                    for (index in 0 until barCount) {
                        val mag = if (index < liveSpec.size) liveSpec[index] else 0.05f
                        val scaledMagnitude = (mag * canvasHeight * 0.9f).coerceIn(4f, canvasHeight * 0.95f)
                        val x = index * (itemWidth + barSpacing)
                        
                        // Mirrored spectrum bars from vertical center
                        val y = (canvasHeight - scaledMagnitude) / 2f

                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(activeTheme.primaryAccent, activeTheme.secondaryAccent)
                            ),
                            topLeft = Offset(x, y),
                            size = Size(itemWidth, scaledMagnitude),
                            cornerRadius = CornerRadius(2f, 2f),
                            alpha = 0.85f
                        )
                    }
                }

                if (liveGR < -0.1f) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compress,
                            contentDescription = "Limiter Status",
                            tint = activeTheme.primaryAccent,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LIMITER REDUCTION: ${String.format("%.1f", liveGR)} dB",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = activeTheme.primaryAccent
                        )
                    }
                }
            }
        }

        // --- Custom Menu Tabs representing internal panels ---
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val menuTabs = listOf("EQUALIZER", "CROSSOVER", "MIXER / FADER", "PEAK LIMITER", "CUSTOM ROUTING", "SYSTEM CAPTURE")
            itemsIndexed(menuTabs) { _, tab ->
                val isSelected = activeTab == tab
                Button(
                    onClick = { activeTab = tab },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) activeTheme.primaryAccent else MaterialTheme.colorScheme.surface,
                        contentColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("tab_${tab.replace(" ", "_")}")
                ) {
                    Text(
                        text = tab,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // --- Active Workspace Container with smooth transitions ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            when (activeTab) {
                "EQUALIZER" -> EqualizerPanel(viewModel, dspSettings, activeTheme)
                "CROSSOVER" -> CrossoverPanel(viewModel, dspSettings, activeTheme)
                "MIXER / FADER" -> MixerPanel(viewModel, dspSettings, activeTheme)
                "PEAK LIMITER" -> LimiterPanel(viewModel, dspSettings, activeTheme)
                "CUSTOM ROUTING" -> CustomRoutingPanel(viewModel, dspSettings, activeTheme)
                "SYSTEM CAPTURE" -> SystemCapturePanel(viewModel, dspSettings, activeTheme)
            }
        }
    }
}

/**
 * 18-Band Bi-Quad Peaking Equalizer dashboard panel
 */
@Composable
fun EqualizerPanel(
    viewModel: AudioDspViewModel,
    settings: DspSettings,
    theme: com.example.ui.theme.DspTheme
) {
    val currentBands = settings.getEqList()
    val presets = listOf("Flat", "Bass Boost", "Vocal Boost", "Treble Boost", "Electronic", "Acoustic")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "18-BAND GRAPHIC EQUALIZER",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = theme.primaryAccent
                )
                
                IconButton(
                    onClick = { viewModel.updateEqPreset("Flat") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset EQ",
                        tint = theme.primaryAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Quick ISO preset loaders
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(presets) { _, preset ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(theme.trackBackground)
                            .clickable { viewModel.updateEqPreset(preset) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = preset.uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Scrollable 18-Band Slider Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DspProcessor.EQ_FREQUENCIES.forEachIndexed { index, freq ->
                    val gainVal = if (index < currentBands.size) currentBands[index] else 0.0f
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(36.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (gainVal >= 0) "+${String.format("%.1f", gainVal)}" else String.format("%.1f", gainVal),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = theme.primaryAccent,
                            fontWeight = FontWeight.Bold
                        )

                        // Vertical slider component
                        Box(
                            modifier = Modifier
                                .height(160.dp)
                                .width(31.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Slider(
                                value = gainVal,
                                onValueChange = { viewModel.updateEqBandValue(index, it) },
                                valueRange = -12f..12f,
                                colors = SliderDefaults.colors(
                                    thumbColor = theme.primaryAccent,
                                    activeTrackColor = theme.primaryAccent,
                                    inactiveTrackColor = theme.trackBackground
                                ),
                                modifier = Modifier
                                    .graphicsLayer {
                                        rotationZ = -90f
                                    }
                                    .width(160.dp)
                                    .testTag("eq_slider_${index}")
                            )
                        }

                        Text(
                            text = if (freq >= 1000) "${(freq / 1000).toInt()}k" else "${freq.toInt()}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * 4-Way Audio Crossover panel with slope details and Canvas responses
 */
@Composable
fun CrossoverPanel(
    viewModel: AudioDspViewModel,
    settings: DspSettings,
    theme: com.example.ui.theme.DspTheme
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "4-WAY LINKWITZ-RILEY CROSSOVER",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = theme.primaryAccent
            )

            // Crossover Response Graph
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF090A0D))
                    .border(1.dp, Color(0xFF1E2129), RoundedCornerShape(6.dp))
            ) {
                val w = size.width
                val h = size.height

                // Grid scale bars for Sub, Low, Mid, High
                val pointSub = (settings.crossoverSubLowHz / 250f) * (w * 0.25f)
                val pointLow = (settings.crossoverLowMidHz / 1000f) * (w * 0.5f)
                val pointMid = (settings.crossoverMidHighHz / 12000f) * (w * 0.85f)

                // Sub-band curve (Red-ish or primary)
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, h * 0.15f)
                        val cpX = pointSub * 1.5f
                        cubicTo(pointSub * 0.8f, h * 0.15f, cpX, h * 0.95f, w * 0.35f, h * 0.95f)
                    },
                    color = theme.primaryAccent,
                    style = Stroke(width = 3f)
                )

                // Low-band curve (Yellow / Cyan)
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(pointSub * 0.3f, h * 0.95f)
                        cubicTo(pointSub, h * 0.2f, pointLow * 0.8f, h * 0.2f, pointLow * 1.3f, h * 0.95f)
                    },
                    color = theme.secondaryAccent,
                    style = Stroke(width = 3f)
                )

                // Mid-band curve
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(pointLow * 0.7f, h * 0.95f)
                        cubicTo(pointLow * 1.1f, h * 0.25f, pointMid * 0.9f, h * 0.25f, pointMid * 1.2f, h * 0.95f)
                    },
                    color = Color.Green,
                    style = Stroke(width = 2f)
                )

                // High-band curve
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(pointMid * 0.8f, h * 0.95f)
                        cubicTo(pointMid * 1.05f, h * 0.35f, w * 0.9f, h * 0.15f, w, h * 0.15f)
                    },
                    color = Color.Magenta,
                    style = Stroke(width = 3f)
                )

                // Vertical markers of cutoff points
                drawLine(Color(0xFF323645), Offset(pointSub, 0f), Offset(pointSub, h), strokeWidth = 2f)
                drawLine(Color(0xFF323645), Offset(pointLow, 0f), Offset(pointLow, h), strokeWidth = 2f)
                drawLine(Color(0xFF323645), Offset(pointMid, 0f), Offset(pointMid, h), strokeWidth = 2f)
            }

            // cutoff adjustment sliders
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Sub-Low Cutoff (40Hz to 250Hz)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("SUB-LOW (CROSS):", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                        Text("${settings.crossoverSubLowHz.toInt()} Hz", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = settings.crossoverSubLowHz,
                        onValueChange = { viewModel.updateCrossoverPoints(it, settings.crossoverLowMidHz, settings.crossoverMidHighHz) },
                        valueRange = 40f..250f,
                        colors = SliderDefaults.colors(thumbColor = theme.primaryAccent, activeTrackColor = theme.primaryAccent, inactiveTrackColor = theme.trackBackground),
                        modifier = Modifier.testTag("slider_sub_low")
                    )
                }

                // Low-Mid Cutoff (250Hz to 1000Hz)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("LOW-MID (CROSS):", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                        Text("${settings.crossoverLowMidHz.toInt()} Hz", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = settings.crossoverLowMidHz,
                        onValueChange = { viewModel.updateCrossoverPoints(settings.crossoverSubLowHz, it, settings.crossoverMidHighHz) },
                        valueRange = 250f..1000f,
                        colors = SliderDefaults.colors(thumbColor = theme.primaryAccent, activeTrackColor = theme.primaryAccent, inactiveTrackColor = theme.trackBackground),
                        modifier = Modifier.testTag("slider_low_mid")
                    )
                }

                // Mid-High Cutoff (1000Hz to 12000Hz)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("MID-HIGH (CROSS):", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                        Text("${settings.crossoverMidHighHz.toInt()} Hz", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = settings.crossoverMidHighHz,
                        onValueChange = { viewModel.updateCrossoverPoints(settings.crossoverSubLowHz, settings.crossoverLowMidHz, it) },
                        valueRange = 1000f..12000f,
                        colors = SliderDefaults.colors(thumbColor = theme.primaryAccent, activeTrackColor = theme.primaryAccent, inactiveTrackColor = theme.trackBackground),
                        modifier = Modifier.testTag("slider_mid_high")
                    )
                }
            }
        }
    }
}

/**
 * Dual Master Mixer Fader panel (left & right independent)
 */
@Composable
fun MixerPanel(
    viewModel: AudioDspViewModel,
    settings: DspSettings,
    theme: com.example.ui.theme.DspTheme
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "DUAL MASTER MIXER FADER",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = theme.primaryAccent
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Left channel control column
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "LEFT VOLUME",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "${(settings.masterVolumeL * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (settings.isMuteL) Color.Red else theme.primaryAccent
                    )

                    Box(
                        modifier = Modifier
                            .height(180.dp)
                            .width(50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Slider(
                            value = settings.masterVolumeL,
                            onValueChange = { viewModel.updateMasterVolumes(it, settings.masterVolumeR) },
                            valueRange = 0f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = if (settings.isMuteL) Color.Red else theme.primaryAccent,
                                activeTrackColor = theme.primaryAccent,
                                inactiveTrackColor = theme.trackBackground
                            ),
                            modifier = Modifier
                                .graphicsLayer { rotationZ = -90f }
                                .width(180.dp)
                                .testTag("slider_volume_l")
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleMute(leftChannel = true) },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (settings.isMuteL) Color(0xFF5A1C1F) else theme.trackBackground)
                    ) {
                        Icon(
                            imageVector = if (settings.isMuteL) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Mute L",
                            tint = if (settings.isMuteL) Color.Red else theme.primaryAccent
                        )
                    }
                }

                // Right channel control column
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "RIGHT VOLUME",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "${(settings.masterVolumeR * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (settings.isMuteR) Color.Red else theme.primaryAccent
                    )

                    Box(
                        modifier = Modifier
                            .height(180.dp)
                            .width(50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Slider(
                            value = settings.masterVolumeR,
                            onValueChange = { viewModel.updateMasterVolumes(settings.masterVolumeL, it) },
                            valueRange = 0f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = if (settings.isMuteR) Color.Red else theme.primaryAccent,
                                activeTrackColor = theme.primaryAccent,
                                inactiveTrackColor = theme.trackBackground
                            ),
                            modifier = Modifier
                                .graphicsLayer { rotationZ = -90f }
                                .width(180.dp)
                                .testTag("slider_volume_r")
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleMute(leftChannel = false) },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (settings.isMuteR) Color(0xFF5A1C1F) else theme.trackBackground)
                    ) {
                        Icon(
                            imageVector = if (settings.isMuteR) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Mute R",
                            tint = if (settings.isMuteR) Color.Red else theme.primaryAccent
                        )
                    }
                }
            }
        }
    }
}

/**
 * Peak Limiter configuration panel
 */
@Composable
fun LimiterPanel(
    viewModel: AudioDspViewModel,
    settings: DspSettings,
    theme: com.example.ui.theme.DspTheme
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "PEAK LIMITER & ENVELOPE CONTROLS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = theme.primaryAccent
            )

            // Threshold (dB Slider)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("THRESHOLD (LIMIT):", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    Text("${String.format("%.1f", settings.limiterThresholdDb)} dB", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = settings.limiterThresholdDb,
                    onValueChange = { viewModel.updateLimiterParams(settings.limiterAttackMs, settings.limiterReleaseMs, it, settings.limiterKneeDb) },
                    valueRange = -30f..0f,
                    colors = SliderDefaults.colors(thumbColor = theme.primaryAccent, activeTrackColor = theme.primaryAccent, inactiveTrackColor = theme.trackBackground),
                    modifier = Modifier.testTag("slider_threshold")
                )
            }

            // Attack (ms Slider)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ATTACK TIME:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    Text("${String.format("%.1f", settings.limiterAttackMs)} ms", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = settings.limiterAttackMs,
                    onValueChange = { viewModel.updateLimiterParams(it, settings.limiterReleaseMs, settings.limiterThresholdDb, settings.limiterKneeDb) },
                    valueRange = 0.1f..25f,
                    colors = SliderDefaults.colors(thumbColor = theme.primaryAccent, activeTrackColor = theme.primaryAccent, inactiveTrackColor = theme.trackBackground),
                    modifier = Modifier.testTag("slider_attack")
                )
            }

            // Release (ms Slider)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("RELEASE TIME:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    Text("${settings.limiterReleaseMs.toInt()} ms", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = settings.limiterReleaseMs,
                    onValueChange = { viewModel.updateLimiterParams(settings.limiterAttackMs, it, settings.limiterThresholdDb, settings.limiterKneeDb) },
                    valueRange = 10f..500f,
                    colors = SliderDefaults.colors(thumbColor = theme.primaryAccent, activeTrackColor = theme.primaryAccent, inactiveTrackColor = theme.trackBackground),
                    modifier = Modifier.testTag("slider_release")
                )
            }

            // Soft Knee (dB Slider)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("SOFT KNEE WIDTH:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    Text("${String.format("%.1f", settings.limiterKneeDb)} dB", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = settings.limiterKneeDb,
                    onValueChange = { viewModel.updateLimiterParams(settings.limiterAttackMs, settings.limiterReleaseMs, settings.limiterThresholdDb, it) },
                    valueRange = 0f..8f,
                    colors = SliderDefaults.colors(thumbColor = theme.primaryAccent, activeTrackColor = theme.primaryAccent, inactiveTrackColor = theme.trackBackground),
                    modifier = Modifier.testTag("slider_knee")
                )
            }
        }
    }
}

/**
 * Custom Installation Routing Order panel (drag/click configuration)
 */
@Composable
fun CustomRoutingPanel(
    viewModel: AudioDspViewModel,
    settings: DspSettings,
    theme: com.example.ui.theme.DspTheme
) {
    val routingList = settings.getRoutingList().toMutableList()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "CUSTOM DSP INSTALLATION ROUTING",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = theme.primaryAccent
            )
            
            Text(
                text = "Customize the processing sequence of effects. Tap arrows to rearrange the signal chain order dynamically in real-time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Vertical list of effects order
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                routingList.forEachIndexed { idx, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.trackBackground)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(theme.primaryAccent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Text(
                                text = when (item) {
                                    "EQ" -> "18-BAND EQUALIZER"
                                    "CROSSOVER" -> "4-WAY LR CROSSOVER"
                                    "LIMITER" -> "PEAK LIMITER & COMP"
                                    "FADER" -> "MASTER VOLUME & MUTE"
                                    else -> item
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Order Shifter buttons
                        Row {
                            IconButton(
                                onClick = {
                                    if (idx > 0) {
                                        val temp = routingList[idx]
                                        routingList[idx] = routingList[idx - 1]
                                        routingList[idx - 1] = temp
                                        viewModel.updateRoutingOrder(routingList)
                                    }
                                },
                                enabled = idx > 0,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Move Up",
                                    tint = if (idx > 0) theme.primaryAccent else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (idx < routingList.lastIndex) {
                                        val temp = routingList[idx]
                                        routingList[idx] = routingList[idx + 1]
                                        routingList[idx + 1] = temp
                                        viewModel.updateRoutingOrder(routingList)
                                    }
                                },
                                enabled = idx < routingList.lastIndex,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Move Down",
                                    tint = if (idx < routingList.lastIndex) theme.primaryAccent else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * System Audio Capture tech overview panel
 */
@Composable
fun SystemCapturePanel(
    viewModel: AudioDspViewModel,
    settings: DspSettings,
    theme: com.example.ui.theme.DspTheme
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "AUDIO PLAYBACK CAPTURE & ARCHITECTURE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = theme.primaryAccent
            )

            Text(
                text = "ABOUT THE ENGINE:",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = theme.secondaryAccent
            )
            
            Text(
                text = "BRO AUDIO captures real-time global system audio output from other music players (like Youtube or Spotify) leveraging the Android 10+ AudioPlaybackCapture API.\n\nAll arithmetic filters are calculated on raw IEEE 754 32-bit floats. Memory chunks and state arrays are pre-allocated during initialization, bypassing garbage-collector invocation during process loops to strictly avoid audio drops or glitch artifacts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "NATIVE NDK IMPLEMENTATION REFERENCE (C++ Oboe/AAudio):",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = theme.secondaryAccent
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF07080B))
                    .border(1.dp, Color(0xFF1B1E24), RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = """
// Audio callback function mapped to native systems
oboe::DataCallbackResult onAudioReady(
    oboe::AudioStream *oboeStream, 
    void *audioData, 
    int32_t numFrames
) {
    float *output = static_cast<float*>(audioData);
    // 1. Process 18-band Peaking cascade
    // 2. Filter 4-way Linkwitz-Riley Crossover
    // 3. Glide Peak Limiter Envelope & soft clamp
    // 4. Multiply Stereo Left/Right Faders
    return oboe::DataCallbackResult::Continue;
}
                    """.trimIndent(),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = Color(0xFF9ECE6A)
                    )
                )
            }
        }
    }
}
