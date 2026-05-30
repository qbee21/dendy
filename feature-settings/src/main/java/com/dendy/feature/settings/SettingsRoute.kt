package com.dendy.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dendy.core.data.SettingsRepository
import com.dendy.core.model.DendySettings
import com.dendy.core.model.PerformancePreset
import com.dendy.core.model.VideoFilter
import kotlinx.coroutines.flow.MutableStateFlow

const val SETTINGS_ROUTE = "settings"

interface SettingsDependencies {
    val settingsRepository: SettingsRepository
}

@Composable
fun SettingsRoute(
    dependencies: SettingsDependencies,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(dependencies),
    )
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Video, audio, input layout and performance presets already have dedicated state objects.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Audio enabled")
                    Switch(checked = state.audioEnabled, onCheckedChange = viewModel::toggleAudio)
                }

                Text("Video filter")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.videoFilter == filter,
                            onClick = { viewModel.selectFilter(filter) },
                            label = { Text(filter.name.replace('_', ' ')) },
                        )
                    }
                }

                Text("Performance preset")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PerformancePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = state.performancePreset == preset,
                            onClick = { viewModel.selectPreset(preset) },
                            label = { Text(preset.name) },
                        )
                    }
                }
            }
        }
    }
}

class SettingsViewModel(
    private val dependencies: SettingsDependencies,
) : ViewModel() {
    private val mutableState = MutableStateFlow(dependencies.settingsRepository.read())
    val state = mutableState

    fun toggleAudio(enabled: Boolean) {
        persist(state.value.copy(audioEnabled = enabled))
    }

    fun selectFilter(filter: VideoFilter) {
        persist(state.value.copy(videoFilter = filter))
    }

    fun selectPreset(preset: PerformancePreset) {
        persist(state.value.copy(performancePreset = preset))
    }

    private fun persist(settings: DendySettings) {
        dependencies.settingsRepository.write(settings)
        mutableState.value = settings
    }

    companion object {
        fun factory(dependencies: SettingsDependencies): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(dependencies) as T
                }
            }
        }
    }
}
