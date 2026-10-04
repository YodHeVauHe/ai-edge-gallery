/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.server.edge.gallery.ui.modelmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.server.edge.gallery.common.isPixel10
import com.server.edge.gallery.data.Accelerator
import com.server.edge.gallery.data.DEFAULT_MAX_TOKEN
import com.server.edge.gallery.data.DEFAULT_TEMPERATURE
import com.server.edge.gallery.data.DEFAULT_TOPK
import com.server.edge.gallery.data.DEFAULT_TOPP
import com.server.edge.gallery.huggingface.CatalogFile
import com.server.edge.gallery.huggingface.DeviceProfile
import com.server.edge.gallery.huggingface.ModelArchitecture
import com.server.edge.gallery.huggingface.ModelFit
import com.server.edge.gallery.huggingface.architectureLabel
import com.server.edge.gallery.huggingface.downloadUrl
import com.server.edge.gallery.huggingface.formatBytes
import com.server.edge.gallery.huggingface.isLoadableModelFile
import com.server.edge.gallery.proto.ImportedModel
import com.server.edge.gallery.proto.LlmConfig

fun importedModelFromCatalog(
  modelId: String,
  file: CatalogFile,
  architecture: ModelArchitecture,
): ImportedModel {
  val lower = file.name.lowercase()
  val accelerators =
    if (isPixel10()) {
      mutableListOf(Accelerator.CPU.label)
    } else {
      mutableListOf(Accelerator.GPU.label, Accelerator.CPU.label)
    }
  if (!isPixel10() && (lower.contains("npu") || lower.contains("htp"))) {
    accelerators.add(Accelerator.NPU.label)
  }
  val displayName = "${modelId.replace('/', '_')}__${file.name.replace('/', '_')}"
  return ImportedModel.newBuilder()
    .setFileName(displayName)
    .setFileSize(file.sizeBytes ?: 0L)
    .setDownloadUrl(downloadUrl(modelId, file.name))
    .setLlmConfig(
      LlmConfig.newBuilder()
        .addAllCompatibleAccelerators(accelerators)
        .setDefaultMaxTokens(DEFAULT_MAX_TOKEN)
        .setDefaultTopk(DEFAULT_TOPK)
        .setDefaultTopp(DEFAULT_TOPP)
        .setDefaultTemperature(DEFAULT_TEMPERATURE)
        .setSupportImage(architecture == ModelArchitecture.VISION)
        .setSupportAudio(architecture == ModelArchitecture.AUDIO)
        .build()
    )
    .build()
}

private val ARCHITECTURES =
  listOf(
    null to "All",
    ModelArchitecture.TEXT to "Text",
    ModelArchitecture.VISION to "Vision",
    ModelArchitecture.AUDIO to "Audio",
    ModelArchitecture.EMBEDDING to "Embedding",
    ModelArchitecture.IMAGE_GENERATION to "Image",
    ModelArchitecture.OTHER to "Other",
  )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HfDiscoverScreen(
  onDismiss: () -> Unit,
  onImportFile: (modelId: String, file: CatalogFile, architecture: ModelArchitecture) -> Unit,
  viewModel: HfDiscoverViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsState()
  val selected = uiState.selected
  val selectedFit = uiState.models.firstOrNull { it.model.id == selected?.id }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      topBar = {
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .background(MaterialTheme.colorScheme.surface)
              .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                "HUGGING FACE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                "Models this phone can run",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
              )
            }
            IconButton(onClick = onDismiss) {
              Icon(Icons.Rounded.Close, contentDescription = "Close")
            }
          }
          OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("Search runnable models") },
          )
        }
      },
    ) { padding ->
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item { DeviceSpecCard(uiState.device) }
        item {
          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilterChip(
              selected = !uiState.showExcluded,
              onClick = { viewModel.setShowExcluded(false) },
              label = { Text("Fits this phone") },
            )
            FilterChip(
              selected = uiState.showExcluded,
              onClick = { viewModel.setShowExcluded(true) },
              label = { Text("Show excluded") },
            )
            ARCHITECTURES.forEach { (architecture, label) ->
              FilterChip(
                selected = uiState.architecture == architecture,
                onClick = { viewModel.setArchitecture(architecture) },
                label = { Text(label) },
              )
            }
          }
        }
        if (uiState.loading) {
          item {
            Row(
              modifier = Modifier.fillMaxWidth().padding(24.dp),
              horizontalArrangement = Arrangement.Center,
            ) {
              CircularProgressIndicator()
            }
          }
        }
        uiState.error?.let { message ->
          item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        val visible = uiState.visibleModels()
        if (!uiState.loading && visible.isEmpty()) {
          item {
            Text(
              if (uiState.showExcluded) {
                "No Hugging Face models matched the formats this app can open."
              } else {
                "Nothing in this search fits the phone. Turn on Show excluded to see why models were hidden."
              },
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
        items(visible, key = { it.model.id }) { fit ->
          ModelDiscoverCard(fit = fit, onClick = { viewModel.select(fit.model) })
        }
      }
    }
  }

  if (selected != null && selectedFit != null) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      onDismissRequest = { viewModel.select(null) },
      sheetState = sheetState,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(selected.id, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
          architectureLabel(selected.architecture),
          color = MaterialTheme.colorScheme.primary,
          style = MaterialTheme.typography.labelLarge,
        )
        selected.runtimeExclusion?.let { reason ->
          Text(reason, style = MaterialTheme.typography.bodyMedium)
        }
        val files =
          if (selected.runtimeExclusion != null) {
            selected.files
          } else {
            selectedFit.fileFits.map { it.file }
          }
        files.forEach { file ->
          val reasons = selectedFit.fileFits.firstOrNull { it.file.name == file.name }?.exclusions.orEmpty()
          val canImport = selected.runtimeExclusion == null && isLoadableModelFile(file.name)
          val allowedNow = canImport && (reasons.isEmpty() || uiState.showExcluded)
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            file.sizeBytes?.let { Text(formatBytes(it), style = MaterialTheme.typography.bodySmall) }
            if (reasons.isNotEmpty()) {
              Text(reasons.joinToString("\n"), color = MaterialTheme.colorScheme.tertiary)
            }
            if (allowedNow) {
              Button(
                onClick = {
                  onImportFile(selected.id, file, selected.architecture)
                  viewModel.select(null)
                }
              ) {
                Text(if (reasons.isEmpty()) "Use this file" else "Use anyway")
              }
            }
          }
        }
        TextButton(onClick = { viewModel.select(null) }) { Text("Close") }
      }
    }
  }
}

@Composable
private fun DeviceSpecCard(device: DeviceProfile?) {
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp))
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text("THIS PHONE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    if (device == null) {
      Text("Reading device specs…")
    } else {
      SpecLine("RAM", device.ramBytes?.let { formatBytes(it) })
      SpecLine("Chip", device.chipLabel)
      SpecLine("GPU", device.gpuLabel)
      SpecLine("NPU", device.npuLabel)
    }
    Text(
      "Search covers LiteRT-LM and MediaPipe task files, including text, vision, audio, and other architectures. Image-generation LiteRT bundles are listed under Image with the reason they cannot be loaded.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun SpecLine(label: String, value: String?) {
  Text(
    "$label: ${value ?: "Could not be detected"}",
    style = MaterialTheme.typography.bodyMedium,
    color = if (value == null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
  )
}

@Composable
private fun ModelDiscoverCard(fit: ModelFit, onClick: () -> Unit) {
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(fit.model.id.substringAfterLast("/"), style = MaterialTheme.typography.titleMedium)
    Text(fit.model.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
      architectureLabel(fit.model.architecture),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.secondary,
    )
    if (!fit.runnableOnDevice) {
      Text(fit.exclusionSummary(), style = MaterialTheme.typography.bodySmall)
    } else {
      val size = fit.fittingFiles.mapNotNull { it.sizeBytes }.minOrNull()
      Text(
        buildString {
          append("${fit.fittingFiles.size} file")
          if (fit.fittingFiles.size != 1) append("s")
          append(" that fit")
          if (size != null) append(" · from ${formatBytes(size)}")
        },
        style = MaterialTheme.typography.bodySmall,
      )
    }
    TextButton(onClick = onClick) { Text(if (fit.runnableOnDevice) "Choose a file" else "Why excluded") }
  }
}
