package com.dendy.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dendy.core.data.BuiltInCatalogRepository
import com.dendy.core.data.RecentGamesRepository
import com.dendy.core.data.SaveStateRepository
import com.dendy.core.data.UserRomRepository
import com.dendy.core.model.RomEntry
import com.dendy.core.model.RomId
import com.dendy.core.model.RomSource
import kotlinx.coroutines.flow.MutableStateFlow

const val LIBRARY_ROUTE = "library"

interface LibraryDependencies {
    val builtInCatalogRepository: BuiltInCatalogRepository
    val userRomRepository: UserRomRepository
    val recentGamesRepository: RecentGamesRepository
    val saveStateRepository: SaveStateRepository
}

@Composable
fun LibraryRoute(
    dependencies: LibraryDependencies,
    onOpenPlayer: (RomId) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(dependencies),
    )
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Dendy 2026", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Built-in legal catalog + imported ROM shell",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        HeroBlock(
            lastPlayed = state.lastPlayed,
            onContinue = { state.lastPlayed?.let { onOpenPlayer(it.id) } },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.selectedSource == LibrarySource.BuiltIn,
                onClick = { viewModel.selectSource(LibrarySource.BuiltIn) },
                label = { Text("Встроенные игры") },
            )
            FilterChip(
                selected = state.selectedSource == LibrarySource.MyRoms,
                onClick = { viewModel.selectSource(LibrarySource.MyRoms) },
                label = { Text("Мои ROM") },
            )
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::updateQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search / filter") },
            singleLine = true,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(onClick = {}, label = { Text("Recent first") })
            AssistChip(onClick = {}, label = { Text("Genre") })
            AssistChip(onClick = {}, label = { Text("Hash dedupe ready") })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${state.visibleGames.size} game${if (state.visibleGames.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = viewModel::simulateImport) {
                Text("Import via SAF")
            }
        }

        if (state.visibleGames.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (state.selectedSource == LibrarySource.BuiltIn) {
                        "No built-in games were found."
                    } else {
                        "No imported ROM yet."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
                items(state.visibleGames, key = { it.id.value }) { card ->
                    GameCard(
                        card = card,
                        onOpen = { onOpenPlayer(card.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroBlock(
    lastPlayed: RomEntry?,
    onContinue: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF173B58), Color(0xFF256D5A), Color(0xFFEB8B3B)),
                    ),
                )
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Continue where you left off",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.9f),
                )
                Text(
                    text = lastPlayed?.metadata?.title ?: "No recent session yet",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = lastPlayed?.metadata?.description
                        ?: "The player hero block is already wired for last-session resume.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
                Button(onClick = onContinue, enabled = lastPlayed != null) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Continue")
                }
            }
        }
    }
}

@Composable
private fun GameCard(
    card: GameCardModel,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        brush = Brush.linearGradient(
                            if (card.sourceLabel == "Built-in") {
                                listOf(Color(0xFF12304A), Color(0xFF2D7F74))
                            } else {
                                listOf(Color(0xFF523A1F), Color(0xFF9D5F2E))
                            },
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = card.genre.uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(card.sourceLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            Text(card.progressLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AssistChip(onClick = {}, label = { Text(card.saveBadge) })
        }
    }
}

data class GameCardModel(
    val id: RomId,
    val title: String,
    val genre: String,
    val sourceLabel: String,
    val progressLabel: String,
    val saveBadge: String,
)

enum class LibrarySource {
    BuiltIn,
    MyRoms,
}

data class LibraryUiState(
    val query: String = "",
    val selectedSource: LibrarySource = LibrarySource.BuiltIn,
    val lastPlayed: RomEntry? = null,
    val visibleGames: List<GameCardModel> = emptyList(),
)

class LibraryViewModel(
    private val dependencies: LibraryDependencies,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        LibraryUiState(),
    )
    val state = mutableState

    init {
        rebuild()
    }

    fun selectSource(source: LibrarySource) {
        mutableState.value = mutableState.value.copy(selectedSource = source)
        rebuild()
    }

    fun updateQuery(query: String) {
        mutableState.value = mutableState.value.copy(query = query)
        rebuild()
    }

    fun simulateImport() {
        dependencies.userRomRepository.importFromSaf(android.net.Uri.parse("content://demo/imported_rom_${System.currentTimeMillis()}.nes"))
        mutableState.value = mutableState.value.copy(selectedSource = LibrarySource.MyRoms)
        rebuild()
    }

    private fun rebuild() {
        val current = mutableState.value
        val entries = if (current.selectedSource == LibrarySource.BuiltIn) {
            dependencies.builtInCatalogRepository.getBuiltInEntries()
        } else {
            dependencies.userRomRepository.getImportedEntries()
        }

        val filtered = entries
            .filter { current.query.isBlank() || it.metadata.title.contains(current.query, ignoreCase = true) }
            .map { entry ->
                val saveCount = dependencies.saveStateRepository.getSaveStates(entry.id).size
                GameCardModel(
                    id = entry.id,
                    title = entry.metadata.title,
                    genre = entry.metadata.genre,
                    sourceLabel = if (entry.location.source == RomSource.BUILT_IN) "Built-in" else "My ROMs",
                    progressLabel = entry.metadata.releaseLabel,
                    saveBadge = "$saveCount save slot${if (saveCount == 1) "" else "s"}",
                )
            }

        mutableState.value = current.copy(
            lastPlayed = dependencies.recentGamesRepository.getLastPlayed(),
            visibleGames = filtered,
        )
    }

    companion object {
        fun factory(dependencies: LibraryDependencies): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LibraryViewModel(dependencies) as T
                }
            }
        }
    }
}
