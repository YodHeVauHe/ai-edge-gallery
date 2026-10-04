/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.server.edge.gallery.huggingface

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import java.io.File

/**
 * Reads only values the platform actually reports. Missing values stay null so the UI can say
 * the spec could not be detected.
 */
object DeviceProbe {
  fun read(context: Context): DeviceProfile {
    val ram = readRam(context)
    val chip = readChip()
    return DeviceProfile(
      ramBytes = ram,
      chipLabel = chip.first,
      chipHaystack = chip.second,
      gpuLabel = readGpu(),
      npuLabel = readNpu(),
    )
  }

  private fun readRam(context: Context): Long? {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    val bytes =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && info.advertisedMem > 0L) {
        info.advertisedMem
      } else {
        info.totalMem
      }
    return bytes.takeIf { it > 0L }
  }

  private fun readChip(): Pair<String?, String> {
    val model = Build.SOC_MODEL?.trim().orEmpty()
    val manufacturer = Build.SOC_MANUFACTURER?.trim().orEmpty()
    val hardware = Build.HARDWARE?.trim().orEmpty()
    val board = Build.BOARD?.trim().orEmpty()
    val haystack = listOf(model, manufacturer, hardware, board).joinToString(" ").lowercase()
    val label =
      when {
        model.isNotEmpty() && manufacturer.isNotEmpty() -> "$manufacturer $model"
        model.isNotEmpty() -> model
        else -> null
      }
    return label to haystack
  }

  private fun readGpu(): String? {
    return try {
      val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
      if (display == EGL14.EGL_NO_DISPLAY) return null
      val version = IntArray(2)
      if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
      val configAttribs =
        intArrayOf(
          EGL14.EGL_RENDERABLE_TYPE,
          EGL14.EGL_OPENGL_ES2_BIT,
          EGL14.EGL_SURFACE_TYPE,
          EGL14.EGL_PBUFFER_BIT,
          EGL14.EGL_RED_SIZE,
          8,
          EGL14.EGL_GREEN_SIZE,
          8,
          EGL14.EGL_BLUE_SIZE,
          8,
          EGL14.EGL_NONE,
        )
      val configs = arrayOfNulls<EGLConfig>(1)
      val count = IntArray(1)
      val chosen =
        EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, count, 0) && count[0] > 0
      if (!chosen || configs[0] == null) {
        EGL14.eglTerminate(display)
        return null
      }
      val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
      val eglContext =
        EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
      val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
      val surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttribs, 0)
      if (
        eglContext == null ||
          eglContext == EGL14.EGL_NO_CONTEXT ||
          surface == null ||
          surface == EGL14.EGL_NO_SURFACE
      ) {
        EGL14.eglTerminate(display)
        return null
      }
      EGL14.eglMakeCurrent(display, surface, surface, eglContext)
      val renderer = GLES20.glGetString(GLES20.GL_RENDERER)?.takeIf { it.isNotBlank() }
      EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
      EGL14.eglDestroySurface(display, surface)
      EGL14.eglDestroyContext(display, eglContext)
      EGL14.eglTerminate(display)
      renderer
    } catch (_: Throwable) {
      null
    }
  }

  /**
   * NPU names are reported only when a known accelerator node exists. A missing node is not
   * turned into "no NPU" or a fake count.
   */
  private fun readNpu(): String? {
    val nodes =
      listOf(
        "/dev/edgetpu" to "Edge TPU",
        "/dev/apex_0" to "Google Edge TPU",
        "/dev/fastrpc-cdsp" to "Qualcomm Hexagon",
        "/dev/synap" to "Synaptics NPU",
        "/dev/apusys" to "MediaTek APU",
      )
    val found = nodes.filter { (path, _) -> File(path).exists() }.map { it.second }
    return found.takeIf { it.isNotEmpty() }?.joinToString()
  }
}
