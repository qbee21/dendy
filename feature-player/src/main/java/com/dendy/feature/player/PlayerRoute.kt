package com.dendy.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dendy.core.data.RecentGamesRepository
import com.dendy.core.data.RomCatalogRepository
import com.dendy.core.data.SaveStateRepository
import com.dendy.core.emulation.CoreProvider
import com.dendy.core.emulation.EmulationSession
import com.dendy.core.emulation.EmulationSessionFactory
import com.dendy.core.emulation.SessionStatus
import com.dendy.core.model.RomEntry
import com.dendy.core.model.RomId
import com.dendy.core.model.SaveSlot
import com.dendy.core.model.SaveSlotType
import com.dendy.core.model.VirtualButton
import kotlinx.coroutines.flow.MutableStateFlow

const val PLAYER_ROUTE_BASE = "player"
const val PLAYER_ROUTE_PATTERN = "$PLAYER_ROUTE_BASE/{romId}"

fun playerRoute(romId: RomId): String = "$PLAYER_ROUTE_BASE/${romId.value}"

interface PlayerDependencies {
    val romCatalogRepository: RomCatalogRepository
    val recentGamesRepository: RecentGamesRepository
    val saveStateRepository: SaveStateRepository
    val coreProvider: CoreProvider
    val emulationSessionFactory: EmulationSessionFactory
}

@Composable
fun PlayerRoute(
    romId: RomId,
    dependencies: PlayerDependencies,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PlayerViewModel = viewModel(
        key = romId.value,
        factory = PlayerViewModel.factory(romId, dependencies),
    )
    val state by viewModel.state.collectAsState()

    LaunchedEffect(romId.value) {
        viewModel.load()
    }

    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (previousOrientation != null) {
                activity.requestedOrientation = previousOrientation
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { viewModel.rendererView(it) },
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(4f / 3f),
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = Color(0x660D1116),
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                    Column {
                        Text(
                            text = state.entry?.metadata?.title ?: "Loading...",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Core: ${state.coreLabel}",
                            color = Color.White.copy(alpha = 0.76f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                IconButton(onClick = viewModel::toggleOverlay) {
                    Icon(Icons.Default.Menu, contentDescription = "Overlay", tint = Color.White)
                }
            }
        }

        if (state.overlayVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                TopControls(
                    state = state,
                    onTogglePause = viewModel::togglePause,
                    onQuickSave = viewModel::quickSave,
                    onQuickLoad = viewModel::quickLoad,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 64.dp),
                )

                state.errorMessage?.let { message ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 120.dp),
                        color = Color(0xCC45191D),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .width(340.dp)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Runtime error",
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = message,
                                color = Color.White.copy(alpha = 0.92f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            OutlinedButton(onClick = viewModel::retryLoad) {
                                Text("Retry")
                            }
                        }
                    }
                }

                Text(
                    text = state.lastSaveLabel,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-78).dp),
                    color = Color.White.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.bodySmall,
                )

                DpadOverlay(
                    buttonSize = 76.dp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 28.dp),
                    onVirtualButton = viewModel::onVirtualButton,
                )

                ActionButtonsOverlay(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 28.dp),
                    onVirtualButton = viewModel::onVirtualButton,
                )

                SystemButtonsRow(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 22.dp),
                    onVirtualButton = viewModel::onVirtualButton,
                )
            }
        }
    }
}

@Composable
private fun TopControls(
    state: PlayerUiState,
    onTogglePause: () -> Unit,
    onQuickSave: () -> Unit,
    onQuickLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OverlayPill(
            label = if (state.status == SessionStatus.PAUSED) "Resume" else "Pause",
            onClick = onTogglePause,
        )
        OverlayPill(
            label = "Save",
            onClick = onQuickSave,
        )
        OverlayPill(
            label = "Load",
            onClick = onQuickLoad,
        )
    }
}

@Composable
private fun OverlayPill(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color(0x3314181F),
        contentColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0x66E8E8E8)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun DpadOverlay(
    buttonSize: Dp,
    modifier: Modifier = Modifier,
    onVirtualButton: (VirtualButton, Boolean) -> Unit,
) {
    val crossWidth = buttonSize * 2.95f
    val crossArm = buttonSize * 1.12f
    val innerButtonWidth = buttonSize * 0.84f
    val innerButtonHeight = buttonSize * 0.84f
    val frameShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier.size(buttonSize * 3.25f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = crossArm, height = crossWidth)
                .border(2.dp, Color(0x55F2F2F2), frameShape)
                .background(Color(0x06000000), frameShape),
        )
        Box(
            modifier = Modifier
                .size(width = crossWidth, height = crossArm)
                .border(2.dp, Color(0x55F2F2F2), frameShape)
                .background(Color(0x06000000), frameShape),
        )

        DirectionPadButton(
            width = innerButtonWidth,
            height = innerButtonHeight,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 16.dp),
            onPressed = { onVirtualButton(VirtualButton.UP, it) },
        )
        DirectionPadButton(
            width = innerButtonWidth,
            height = innerButtonHeight,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 16.dp),
            onPressed = { onVirtualButton(VirtualButton.LEFT, it) },
        )
        DirectionPadButton(
            width = innerButtonWidth,
            height = innerButtonHeight,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-16).dp),
            onPressed = { onVirtualButton(VirtualButton.RIGHT, it) },
        )
        DirectionPadButton(
            width = innerButtonWidth,
            height = innerButtonHeight,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-16).dp),
            onPressed = { onVirtualButton(VirtualButton.DOWN, it) },
        )
    }
}

@Composable
private fun ActionButtonsOverlay(
    modifier: Modifier = Modifier,
    onVirtualButton: (VirtualButton, Boolean) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "TURBO",
            color = Color(0x99F0F0F0),
            style = MaterialTheme.typography.headlineSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            FaceButton(
                label = "B",
                onPressed = { onVirtualButton(VirtualButton.B, it) },
            )
            FaceButton(
                label = "A",
                onPressed = { onVirtualButton(VirtualButton.A, it) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            FaceButton(
                label = "B",
                onPressed = { onVirtualButton(VirtualButton.B, it) },
            )
            FaceButton(
                label = "A",
                onPressed = { onVirtualButton(VirtualButton.A, it) },
            )
        }
    }
}

@Composable
private fun FaceButton(
    label: String,
    onPressed: (Boolean) -> Unit,
) {
    HoldButton(
        label = label,
        width = 92.dp,
        height = 92.dp,
        shape = CircleShape,
        borderColor = Color(0x77E6E6E6),
        backgroundColor = Color.Transparent,
        textColor = Color(0xFFEFEFEF),
        onPressed = onPressed,
    )
}

@Composable
private fun SystemButtonsRow(
    modifier: Modifier = Modifier,
    onVirtualButton: (VirtualButton, Boolean) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HoldButton(
            label = "Select",
            width = 112.dp,
            height = 46.dp,
            shape = RoundedCornerShape(22.dp),
            borderColor = Color(0x66E8E8E8),
            backgroundColor = Color(0x22000000),
            textColor = Color(0xFFF0F0F0),
            onPressed = { onVirtualButton(VirtualButton.SELECT, it) },
        )
        HoldButton(
            label = "Start",
            width = 112.dp,
            height = 46.dp,
            shape = RoundedCornerShape(22.dp),
            borderColor = Color(0x66E8E8E8),
            backgroundColor = Color(0x22000000),
            textColor = Color(0xFFF0F0F0),
            onPressed = { onVirtualButton(VirtualButton.START, it) },
        )
    }
}

@Composable
private fun DirectionPadButton(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    onPressed: (Boolean) -> Unit,
) {
    HoldButton(
        label = "",
        width = width,
        height = height,
        modifier = modifier,
        shape = DirectionPadShape,
        borderColor = Color(0x88DADADA),
        backgroundColor = Color.Transparent,
        textColor = Color(0xFFEAEAEA),
        onPressed = onPressed,
    )
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

@Composable
private fun PlayerControls(
    state: PlayerUiState,
    onTogglePause: () -> Unit,
    onQuickSave: () -> Unit,
    onQuickLoad: () -> Unit,
    onRetry: () -> Unit,
    onVirtualButton: (VirtualButton, Boolean) -> Unit,
) {
    Box {
        TopControls(
            state = state,
            onTogglePause = onTogglePause,
            onQuickSave = onQuickSave,
            onQuickLoad = onQuickLoad,
        )
        if (state.errorMessage != null) {
            OutlinedButton(onClick = onRetry) {
                Text("Retry")
            }
        }
        SystemButtonsRow(onVirtualButton = onVirtualButton)
        DpadOverlay(buttonSize = 70.dp, onVirtualButton = onVirtualButton)
        ActionButtonsOverlay(onVirtualButton = onVirtualButton)
    }
}

@Composable
private fun ActionButtonsRow(
    buttonWidth: Dp,
    buttonHeight: Dp,
    onVirtualButton: (VirtualButton, Boolean) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HoldButton(
            label = "B",
            width = buttonWidth,
            height = buttonHeight,
            onPressed = { onVirtualButton(VirtualButton.B, it) },
        )
        HoldButton(
            label = "A",
            width = buttonWidth,
            height = buttonHeight,
            onPressed = { onVirtualButton(VirtualButton.A, it) },
        )
    }
}

@Composable
private fun DpadCluster(
    buttonSize: Dp,
    onVirtualButton: (VirtualButton, Boolean) -> Unit,
) {
    DpadOverlay(buttonSize = buttonSize, onVirtualButton = onVirtualButton)
}

@Composable
private fun HoldButton(
    label: String,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    borderColor: Color = Color.Transparent,
    backgroundColor: Color = Color(0x4DFFFFFF),
    textColor: Color = Color.White,
    onPressed: (Boolean) -> Unit,
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .background(backgroundColor, shape)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(2.dp, borderColor, shape)
                } else {
                    Modifier
                },
            )
            .pointerInput(label) {
                detectTapGestures(
                    onPress = {
                        onPressed(true)
                        tryAwaitRelease()
                        onPressed(false)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) {
            BasicText(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(color = textColor),
            )
        }
    }
}

private val DirectionPadShape = object : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        return RoundedCornerShape(18.dp).createOutline(size, layoutDirection, density)
    }
}

data class PlayerUiState(
    val entry: RomEntry? = null,
    val status: SessionStatus = SessionStatus.CREATED,
    val overlayVisible: Boolean = true,
    val isPlaceholder: Boolean = false,
    val coreLabel: String = "",
    val lastSaveLabel: String = "No save-state yet",
    val errorMessage: String? = null,
)

class PlayerViewModel(
    private val romId: RomId,
    private val dependencies: PlayerDependencies,
) : ViewModel() {
    private var session: EmulationSession? = null
    private val mutableState = MutableStateFlow(
        PlayerUiState(
            coreLabel = dependencies.coreProvider.defaultCore().displayName,
        ),
    )
    val state = mutableState

    fun rendererView(context: Context): View {
        ensureSession()
        return session?.rendererHost?.createView(context) ?: FrameLayout(context)
    }

    fun load() {
        ensureSession()
        val entry = dependencies.romCatalogRepository.findById(romId)
        val activeSession = session
        if (entry != null && state.value.entry == null && activeSession != null) {
            val result = runCatching {
                activeSession.load(entry)
                activeSession.start()
                dependencies.recentGamesRepository.markPlayed(entry)
            }
            if (result.isSuccess) {
                mutableState.value = mutableState.value.copy(
                    entry = entry,
                    status = activeSession.status,
                    lastSaveLabel = dependencies.saveStateRepository.getLastSession(romId)?.timestampLabel
                        ?: "Ready for quick save",
                    isPlaceholder = activeSession.isPlaceholder,
                    errorMessage = null,
                )
            } else {
                mutableState.value = mutableState.value.copy(
                    status = SessionStatus.CREATED,
                    errorMessage = result.exceptionOrNull()?.message
                        ?: "Unable to load selected ROM",
                )
            }
        }
    }

    fun retryLoad() {
        session?.release()
        session = null
        mutableState.value = mutableState.value.copy(
            entry = null,
            status = SessionStatus.CREATED,
            errorMessage = null,
        )
        load()
    }

    fun toggleOverlay() {
        mutableState.value = mutableState.value.copy(
            overlayVisible = !mutableState.value.overlayVisible,
        )
    }

    fun togglePause() {
        val activeSession = session ?: return
        if (activeSession.status == SessionStatus.PAUSED) {
            activeSession.resume()
        } else {
            activeSession.pause()
        }
        mutableState.value = mutableState.value.copy(status = activeSession.status)
    }

    fun quickSave() {
        val activeSession = session ?: return
        val summary = activeSession.saveState(
            SaveSlot("quick-1", SaveSlotType.QUICK, "Quick 1"),
        ) ?: return
        dependencies.saveStateRepository.upsert(summary)
        mutableState.value = mutableState.value.copy(lastSaveLabel = "Saved ${summary.slot.label} just now")
    }

    fun quickLoad() {
        val summary = dependencies.saveStateRepository.getSaveStates(romId).firstOrNull() ?: return
        session?.loadState(summary)
        mutableState.value = mutableState.value.copy(lastSaveLabel = "Loaded ${summary.slot.label}")
    }

    fun onVirtualButton(button: VirtualButton, pressed: Boolean) {
        session?.inputSink()?.send(button, pressed)
    }

    override fun onCleared() {
        session?.release()
        super.onCleared()
    }

    private fun ensureSession() {
        if (session != null) {
            return
        }
        val result = runCatching {
            dependencies.emulationSessionFactory.create(dependencies.coreProvider)
        }
        val created = result.getOrNull()
        if (created != null) {
            session = created
            mutableState.value = mutableState.value.copy(
                isPlaceholder = created.isPlaceholder,
                errorMessage = null,
            )
            return
        }
        val message = result.exceptionOrNull()?.message ?: "Unable to initialize libretro runtime"
        mutableState.value = mutableState.value.copy(errorMessage = message)
    }

    companion object {
        fun factory(romId: RomId, dependencies: PlayerDependencies): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PlayerViewModel(romId, dependencies) as T
                }
            }
        }
    }
}
