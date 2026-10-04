/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.server.edge.gallery.huggingface

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private const val TAG = "AGHfCatalog"

open class HuggingFaceCatalogClient @Inject constructor() {
  open suspend fun search(userQuery: String = "", accessToken: String? = null): List<CatalogModel> =
    withContext(Dispatchers.IO) {
      val pages =
        discoveryRequestUrls(userQuery).map { url ->
          val body = get(url, accessToken)
          if (body.isNullOrBlank()) emptyList() else parseModelList(body)
        }
      val merged = mergeModelLists(pages)
      enrichSizes(merged, accessToken)
    }

  private suspend fun enrichSizes(
    models: List<CatalogModel>,
    accessToken: String?,
  ): List<CatalogModel> {
    val needsSize = models.filter { model -> model.files.any { it.sizeBytes == null || it.sizeBytes <= 0L } }
    if (needsSize.isEmpty()) return models
    val gate = Semaphore(4)
    val sized =
      coroutineScope {
        needsSize
          .take(24)
          .map { model ->
            async {
              gate.withPermit {
                val body = get("https://huggingface.co/api/models/${model.id}?blobs=true", accessToken)
                if (body.isNullOrBlank()) {
                  model
                } else {
                  try {
                    val element = com.google.gson.JsonParser.parseString(body)
                    if (element.isJsonObject) parseModelObject(element.asJsonObject) ?: model else model
                  } catch (_: Exception) {
                    model
                  }
                }
              }
            }
          }
          .awaitAll()
      }
    val byId = sized.associateBy { it.id }
    return models.map { model ->
      val detailed = byId[model.id] ?: return@map model
      val sizes = detailed.files.associate { it.name to it.sizeBytes }
      model.copy(
        files =
          model.files.map { file ->
            val size = sizes[file.name]
            if (size != null && size > 0L) file.copy(sizeBytes = size) else file
          }
      )
    }
  }

  private fun get(urlString: String, accessToken: String?): String? {
    return try {
      val connection = URL(urlString).openConnection() as HttpURLConnection
      connection.requestMethod = "GET"
      connection.setRequestProperty("User-Agent", "ServerEdgeGallery/1.0 (Android)")
      connection.connectTimeout = 20000
      connection.readTimeout = 20000
      if (!accessToken.isNullOrBlank()) {
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
      }
      connection.connect()
      if (connection.responseCode != HttpURLConnection.HTTP_OK) {
        Log.e(TAG, "HF HTTP ${connection.responseCode} for $urlString")
        null
      } else {
        connection.inputStream.bufferedReader().use { it.readText() }
      }
    } catch (e: Exception) {
      Log.e(TAG, "HF request failed for $urlString", e)
      null
    }
  }
}
