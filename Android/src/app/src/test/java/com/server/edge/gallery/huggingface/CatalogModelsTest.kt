package com.server.edge.gallery.huggingface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogModelsTest {
  private val phone =
    DeviceProfile(
      ramBytes = 8L * 1024 * 1024 * 1024,
      chipLabel = "Google Tensor G3",
      chipHaystack = "tensor zuma gs201",
      gpuLabel = "Mali-G715",
      npuLabel = null,
    )

  @Test
  fun discoveryUrlsAreNotLimitedToLitertLmTag() {
    val urls = discoveryRequestUrls()
    assertTrue(urls.any { it.contains("filter=litert-lm") })
    assertTrue(urls.any { it.contains("filter=litert&") && !it.contains("text-to-image") })
    assertTrue(urls.any { it.contains("filter=text-to-image") })
    assertTrue(urls.any { it.contains("search=litertlm") })
    assertTrue(urls.all { it.contains("limit=25") })
  }

  @Test
  fun keepsLitertLmAndTaskFilesWithoutRequiringTheTag() {
    val qwen =
      CatalogModel(
        id = "someone/qwen-litert",
        pipeline = "text-generation",
        tags = listOf("litert", "qwen"),
        files =
          listOf(
            CatalogFile("weights.safetensors"),
            CatalogFile("qwen2.5-1.5b-q8.litertlm", 1_000_000_000L),
            CatalogFile("qwen-web.litertlm"),
          ),
      )
    val accepted = acceptForDiscovery(qwen)
    assertEquals(listOf("qwen2.5-1.5b-q8.litertlm"), accepted?.files?.map { it.name })
    assertEquals(ModelArchitecture.TEXT, accepted?.architecture)
    assertNull(accepted?.runtimeExclusion)

    val task =
      acceptForDiscovery(
        CatalogModel(
          id = "someone/gemma.task",
          tags = listOf("mediapipe"),
          files = listOf(CatalogFile("gemma-q4.task", 800_000_000L)),
        )
      )
    assertEquals(ModelArchitecture.OTHER, task?.architecture)
    assertEquals(1, task?.files?.size)
  }

  @Test
  fun dropsReposThatAreNotARunnableFormat() {
    val safetensors =
      CatalogModel(
        id = "org/plain-transformer",
        pipeline = "text-generation",
        tags = listOf("transformers"),
        files = listOf(CatalogFile("model.safetensors")),
      )
    assertNull(acceptForDiscovery(safetensors))
  }

  @Test
  fun imagePipelinesAreDiscoveredAndMarkedUnrunnable() {
    val image =
      CatalogModel(
        id = "litert-community/Z-Image-Turbo-LiteRT",
        pipeline = "text-to-image",
        tags = listOf("litert", "tflite", "on-device", "text-to-image"),
        files = listOf(CatalogFile("zvae.tflite"), CatalogFile("README.md")),
      )
    val accepted = acceptForDiscovery(image)
    assertEquals(ModelArchitecture.IMAGE_GENERATION, accepted?.architecture)
    assertTrue(accepted?.runtimeExclusion?.contains("CompiledModel") == true)
    val fit = evaluateModel(accepted!!, phone)
    assertFalse(fit.runnableOnDevice)
    assertTrue(fit.exclusionSummary().contains("image pipeline"))
  }

  @Test
  fun homeworkImageReposWithoutLiteRtTagsAreNotDumped() {
    val homework =
      CatalogModel(
        id = "student/ml1-task-image-generation",
        pipeline = "text-to-image",
        tags = listOf("diffusers"),
        files = listOf(CatalogFile("model.safetensors")),
      )
    assertNull(acceptForDiscovery(homework))
  }

  @Test
  fun ramChipAndNpuExclusionsNameTheReason() {
    val huge =
      CatalogFile("gemma-generic.litertlm", 6L * 1024 * 1024 * 1024)
    val snapdragon = CatalogFile("model_qualcomm.litertlm", 1_000_000_000L)
    val npu = CatalogFile("model-htp.litertlm", 1_000_000_000L)
    val web = CatalogFile("model_intel.litertlm", 100_000_000L)

    assertTrue(exclusionsFor(huge, phone).any { it.contains("RAM") })
    assertTrue(exclusionsFor(snapdragon, phone).any { it.contains("Qualcomm") && it.contains("Tensor") })
    assertTrue(exclusionsFor(npu, phone).any { it.contains("NPU could not be detected") })
    assertTrue(exclusionsFor(web, phone).any { it.contains("non-Android") })
  }

  @Test
  fun missingRamDoesNotInventANumberOrExclude() {
    val unknownRam = phone.copy(ramBytes = null)
    val file = CatalogFile("model.litertlm", 6L * 1024 * 1024 * 1024)
    assertTrue(exclusionsFor(file, unknownRam).none { it.contains("RAM") || it.contains("GB") })
  }

  @Test
  fun missingChipDoesNotGuessAndExplainsVendorFiles() {
    val unknownChip = phone.copy(chipLabel = null, chipHaystack = "")
    val generic = exclusionsFor(CatalogFile("model.litertlm", 100L), unknownChip)
    assertTrue(generic.isEmpty())
    val targeted = exclusionsFor(CatalogFile("gemma_tensor.litertlm", 100L), unknownChip)
    assertTrue(targeted.single().contains("could not be detected"))
  }

  @Test
  fun matchingTensorFileFitsAndOverrideStillSeesExcludedOnes() {
    val model =
      acceptForDiscovery(
        CatalogModel(
          id = "litert-community/gemma",
          pipeline = "image-text-to-text",
          tags = listOf("litert-lm"),
          files =
            listOf(
              CatalogFile("gemma.litertlm", 2L * 1024 * 1024 * 1024),
              CatalogFile("gemma_qualcomm.litertlm", 2L * 1024 * 1024 * 1024),
            ),
        )
      )!!
    val fit = evaluateModel(model, phone)
    assertTrue(fit.runnableOnDevice)
    assertEquals(listOf("gemma.litertlm"), fit.fittingFiles.map { it.name })
    assertEquals(ModelArchitecture.VISION, model.architecture)
    assertTrue(fit.fileFits.first { it.file.name.contains("qualcomm") }.exclusions.isNotEmpty())
  }

  @Test
  fun parsesSiblingsAndDropsUnusableJson() {
    val json =
      """
      [
        {
          "id": "org/phi",
          "pipeline_tag": "text-generation",
          "tags": ["litert"],
          "downloads": 12,
          "siblings": [
            {"rfilename": "phi.litertlm", "lfs": {"size": 42}},
            {"rfilename": "README.md"}
          ]
        }
      ]
      """
        .trimIndent()
    val parsed = parseModelList(json)
    assertEquals(1, parsed.size)
    assertEquals(42L, parsed[0].files.first { it.name == "phi.litertlm" }.sizeBytes)
    assertTrue(parseModelList("not-json").isEmpty())
  }
}
