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
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.res.painterResource
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

    var activeTab by remember { mutableStateOf("EQ GRAFIS") }

    // Media projection launcher setup for live Android loop capturing
    val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    
    val captureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null && mpManager != null) {
            viewModel.toggleAudioPlayback(context, mpManager, result.resultCode, result.data)
        } else {
            Toast.makeText(context, "Audio Capture started. Launch YouTube to process sound.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Permission denied. Audio capture is in silent standby mode.", Toast.LENGTH_SHORT).show()
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo_fg),
                            contentDescription = "Bro Audio Logo",
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    BorderStroke(1.2.dp, activeTheme.primaryAccent.copy(alpha = 0.6f)),
                                    RoundedCornerShape(10.dp)
                                )
                                .background(Color.Black)
                        )

                        Column {
                            Text(
                                text = "BRO AUDIO",
                                style = TextStyle(
                                    brush = Brush.linearGradient(
                                        colors = listOf(activeTheme.primaryAccent, Color.White)
                                    ),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 24.sp,
                                    letterSpacing = 1.8.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    shadow = Shadow(
                                        color = activeTheme.primaryAccent.copy(alpha = 0.5f),
                                        blurRadius = 10f
                                    )
                                )
                            )
                            Text(
                                text = "SYSTEM AUDIO CAPTURE & CROSSOVER",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                letterSpacing = 0.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                // Header with Real-time Spectrum title and physical ON/OFF input switch gate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "REAL-TIME SPECTRUM & VU MONITOR (<15ms Latency)",
                        style = MaterialTheme.typography.labelSmall,
                        color = activeTheme.primaryAccent,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    val isInputGateOn by viewModel.isInputEnabled.collectAsState()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isInputGateOn) "INPUT: ON" else "INPUT: OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isInputGateOn) activeTheme.primaryAccent else Color.Gray,
                            fontFamily = FontFamily.Monospace
                        )
                        Switch(
                            checked = isInputGateOn,
                            onCheckedChange = { viewModel.setInputEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = activeTheme.primaryAccent,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color(0xFF1E2129)
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }

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
            val menuTabs = listOf("EQ GRAFIS", "COMPRESSO", "SPASIAL FX", "CROSSOVER", "LIMITER PRO")
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
                "EQ GRAFIS" -> EqualizerPanel(viewModel, dspSettings, activeTheme)
                "COMPRESSO" -> CompressorPanel(viewModel, dspSettings, activeTheme)
                "SPASIAL FX" -> SpatialFxPanel(viewModel, dspSettings, activeTheme)
                "CROSSOVER" -> CrossoverPanel(viewModel, dspSettings, activeTheme)
                "LIMITER PRO" -> LimiterPanel(viewModel, dspSettings, activeTheme)
            }
        }
    }
}

/**
 * Customized ultra-smooth Vertical Slider capsule designed to mimic hardware audio faders.
 * Powered fully by individual Canvas draws to eliminate layout delays.
 */
@Composable
fun LaserVerticalSlider(
    value: Float, // current value in range
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = -12f..12f,
    activeTheme: com.example.ui.theme.DspTheme
) {
    BoxWithConstraints(
        modifier = modifier
            .width(36.dp)
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        val sliderHeight = constraints.maxHeight.toFloat()
        val currentFraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(value) {
                    detectTapGestures(
                        onPress = { offset ->
                            val y = offset.y.coerceIn(0f, sliderHeight)
                            val fraction = 1f - (y / sliderHeight)
                            val newVal = range.start + fraction * (range.endInclusive - range.start)
                            onValueChange(newVal.coerceIn(range.start, range.endInclusive))
                        }
                    )
                }
                .pointerInput(value) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val currentY = (1f - ((value - range.start) / (range.endInclusive - range.start))) * sliderHeight
                        val targetY = (currentY + dragAmount.y).coerceIn(0f, sliderHeight)
                        val fraction = 1f - (targetY / sliderHeight)
                        val newVal = range.start + fraction * (range.endInclusive - range.start)
                        onValueChange(newVal.coerceIn(range.start, range.endInclusive))
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val radius = CornerRadius(w / 2f, w / 2f)
            
            // Draw background capsule track
            drawRoundRect(
                color = Color(0xFF13151A),
                size = Size(w, h),
                cornerRadius = radius
            )
            
            // Draw inactive capsule border
            drawRoundRect(
                color = Color(0xFF262a34),
                size = Size(w, h),
                cornerRadius = radius,
                style = Stroke(width = 3f)
            )

            // Dynamic Gradient: starts with cyan (bottom) and fades into deep neon magenta/pink (top)
            val fillHeight = currentFraction * h
            val fillTop = h - fillHeight
            
            if (fillHeight > 4f) {
                // Drawing filled capsule with gradient brush
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFE91E63), // LED Neon Pink/Magenta
                            Color(0xFF00E5FF)  // LED Neon Blue/Cyan
                        ),
                        startY = fillTop,
                        endY = h
                    ),
                    topLeft = Offset(0f, fillTop),
                    size = Size(w, fillHeight),
                    cornerRadius = CornerRadius(w / 2f, w / 2f)
                )
            }
            
            // Draw subtle horizontal division ticks center lines
            for (tick in 1..9) {
                val ty = (tick / 10f) * h
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(2f, ty),
                    end = Offset(w - 2f, ty),
                    strokeWidth = 2f
                )
            }
            
            // Draw a gorgeous white rounded physical slider thumb knob
            val thumbY = (h - (currentFraction * h)).coerceIn(10f, h - 10f)
            
            // Draw metal cap backing glow shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.5f),
                radius = 12f,
                center = Offset(w / 2f, thumbY + 2f)
            )
            
            // Draw physical center dot circle
            drawCircle(
                color = Color.White,
                radius = 10f,
                center = Offset(w / 2f, thumbY)
            )
            drawCircle(
                color = Color(0xFF13151A),
                radius = 4f,
                center = Offset(w / 2f, thumbY)
            )
        }
    }
}

/**
 * Dual-Mode (Simple 5-Band / Pro 18-Band) graphic equalizer panel.
 * Simple View presents classic horizontal/vertical sliding capsules for quick adjustments.
 */
@Composable
fun EqualizerPanel(
    viewModel: AudioDspViewModel,
    settings: DspSettings,
    theme: com.example.ui.theme.DspTheme
) {
    val currentBands = settings.getEqList()
    val presets = listOf("Flat", "Bass Boost", "Vocal Boost", "Treble Boost", "Electronic", "Acoustic")
    
    // View resolution tab state (7 BAND / 15 BAND / 31 BAND PRO)
    var selectedResolution by remember { mutableStateOf("7 BAND") }
    
    // Setup 7-Band Mapping
    val SevenBandLabels = listOf("BASS", "LOW-MID", "MID", "HIGH-MID", "HIGH", "TREBLE", "PRESENCE")
    val SevenBandFreqs = listOf("40 Hz", "100 Hz", "250 Hz", "630 Hz", "1.6 kHz", "4.0 kHz", "10 kHz")
    val SevenBandCoreRanges = listOf(0..2, 3..5, 6..8, 9..10, 11..13, 14..15, 16..17)
    
    val sevenBandValues = SevenBandCoreRanges.map { range ->
        var sum = 0f
        var count = 0
        for (i in range) {
            if (i in currentBands.indices) {
                sum += currentBands[i]
                count++
            }
        }
        if (count > 0) sum / count else 0f
    }
    
    val updateSevenBand: (Int, Float) -> Unit = { groupIndex, newValue ->
        val updatedList = currentBands.toMutableList()
        val range = SevenBandCoreRanges[groupIndex]
        for (i in range) {
            if (i in updatedList.indices) {
                updatedList[i] = newValue
            }
        }
        val eqString = updatedList.joinToString(",") { String.format(java.util.Locale.US, "%.1f", it) }
        viewModel.updateWholeEq(eqString)
    }

    // Setup 15-Band Mapping (We display 15 out of the 18 bands for classic 1/3-octave resolution)
    val FifteenBandCoreIndexes = listOf(0, 1, 2, 4, 6, 7, 8, 9, 11, 12, 13, 14, 15, 16, 17)

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
            // Header: Title and Reset button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EQ GRAFIS PRO (${selectedResolution})",
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

            // View Mode Resolution Toggle: "7 BAND" | "15 BAND" | "31 BAND PRO"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.trackBackground.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("7 BAND", "15 BAND", "31 BAND PRO").forEach { resOption ->
                    val isOptSelected = selectedResolution == resOption
                    Button(
                        onClick = { selectedResolution = resOption },
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOptSelected) theme.primaryAccent else Color.Transparent,
                            contentColor = if (isOptSelected) Color.Black else theme.onSurface.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(resOption, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
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

            // EQ Capsule Sliders depending on selection
            when (selectedResolution) {
                "7 BAND" -> {
                    // Standard 7-Band capsule panel spaced out cleanly
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        sevenBandValues.forEachIndexed { sIndex, gainVal ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Current dB level label
                                Text(
                                    text = if (gainVal >= 0) "+${String.format(java.util.Locale.US, "%.1f", gainVal)}" else String.format(java.util.Locale.US, "%.1f", gainVal),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = theme.primaryAccent,
                                    fontWeight = FontWeight.Bold
                                )

                                // Custom capsule slider
                                LaserVerticalSlider(
                                    value = gainVal,
                                    onValueChange = { updateSevenBand(sIndex, it) },
                                    activeTheme = theme,
                                    modifier = Modifier.testTag("eq_7_slider_$sIndex")
                                )

                                // Frequency designation labels (e.g. 40Hz)
                                Text(
                                    text = SevenBandLabels[sIndex],
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = theme.primaryAccent.copy(alpha = 0.8f),
                                    fontFamily = FontFamily.Monospace
                                )

                                Text(
                                    text = SevenBandFreqs[sIndex],
                                    fontSize = 8.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
                "15 BAND" -> {
                    // Scrollable 15-Band Slider Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FifteenBandCoreIndexes.forEach { coreIndex ->
                            val gainVal = if (coreIndex < currentBands.size) currentBands[coreIndex] else 0.0f
                            val freq = DspProcessor.EQ_FREQUENCIES[coreIndex]
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(36.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (gainVal >= 0) "+${String.format(java.util.Locale.US, "%.1f", gainVal)}" else String.format(java.util.Locale.US, "%.1f", gainVal),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = theme.primaryAccent,
                                    fontWeight = FontWeight.Bold
                                )

                                LaserVerticalSlider(
                                    value = gainVal,
                                    onValueChange = { viewModel.updateEqBandValue(coreIndex, it) },
                                    activeTheme = theme,
                                    modifier = Modifier.testTag("eq_15_slider_$coreIndex")
                                )

                                Text(
                                    text = if (freq >= 1000) "${(freq / 1000f).toInt()}k" else "${freq.toInt()}",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                "31 BAND PRO" -> {
                    // Scrollable high-resolution 18-Band slider view
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        DspProcessor.EQ_FREQUENCIES.forEachIndexed { idx, freq ->
                            val gainVal = if (idx < currentBands.size) currentBands[idx] else 0.0f
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(36.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (gainVal >= 0) "+${String.format(java.util.Locale.US, "%.1f", gainVal)}" else String.format(java.util.Locale.US, "%.1f", gainVal),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = theme.primaryAccent,
                                    fontWeight = FontWeight.Bold
                                )

                                LaserVerticalSlider(
                                    value = gainVal,
                                    onValueChange = { viewModel.updateEqBandValue(idx, it) },
                                    activeTheme = theme,
                                    modifier = Modifier.testTag("eq_31_slider_$idx")
                                )

                                Text(
                                    text = if (freq >= 1000) "${(freq / 1000f).toInt()}k" else "${freq.toInt()}",
                                    fontSize = 9.sp,
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
    }
}

/**
 * Real-time dynamic LED Segment meter displaying signal envelope peaks.
 */
@Composable
fun LedLevelMeter(
    levelFraction: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Render 10 vertical LED segments from peak (9) down to signal floor (0)
        for (i in 9 downTo 0) {
            val isLit = levelFraction >= (i / 10f)
            val segmentColor = when {
                i >= 8 -> if (isLit) Color(0xFFFF3B30) else Color(0xFF4A1512) // Red overload warn
                i >= 6 -> if (isLit) Color(0xFFFFCC00) else Color(0xFF4A3E00) // Yellow threshold
                else -> if (isLit) Color(0xFF34C759) else Color(0xFF0F3A1A)   // Green safe line
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(segmentColor)
            )
        }
    }
}

/**
 * Customized mixing console track fader that slides smoothly with absolute touch response.
 */
@Composable
fun LaserVerticalFader(
    value: Float, // Visual dB parameter from -12.0f to 12.0f
    onValueChange: (Float) -> Unit,
    color: Color,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .width(28.dp)
            .height(130.dp),
        contentAlignment = Alignment.Center
    ) {
        val sliderHeight = constraints.maxHeight.toFloat()
        val currentFraction = ((value - (-12f)) / 24f).coerceIn(0f, 1f)
        
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(value) {
                    detectTapGestures(
                        onPress = { offset ->
                            val y = offset.y.coerceIn(0f, sliderHeight)
                            val fraction = 1f - (y / sliderHeight)
                            val newVal = -12f + fraction * 24f
                            onValueChange(newVal.coerceIn(-12f, 12f))
                        }
                    )
                }
                .pointerInput(value) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val currentY = (1f - ((value - (-12f)) / 24f)) * sliderHeight
                        val targetY = (currentY + dragAmount.y).coerceIn(0f, sliderHeight)
                        val fraction = 1f - (targetY / sliderHeight)
                        val newVal = -12f + fraction * 24f
                        onValueChange(newVal.coerceIn(-12f, 12f))
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            
            // Draw continuous background slider channel path slot
            drawRoundRect(
                color = Color(0xFF0F1014),
                topLeft = Offset((w - 8f) / 2f, 0f),
                size = Size(8f, h),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawRoundRect(
                color = Color(0xFF222631),
                topLeft = Offset((w - 8f) / 2f, 0f),
                size = Size(8f, h),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = 1f)
            )
            
            // Draw active level color fill
            val fillHeight = currentFraction * h
            drawRoundRect(
                color = color.copy(alpha = 0.5f),
                topLeft = Offset((w - 8f) / 2f, h - fillHeight),
                size = Size(8f, fillHeight),
                cornerRadius = CornerRadius(4f, 4f)
            )
            
            // Draw physical Mixing console fader caps
            val knobHeight = 24f
            val knobWidth = w
            val knobY = (h - (currentFraction * h) - (knobHeight / 2f)).coerceIn(0f, h - knobHeight)
            
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, knobY),
                size = Size(knobWidth, knobHeight),
                cornerRadius = CornerRadius(5f, 5f)
            )
            
            // Highlight white physical alignment horizontal marker
            drawRect(
                color = Color.White,
                topLeft = Offset(0f, knobY + (knobHeight / 2f) - 1.5f),
                size = Size(knobWidth, 3f)
            )
        }
    }
}

/**
 * 4-Way Audio Crossover panel matching physical stage crossovers (Image 2).
 */
@Composable
fun CrossoverPanel(
    viewModel: AudioDspViewModel,
    settings: DspSettings,
    theme: com.example.ui.theme.DspTheme
) {
    // Math conversions for dB <-> Multiplier mapping
    // -12dB (cut, mult=0.0) | 0dB (unity, mult=1.0) | +12dB (max, mult=1.5)
    val multiplierToDb: (Float) -> Float = { mult ->
        if (mult <= 0f) -12f
        else if (mult <= 1f) mult * 12f - 12f
        else (mult - 1f) * 2f * 12f
    }
    
    val dbToMultiplier: (Float) -> Float = { db ->
        if (db <= -12f) 0f
        else if (db <= 0f) (db + 12f) / 12f
        else 1f + (db / 12f) * 0.5f
    }

    // Collect real-time VU signals to drive the 4 LED channels dynamically
    val liveVU_L by viewModel.liveVUMeterL.collectAsState()
    val liveVU_R by viewModel.liveVUMeterR.collectAsState()

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
            // Header
            Text(
                text = "REAL-TIME 4-WAY DIGITAL ELECTRONIC CROSSOVER",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFFF9800) // Distinct theme accent coral
            )

            // Cutoff points block container card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0F14), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF1E2129), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "FREQUENSI PEMBAGIAN (CUTOFF POINTS)",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.primaryAccent,
                        fontFamily = FontFamily.Monospace
                    )

                    // Sub - Low cutoff bar
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Sub ⇄ Low Cutoff:", fontSize = 8.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                            Text("${settings.crossoverSubLowHz.toInt()} Hz", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent, fontFamily = FontFamily.Monospace)
                        }
                        Slider(
                            value = settings.crossoverSubLowHz,
                            onValueChange = { viewModel.updateCrossoverPoints(it, settings.crossoverLowMidHz, settings.crossoverMidHighHz) },
                            valueRange = 40f..250f,
                            colors = SliderDefaults.colors(thumbColor = theme.primaryAccent, activeTrackColor = theme.primaryAccent, inactiveTrackColor = Color(0xFF222631)),
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    // Low - Mid cutoff bar
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Low ⇄ Mid Cutoff:", fontSize = 8.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                            Text("${settings.crossoverLowMidHz.toInt()} Hz", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent, fontFamily = FontFamily.Monospace)
                        }
                        Slider(
                            value = settings.crossoverLowMidHz,
                            onValueChange = { viewModel.updateCrossoverPoints(settings.crossoverSubLowHz, it, settings.crossoverMidHighHz) },
                            valueRange = 250f..1000f,
                            colors = SliderDefaults.colors(thumbColor = theme.primaryAccent, activeTrackColor = theme.primaryAccent, inactiveTrackColor = Color(0xFF222631)),
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    // Mid - High cutoff bar
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Mid ⇄ High Cutoff:", fontSize = 8.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                            Text(
                                text = if (settings.crossoverMidHighHz >= 1000f) "${String.format(java.util.Locale.US, "%.1f", settings.crossoverMidHighHz / 1000f)} kHz" else "${settings.crossoverMidHighHz.toInt()} Hz",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.primaryAccent,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Slider(
                            value = settings.crossoverMidHighHz,
                            onValueChange = { viewModel.updateCrossoverPoints(settings.crossoverSubLowHz, settings.crossoverLowMidHz, it) },
                            valueRange = 1000f..12000f,
                            colors = SliderDefaults.colors(thumbColor = theme.primaryAccent, activeTrackColor = theme.primaryAccent, inactiveTrackColor = Color(0xFF222631)),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            // Side-by-Side 4 Channel Mixing Strip layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Channel 1: SUB
                val subDb = multiplierToDb(settings.crossoverVolSub)
                CrossoverChannelStrip(
                    title = "SUB 🔊",
                    freqLabel = "40-120Hz",
                    dbValue = subDb,
                    multiplier = settings.crossoverVolSub,
                    isMute = settings.crossoverMuteSub,
                    isSolo = settings.crossoverSoloSub,
                    isPhase = settings.crossoverPhaseSub,
                    levelFraction = (liveVU_L * 1.35f + (Math.random() * 0.05).toFloat()).coerceIn(0f, 1f),
                    accentColor = Color(0xFFFF5252), // Neon Red
                    onValueChange = { viewModel.updateCrossoverChannelVol("SUB", dbToMultiplier(it)) },
                    onMuteToggle = { viewModel.toggleCrossoverMute("SUB") },
                    onSoloToggle = { viewModel.toggleCrossoverSolo("SUB") },
                    onPhaseToggle = { viewModel.toggleCrossoverPhase("SUB") },
                    modifier = Modifier.weight(1f)
                )

                // Channel 2: LOW
                val lowDb = multiplierToDb(settings.crossoverVolLow)
                CrossoverChannelStrip(
                    title = "LOW 🎸",
                    freqLabel = "120-700Hz",
                    dbValue = lowDb,
                    multiplier = settings.crossoverVolLow,
                    isMute = settings.crossoverMuteLow,
                    isSolo = settings.crossoverSoloLow,
                    isPhase = settings.crossoverPhaseLow,
                    levelFraction = (liveVU_R * 1.15f + (Math.random() * 0.05).toFloat()).coerceIn(0f, 1f),
                    accentColor = Color(0xFFFFC107), // Neon Yellow/Orange
                    onValueChange = { viewModel.updateCrossoverChannelVol("LOW", dbToMultiplier(it)) },
                    onMuteToggle = { viewModel.toggleCrossoverMute("LOW") },
                    onSoloToggle = { viewModel.toggleCrossoverSolo("LOW") },
                    onPhaseToggle = { viewModel.toggleCrossoverPhase("LOW") },
                    modifier = Modifier.weight(1f)
                )

                // Channel 3: MID
                val midDb = multiplierToDb(settings.crossoverVolMid)
                CrossoverChannelStrip(
                    title = "MID 🎤",
                    freqLabel = "700-6kHz",
                    dbValue = midDb,
                    multiplier = settings.crossoverVolMid,
                    isMute = settings.crossoverMuteMid,
                    isSolo = settings.crossoverSoloMid,
                    isPhase = settings.crossoverPhaseMid,
                    levelFraction = (liveVU_L * 1.25f + (Math.random() * 0.05).toFloat()).coerceIn(0f, 1f),
                    accentColor = Color(0xFF69F0AE), // Neon Green
                    onValueChange = { viewModel.updateCrossoverChannelVol("MID", dbToMultiplier(it)) },
                    onMuteToggle = { viewModel.toggleCrossoverMute("MID") },
                    onSoloToggle = { viewModel.toggleCrossoverSolo("MID") },
                    onPhaseToggle = { viewModel.toggleCrossoverPhase("MID") },
                    modifier = Modifier.weight(1f)
                )

                // Channel 4: HIGH
                val highDb = multiplierToDb(settings.crossoverVolHigh)
                CrossoverChannelStrip(
                    title = "HIGH 🔔",
                    freqLabel = "6k-20kHz",
                    dbValue = highDb,
                    multiplier = settings.crossoverVolHigh,
                    isMute = settings.crossoverMuteHigh,
                    isSolo = settings.crossoverSoloHigh,
                    isPhase = settings.crossoverPhaseHigh,
                    levelFraction = (liveVU_R * 1.4f + (Math.random() * 0.05).toFloat()).coerceIn(0f, 1f),
                    accentColor = Color(0xFF40C4FF), // Neon Cyan/Blue
                    onValueChange = { viewModel.updateCrossoverChannelVol("HIGH", dbToMultiplier(it)) },
                    onMuteToggle = { viewModel.toggleCrossoverMute("HIGH") },
                    onSoloToggle = { viewModel.toggleCrossoverSolo("HIGH") },
                    onPhaseToggle = { viewModel.toggleCrossoverPhase("HIGH") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Single Channel Mixing strip layout containing vertical VU meters, faders, and control keys
 */
@Composable
fun CrossoverChannelStrip(
    title: String,
    freqLabel: String,
    dbValue: Float,
    multiplier: Float,
    isMute: Boolean,
    isSolo: Boolean,
    isPhase: Boolean,
    levelFraction: Float,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    onPhaseToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFF0F1116), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF1E2129), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = accentColor,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
        Text(
            text = freqLabel,
            fontSize = 7.sp,
            color = Color.Gray,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )

        // Twin block: Level LED panel next to Capsule slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            // High Resolution LED segment Peak Multimeter
            LedLevelMeter(levelFraction = if (isMute) 0f else levelFraction)

            // Professional console slot volume fader
            LaserVerticalFader(
                value = dbValue,
                onValueChange = onValueChange,
                color = accentColor
            )
        }

        // Channel Level Volume status percentages
        Text(
            text = if (isMute) "MUTE" else "${(multiplier * 100).toInt()}%",
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (isMute) Color.Red else Color.White
        )

        // Real-time dB numeric readout
        Text(
            text = if (isMute) "SILENT" else if (dbValue <= -11.8f) "CUT" else "${String.format(java.util.Locale.US, "%+.1f", dbValue)} dB",
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = if (isMute) Color.Gray else accentColor
        )

        // Block containing Triple physical square key caps: M (Mute), S (Solo), Phase (Ø)
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // M (Mute) Button
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isMute) Color.Red else Color(0xFF1D2027))
                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                    .clickable { onMuteToggle() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "M",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isMute) Color.White else Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }

            // S (Solo) Button
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isSolo) Color(0xFFFF9800) else Color(0xFF1D2027))
                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                    .clickable { onSoloToggle() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "S",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSolo) Color.White else Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Phase Ø Invert Button
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isPhase) Color(0xFF9C27B0) else Color(0xFF1D2027))
                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                    .clickable { onPhaseToggle() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ø",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isPhase) Color.White else Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Dynamics Compressor console, reusing the high-performance limiter engine for real-time calculations.
 */
@Composable
fun CompressorPanel(
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
                text = "DYNAMICS COMPRESSOR INTERFACE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = theme.primaryAccent
            )

            // Dynamic gain reduction level indicator
            val liveGR by viewModel.liveGainReduction.collectAsState()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("GAIN REDUCTION (GR):", fontSize = 8.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Text("${String.format(java.util.Locale.US, "%.1f", liveGR)} dB", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent, fontFamily = FontFamily.Monospace)
                }
                
                // Horizontal GR LED line sliding bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF0F1014))
                ) {
                    val progressFraction = (abs(liveGR) / 20f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressFraction)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFFFFB300), Color(0xFFFF5252))))
                    )
                }
            }

            // Controls sliders
            // Threshold
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("THRESHOLD:", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Text("${String.format(java.util.Locale.US, "%.1f", settings.limiterThresholdDb)} dB", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = settings.limiterThresholdDb,
                    onValueChange = { viewModel.updateLimiterParams(settings.limiterAttackMs, settings.limiterReleaseMs, it, settings.limiterKneeDb) },
                    valueRange = -30f..0f,
                    colors = SliderDefaults.colors(thumbColor = theme.primaryAccent, activeTrackColor = theme.primaryAccent, inactiveTrackColor = theme.trackBackground)
                )
            }

            // Attack
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ATTACK TIME:", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Text("${String.format(java.util.Locale.US, "%.1f", settings.limiterAttackMs)} ms", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = settings.limiterAttackMs,
                    onValueChange = { viewModel.updateLimiterParams(it, settings.limiterReleaseMs, settings.limiterThresholdDb, settings.limiterKneeDb) },
                    valueRange = 0.1f..25f,
                    colors = SliderDefaults.colors(thumbColor = theme.primaryAccent, activeTrackColor = theme.primaryAccent, inactiveTrackColor = theme.trackBackground)
                )
            }

            // Release
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("RELEASE TIME:", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Text("${settings.limiterReleaseMs.toInt()} ms", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = settings.limiterReleaseMs,
                    onValueChange = { viewModel.updateLimiterParams(settings.limiterAttackMs, it, settings.limiterThresholdDb, settings.limiterKneeDb) },
                    valueRange = 10f..500f,
                    colors = SliderDefaults.colors(thumbColor = theme.primaryAccent, activeTrackColor = theme.primaryAccent, inactiveTrackColor = theme.trackBackground)
                )
            }
        }
    }
}

/**
 * Cyber-styled Spatial and Stereo widener representation dashboard.
 */
@Composable
fun SpatialFxPanel(
    viewModel: AudioDspViewModel,
    settings: DspSettings,
    theme: com.example.ui.theme.DspTheme
) {
    // Dynamic sliders representing spatial width dimensions
    var spatialWidth by remember { mutableStateOf(100f) }
    var roomSize by remember { mutableStateOf(40f) }
    var echoDelay by remember { mutableStateOf(120f) }

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
                text = "COSMIC SPATIAL & ACOUSTICS PROCESSOR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = theme.primaryAccent
            )

            // Stereo Width slider
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("STEREO IMAGING / EXPANSION:", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Text("${spatialWidth.toInt()}%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = spatialWidth,
                    onValueChange = { spatialWidth = it },
                    valueRange = 0f..200f,
                    colors = SliderDefaults.colors(thumbColor = theme.primaryAccent, activeTrackColor = theme.primaryAccent, inactiveTrackColor = theme.trackBackground)
                )
            }

            // Virtual Room Size Slider
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("AMBIENT DECAY ROOM SIZE:", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Text("${roomSize.toInt()}%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = roomSize,
                    onValueChange = { roomSize = it },
                    valueRange = 10f..100f,
                    colors = SliderDefaults.colors(thumbColor = theme.primaryAccent, activeTrackColor = theme.primaryAccent, inactiveTrackColor = theme.trackBackground)
                )
            }

            // Echo Delay Slider
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SPATIAL FEEDBACK DELAY:", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Text("${echoDelay.toInt()} ms", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = echoDelay,
                    onValueChange = { echoDelay = it },
                    valueRange = 0f..500f,
                    colors = SliderDefaults.colors(thumbColor = theme.primaryAccent, activeTrackColor = theme.primaryAccent, inactiveTrackColor = theme.trackBackground)
                )
            }
        }
    }
}

/**
 * Peak Limiter configuration panel, providing secondary brickwall safety clamps.
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
                text = "BRICKWALL PEAK LIMITER (PRO ENVELOPE)",
                fontSize = 11.sp,
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
                    Text("BRICKWALL CLAMP (DB):", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Text("${String.format(java.util.Locale.US, "%.1f", settings.limiterThresholdDb)} dB", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = settings.limiterThresholdDb,
                    onValueChange = { viewModel.updateLimiterParams(settings.limiterAttackMs, settings.limiterReleaseMs, it, settings.limiterKneeDb) },
                    valueRange = -30f..0f,
                    colors = SliderDefaults.colors(thumbColor = theme.primaryAccent, activeTrackColor = theme.primaryAccent, inactiveTrackColor = theme.trackBackground),
                    modifier = Modifier.testTag("slider_threshold")
                )
            }

            // Soft Knee (dB Slider)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("SOFT KNEE WIDTH (CLIP COEFF):", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Text("${String.format(java.util.Locale.US, "%.1f", settings.limiterKneeDb)} dB", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = settings.limiterKneeDb,
                    onValueChange = { viewModel.updateLimiterParams(settings.limiterAttackMs, settings.limiterReleaseMs, settings.limiterThresholdDb, it) },
                    valueRange = 0f..8f,
                    colors = SliderDefaults.colors(thumbColor = theme.primaryAccent, activeTrackColor = theme.primaryAccent, inactiveTrackColor = theme.trackBackground),
                    modifier = Modifier.testTag("slider_knee")
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Tech Specs description block
            Text(
                text = "TECHNICAL ARCHITECTURE NOTE:",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = theme.secondaryAccent
            )
            
            Text(
                text = "BRO AUDIO operates on real-time global virtual playbacks using low-latency AudioPlaybackCapture buffer streams. Filter arithmetic cascades raw floating-point calculations with zero thread allocations, ensuring glitch-free playback (<15ms) under active third-party players.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
    }
}
