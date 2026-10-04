/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.server.edge.gallery.ui.modelmanager

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.server.edge.gallery.data.DataStoreRepository
import com.server.edge.gallery.huggingface.CatalogModel
import com.server.edge.gallery.huggingface.DeviceProbe
import com.server.edge.gallery.huggingface.DeviceProfile
import com.server.edge.gallery.huggingface.HuggingFaceCatalogClient
import com.server.edge.gallery.huggingface.ModelArchitecture
import com.server.edge.gallery.huggingface.ModelFit
import com.server.edge.gallery.huggingface.evaluateModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "AGHfDiscover"

data class HfDiscoverUiState(
  val query: String = "",
  val device: DeviceProfile? = null,
  val models: List<ModelFit> = emptyList(),
  val loading: Boolean = true,
  val error: String? = null,
  val showExcluded: Boolean = false,
  val architecture: ModelArchitecture? = null,
  val selected: CatalogModel? = null,
)

@HiltViewModel
class HfDiscoverViewModel
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val client: HuggingFaceCatalogClient,
  private val dataStoreRepository: DataStoreRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(HfDiscoverUiState())
  val uiState: StateFlow<HfDiscoverUiState> = _uiState.asStateFlow()
  private var searchJob: Job? = null

  init {
    val device = DeviceProbe.read(context)
    _uiState.update { it.copy(device = device) }
    refresh()
  }

  fun onQueryChanged(query: String) {
    _uiState.update { it.copy(query = query) }
    searchJob?.cancel()
    searchJob =
      viewModelScope.launch {
        delay(350.milliseconds)
        load(query)
      }
  }

  fun setShowExcluded(show: Boolean) {
    _uiState.update { it.copy(showExcluded = show) }
  }

  fun setArchitecture(architecture: ModelArchitecture?) {
    _uiState.update { it.copy(architecture = architecture) }
  }

  fun select(model: CatalogModel?) {
    _uiState.update { it.copy(selected = model) }
  }

  fun refresh() {
    searchJob?.cancel()
    searchJob = viewModelScope.launch { load(_uiState.value.query) }
  }

  private suspend fun load(query: String) {
    val device = _uiState.value.device ?: DeviceProbe.read(context)
    _uiState.update { it.copy(loading = true, error = null, device = device) }
    try {
      val token = dataStoreRepository.readAccessTokenData()?.accessToken?.takeIf { it.isNotBlank() }
      val models = client.search(userQuery = query, accessToken = token)
      val fits = models.map { evaluateModel(it, device) }
      _uiState.update { it.copy(models = fits, loading = false, error = null) }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Hugging Face search failed", e)
      _uiState.update { it.copy(loading = false, error = "Could not reach Hugging Face.") }
    }
  }
}

fun HfDiscoverUiState.visibleModels(): List<ModelFit> {
  return models.filter { fit ->
    val architectureMatches = architecture == null || fit.model.architecture == architecture
    if (!architectureMatches) return@filter false
    if (showExcluded) return@filter true
    if (architecture == ModelArchitecture.IMAGE_GENERATION) return@filter true
    fit.runnableOnDevice
  }
}
