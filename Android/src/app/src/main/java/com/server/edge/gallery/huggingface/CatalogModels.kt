/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.server.edge.gallery.huggingface

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URLEncoder

/** File extensions the on-device engine in this app can load. */
const val LITERT_LM_EXTENSION = ".litertlm"
const val MEDIAPIPE_TASK_EXTENSION = ".task"

private const val HF_API = "https://huggingface.co/api/models"
private const val PAGE_LIMIT = 25

enum class ModelArchitecture {
  TEXT,
  VISION,
  AUDIO,
  EMBEDDING,
  IMAGE_GENERATION,
  OTHER,
}

data class CatalogFile(val name: String, val sizeBytes: Long? = null)

data class CatalogModel(
  val id: String,
  val author: String = "",
  val pipeline: String? = null,
  val tags: List<String> = emptyList(),
  val downloads: Long = 0L,
  val likes: Long = 0L,
  val files: List<CatalogFile> = emptyList(),
  val architecture: ModelArchitecture = ModelArchitecture.OTHER,
  /** Set when the repo is an on-device image pipeline this engine cannot execute. */
  val runtimeExclusion: String? = null,
)

data class DeviceProfile(
  /** Null when RAM could not be read. Never invent a number. */
  val ramBytes: Long?,
  /** Null when the chip model was blank. */
  val chipLabel: String?,
  /** Lowercase SOC, board, and hardware strings used for vendor matching. Empty if none. */
  val chipHaystack: String = "",
  /** Null when no GPU renderer could be read. */
  val gpuLabel: String?,
  /** Null when no NPU device node was found. That is "not detected", not a guessed "0". */
  val npuLabel: String?,
)

data class FileFit(val file: CatalogFile, val exclusions: List<String>) {
  val fits: Boolean
    get() = exclusions.isEmpty()
}

data class ModelFit(val model: CatalogModel, val fileFits: List<FileFit>) {
  val fittingFiles: List<CatalogFile>
    get() = fileFits.filter { it.fits }.map { it.file }

  val runnableOnDevice: Boolean
    get() = model.runtimeExclusion == null && fittingFiles.isNotEmpty()

  fun exclusionSummary(): String {
    model.runtimeExclusion?.let {
      return it
    }
    return fileFits
      .filter { !it.fits }
      .joinToString("\n") { fit -> "${fit.file.name}: ${fit.exclusions.joinToString("; ")}" }
  }
}

private val nonAndroidTokens =
  setOf("apple", "metal", "ios", "intel", "amd", "nvidia", "web")

private val npuTokens = setOf("npu", "htp", "edgetpu", "hexagon")

private enum class ChipVendor(val aliases: Set<String>, val label: String) {
  GOOGLE_TENSOR(setOf("tensor", "zuma", "gs101", "gs201", "laguna"), "Google Tensor"),
  QUALCOMM(setOf("snapdragon", "qualcomm", "qcom"), "Qualcomm"),
  MEDIATEK(setOf("dimensity", "mediatek", "mtk"), "MediaTek"),
  SAMSUNG(setOf("exynos"), "Samsung Exynos"),
}

/** A weight file this app's LiteRT-LM engine or MediaPipe task import path can open. */
fun isLoadableModelFile(filename: String): Boolean {
  val lower = filename.lowercase()
  val extensionOk = lower.endsWith(LITERT_LM_EXTENSION) || lower.endsWith(MEDIAPIPE_TASK_EXTENSION)
  if (!extensionOk) return false
  if (lower.contains("-web") || lower.contains("_web")) return false
  return true
}

fun loadableFiles(model: CatalogModel): List<CatalogFile> =
  model.files.filter { isLoadableModelFile(it.name) }

fun isOnDeviceImagePipeline(model: CatalogModel): Boolean {
  val pipeline = model.pipeline?.lowercase().orEmpty()
  if (pipeline != "text-to-image" && pipeline != "image-to-image") return false
  val tags = model.tags.map { it.lowercase() }
  val onDevice = "litert" in tags || "tflite" in tags || "on-device" in tags
  if (!onDevice) return false
  if ("diffusers" in tags && "litert" !in tags && "tflite" !in tags) return false
  return model.files.any { it.name.endsWith(".tflite", ignoreCase = true) }
}

const val IMAGE_PIPELINE_EXCLUSION =
  "This repository is a multi-file LiteRT image pipeline (.tflite graphs such as a diffusion transformer and VAE). This app executes LiteRT-LM (.litertlm) and MediaPipe task (.task) files, and does not ship a CompiledModel image runtime, so the pipeline cannot be loaded."

fun architectureOf(model: CatalogModel): ModelArchitecture {
  val pipeline = model.pipeline?.lowercase().orEmpty()
  val tags = model.tags.map { it.lowercase() }
  return when {
    pipeline == "text-to-image" || pipeline == "image-to-image" -> ModelArchitecture.IMAGE_GENERATION
    pipeline == "image-text-to-text" || "image-text-to-text" in tags -> ModelArchitecture.VISION
    pipeline == "automatic-speech-recognition" ||
      pipeline == "audio-to-audio" ||
      "automatic-speech-recognition" in tags ||
      "asr" in tags -> ModelArchitecture.AUDIO
    pipeline == "feature-extraction" || "feature-extraction" in tags -> ModelArchitecture.EMBEDDING
    pipeline == "text-generation" || pipeline == "conversational" || "text-generation" in tags ->
      ModelArchitecture.TEXT
    "vision" in tags || tags.any { it.contains("image") } -> ModelArchitecture.VISION
    else -> ModelArchitecture.OTHER
  }
}

/**
 * Keep a catalog row only when this app can load a file in it, or when it is a bounded
 * on-device image pipeline (those stay visible so the exclusion reason can be shown).
 */
fun acceptForDiscovery(model: CatalogModel): CatalogModel? {
  val runnable = loadableFiles(model)
  val imagePipeline = isOnDeviceImagePipeline(model)
  if (runnable.isEmpty() && !imagePipeline) return null
  val architecture = architectureOf(model)
  val runtimeExclusion =
    if (runnable.isEmpty() && imagePipeline) IMAGE_PIPELINE_EXCLUSION else null
  return model.copy(
    architecture = if (imagePipeline && runnable.isEmpty()) ModelArchitecture.IMAGE_GENERATION else architecture,
    files = if (runnable.isNotEmpty()) runnable else model.files.filter { it.name.endsWith(".tflite", true) },
    runtimeExclusion = runtimeExclusion,
  )
}

fun discoveryRequestUrls(userQuery: String = ""): List<String> {
  val trimmed = userQuery.trim()
  val specs =
    mutableListOf(
      listOf("filter" to "litert-lm"),
      listOf("filter" to "litert"),
      listOf("filter" to "litert", "filter" to "text-to-image"),
    )
  if (trimmed.isEmpty()) {
    specs.add(listOf("search" to "litertlm"))
  }
  return specs.map { pairs ->
    val params = pairs.toMutableList()
    params += "sort" to "downloads"
    params += "direction" to "-1"
    params += "limit" to PAGE_LIMIT.toString()
    params += "expand" to "siblings"
    params += "expand" to "tags"
    if (trimmed.isNotEmpty()) {
      params += "search" to URLEncoder.encode(trimmed, "UTF-8")
    }
    val query = params.joinToString("&") { (key, value) -> "$key=$value" }
    "$HF_API?$query"
  }
}

fun parseModelList(json: String): List<CatalogModel> {
  val element =
    try {
      JsonParser.parseString(json)
    } catch (_: Exception) {
      return emptyList()
    }
  if (!element.isJsonArray) return emptyList()
  return element.asJsonArray.mapNotNull { item ->
    if (!item.isJsonObject) null else parseModelObject(item.asJsonObject)
  }
}

fun parseModelObject(json: JsonObject): CatalogModel? {
  val id = json.stringOrNull("id") ?: return null
  val files =
    json.getAsJsonArray("siblings")
      ?.mapNotNull { sibling ->
        if (!sibling.isJsonObject) return@mapNotNull null
        val obj = sibling.asJsonObject
        val name = obj.stringOrNull("rfilename") ?: return@mapNotNull null
        CatalogFile(name = name, sizeBytes = siblingSize(obj))
      }
      .orEmpty()
  val tags =
    json.getAsJsonArray("tags")?.mapNotNull { element ->
      if (element.isJsonNull) null else element.asString
    }.orEmpty()
  return CatalogModel(
    id = id,
    author = json.stringOrNull("author").orEmpty(),
    pipeline = json.stringOrNull("pipeline_tag"),
    tags = tags,
    downloads = json.longOrNull("downloads") ?: 0L,
    likes = json.longOrNull("likes") ?: 0L,
    files = files,
  )
}

fun mergeModelLists(pages: List<List<CatalogModel>>): List<CatalogModel> {
  val byId = linkedMapOf<String, CatalogModel>()
  for (page in pages) {
    for (model in page) {
      val accepted = acceptForDiscovery(model) ?: continue
      val existing = byId[accepted.id]
      if (existing == null) {
        byId[accepted.id] = accepted
      } else {
        val files = (existing.files + accepted.files).distinctBy { it.name }
        val runnable = files.any { isLoadableModelFile(it.name) }
        byId[accepted.id] =
          existing.copy(
            files = if (runnable) files.filter { isLoadableModelFile(it.name) } else files,
            downloads = maxOf(existing.downloads, accepted.downloads),
            pipeline = existing.pipeline ?: accepted.pipeline,
            tags = (existing.tags + accepted.tags).distinct(),
            runtimeExclusion = if (runnable) null else existing.runtimeExclusion ?: accepted.runtimeExclusion,
            architecture = if (runnable) architectureOf(existing.copy(pipeline = existing.pipeline ?: accepted.pipeline)) else existing.architecture,
          )
      }
    }
  }
  return byId.values.sortedByDescending { it.downloads }
}

fun evaluateModel(model: CatalogModel, device: DeviceProfile): ModelFit {
  if (model.runtimeExclusion != null) {
    return ModelFit(model = model, fileFits = emptyList())
  }
  val fits = loadableFiles(model).map { file -> FileFit(file, exclusionsFor(file, device)) }
  return ModelFit(model = model, fileFits = fits)
}

fun exclusionsFor(file: CatalogFile, device: DeviceProfile): List<String> {
  val reasons = mutableListOf<String>()
  val tokens = tokenize(file.name)

  if (tokens.any { it in nonAndroidTokens }) {
    reasons +=
      "This file targets a non-Android runtime (${tokens.first { it in nonAndroidTokens }})."
  }

  val fileVendor = ChipVendor.entries.firstOrNull { vendor -> tokens.any { it in vendor.aliases } }
  if (fileVendor != null) {
    if (device.chipLabel == null || device.chipHaystack.isBlank()) {
      reasons +=
        "This file targets ${fileVendor.label}, and this phone's chip could not be detected."
    } else if (fileVendor.aliases.none { device.chipHaystack.contains(it) }) {
      reasons +=
        "This file targets ${fileVendor.label}, but this phone reports ${device.chipLabel}."
    }
  }

  if (tokens.any { it in npuTokens } && device.npuLabel == null) {
    reasons +=
      "This file targets an NPU (${tokens.first { it in npuTokens }}), and an NPU could not be detected on this phone."
  }

  val size = file.sizeBytes
  val ram = device.ramBytes
  if (size != null && size > 0L && ram != null && ram > 0L && size > ram / 2) {
    reasons +=
      "This file is ${formatBytes(size)}. This phone reports ${formatBytes(ram)} of RAM, and the runtime needs the file to stay under half of that."
  }

  return reasons
}

fun formatBytes(bytes: Long): String {
  val gb = bytes / (1024.0 * 1024.0 * 1024.0)
  if (gb >= 1.0) return String.format("%.1f GB", gb)
  val mb = bytes / (1024.0 * 1024.0)
  return String.format("%.0f MB", mb)
}

fun downloadUrl(modelId: String, fileName: String): String {
  val encoded = fileName.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
  return "https://huggingface.co/$modelId/resolve/main/$encoded?download=true"
}

fun architectureLabel(architecture: ModelArchitecture): String =
  when (architecture) {
    ModelArchitecture.TEXT -> "Text"
    ModelArchitecture.VISION -> "Vision"
    ModelArchitecture.AUDIO -> "Audio"
    ModelArchitecture.EMBEDDING -> "Embedding"
    ModelArchitecture.IMAGE_GENERATION -> "Image"
    ModelArchitecture.OTHER -> "Other"
  }

private fun tokenize(filename: String): Set<String> {
  val stem = filename.substringAfterLast("/").lowercase().substringBeforeLast(".")
  return stem.split('_', '-', '.', ' ').filter { it.isNotBlank() }.toSet()
}

private fun siblingSize(obj: JsonObject): Long? {
  obj.longOrNull("size")?.let {
    return it
  }
  val lfs = obj.get("lfs")
  if (lfs != null && lfs.isJsonObject) {
    return lfs.asJsonObject.longOrNull("size")
  }
  return null
}

private fun JsonObject.stringOrNull(name: String): String? {
  if (!has(name) || get(name).isJsonNull) return null
  return get(name).asString
}

private fun JsonObject.longOrNull(name: String): Long? {
  if (!has(name) || get(name).isJsonNull) return null
  return try {
    get(name).asLong
  } catch (_: Exception) {
    null
  }
}
