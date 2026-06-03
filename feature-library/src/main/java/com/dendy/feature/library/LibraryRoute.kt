package com.dendy.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dendy.core.data.BuiltInCatalogRepository
import com.dendy.core.data.SaveStateRepository
import com.dendy.core.data.UserRomRepository
import com.dendy.core.model.RomId
import kotlinx.coroutines.flow.MutableStateFlow

const val LIBRARY_ROUTE = "library"

interface LibraryDependencies {
    val builtInCatalogRepository: BuiltInCatalogRepository
    val userRomRepository: UserRomRepository
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
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.size(0.dp)) {
                Text("Dendy", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Встроенные и импортированные ROM",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Настройки")
            }
        }

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
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        keyboardController?.show()
                    }
                },
            label = { Text("Поиск по играм") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { keyboardController?.hide() },
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Найдено игр: ${state.visibleGames.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = viewModel::simulateImport) {
                Text("Импорт через SAF")
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
                    text = if (state.query.isNotBlank()) {
                        "По вашему запросу ничего не найдено."
                    } else if (state.selectedSource == LibrarySource.BuiltIn) {
                        "Встроенные игры не найдены."
                    } else {
                        "Пока нет импортированных ROM."
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            AssistChip(onClick = {}, label = { Text(card.saveBadge) })
        }
    }
}

data class GameCardModel(
    val id: RomId,
    val title: String,
    val saveBadge: String,
)

enum class LibrarySource {
    BuiltIn,
    MyRoms,
}

data class LibraryUiState(
    val query: String = "",
    val selectedSource: LibrarySource = LibrarySource.BuiltIn,
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
        dependencies.userRomRepository.importFromSaf(
            android.net.Uri.parse("content://demo/imported_rom_${System.currentTimeMillis()}.nes"),
        )
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
            .filter { entry ->
                current.query.isBlank() || entry.matchesSearchQuery(current.query)
            }
            .map { entry ->
                val saveCount = dependencies.saveStateRepository.getSaveStates(entry.id).size
                GameCardModel(
                    id = entry.id,
                    title = entry.metadata.title,
                    saveBadge = "Сохранений: $saveCount",
                )
            }

        mutableState.value = current.copy(
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

private fun com.dendy.core.model.RomEntry.matchesSearchQuery(query: String): Boolean {
    val normalizedQuery = query.normalizeSearchToken()
    if (normalizedQuery.isBlank()) {
        return true
    }

    return sequenceOf(
        metadata.title,
        fileName,
        location.displayName,
    ).any { candidate ->
        candidate.normalizeSearchToken().contains(normalizedQuery)
    }
}

private fun String.normalizeSearchToken(): String {
    return lowercase()
        .replace(Regex("[._\\-\\[\\]()]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
