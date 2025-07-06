/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.geoai.geoaimobile.ui.llmchat

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.geoai.geoaimobile.common.cleanUpMediapipeTaskErrorMessage
import com.geoai.geoaimobile.data.Accelerator
import com.geoai.geoaimobile.data.ConfigKey
import com.geoai.geoaimobile.data.DEFAULT_MAX_TOKEN
import com.geoai.geoaimobile.data.DEFAULT_TEMPERATURE
import com.geoai.geoaimobile.data.DEFAULT_TOPK
import com.geoai.geoaimobile.data.DEFAULT_TOPP
import com.geoai.geoaimobile.data.MAX_IMAGE_COUNT
import com.geoai.geoaimobile.data.Model
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

private const val TAG = "AGLlmChatModelHelper"

typealias ResultListener = (partialResult: String, done: Boolean) -> Unit

typealias CleanUpListener = () -> Unit

typealias InitializationProgressListener = (backend: String, phase: String, progress: Int, phaseDetail: String) -> Unit

data class LlmModelInstance(val engine: LlmInference, var session: LlmInferenceSession)

object LlmChatModelHelper {
  // Indexed by model name.
  private val cleanUpListeners: MutableMap<String, CleanUpListener> = mutableMapOf()
  
  /**
   * Simulates progress during a long-running operation by gradually updating progress
   */
  private suspend fun <T> simulateProgressDuringOperation(
    backend: String,
    startProgress: Int,
    endProgress: Int,
    baseMessage: String,
    detailMessages: List<String>,
    onProgress: InitializationProgressListener?,
    operation: suspend () -> T
  ): T {
    val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val progressRange = endProgress - startProgress
    val totalSteps = detailMessages.size
    var operationComplete = false
    
    var result: T? = null
    
    // Start the actual operation in background
    val operationJob = progressScope.launch {
      try {
        result = operation()
      } finally {
        operationComplete = true
      }
    }
    
    // Simulate progress updates
    progressScope.launch {
      for (i in detailMessages.indices) {
        if (operationComplete) break
        
        val currentProgress = startProgress + (progressRange * (i + 1) / totalSteps)
        val message = detailMessages[i]
        onProgress?.invoke(backend, baseMessage, currentProgress, message)
        
        // Wait before next update, but break if operation completes
        var waitCount = 0
        while (waitCount < 10 && !operationComplete) { // Check every 300ms for 3 seconds total
          delay(300)
          waitCount++
        }
      }
    }
    
    // Wait for operation to complete
    operationJob.join()
    return result!!
  }

  suspend fun initialize(context: Context, model: Model, onDone: (String) -> Unit, onProgress: InitializationProgressListener? = null) {
    // Prepare options.
    val maxTokens =
      model.getIntConfigValue(key = ConfigKey.MAX_TOKENS, defaultValue = DEFAULT_MAX_TOKEN)
    val topK = model.getIntConfigValue(key = ConfigKey.TOPK, defaultValue = DEFAULT_TOPK)
    val topP = model.getFloatConfigValue(key = ConfigKey.TOPP, defaultValue = DEFAULT_TOPP)
    val temperature =
      model.getFloatConfigValue(key = ConfigKey.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
    val accelerator =
      model.getStringConfigValue(key = ConfigKey.ACCELERATOR, defaultValue = Accelerator.GPU.label)
    Log.d(TAG, "Initializing...")
    val preferredBackend =
      when (accelerator) {
        Accelerator.CPU.label -> LlmInference.Backend.CPU
        Accelerator.GPU.label -> LlmInference.Backend.GPU
        else -> LlmInference.Backend.GPU
      }
    
    // Report backend selection to UI
    val backendName = when (preferredBackend) {
      LlmInference.Backend.CPU -> "CPU"
      LlmInference.Backend.GPU -> "GPU"
      else -> "GPU"
    }
    
    onProgress?.invoke(backendName, "loading", 10, "Loading model weights from storage...")
    val optionsBuilder =
      LlmInference.LlmInferenceOptions.builder()
        .setModelPath(model.getPath(context = context))
        .setMaxTokens(maxTokens)
        .setPreferredBackend(preferredBackend)
        .setMaxNumImages(if (model.llmSupportImage) MAX_IMAGE_COUNT else 0)
    val options = optionsBuilder.build()

    // Create an instance of the LLM Inference task and session.
    try {
      // Phase 1: Create LLM Inference with intermediate progress updates
      onProgress?.invoke(backendName, "creating", 25, "Creating inference engine...")
      
      val llmInference = simulateProgressDuringOperation(
        backend = backendName,
        startProgress = 25,
        endProgress = 30,
        baseMessage = "preparing",
        detailMessages = listOf(
          "Preparing inference engine...",
          "Configuring model parameters...",
          "Validating model architecture...",
          "Initializing inference context..."
        ),
        onProgress = onProgress
      ) {
        withContext(Dispatchers.IO) {
          LlmInference.createFromOptions(context, options)
        }
      }

      // Phase 2: Create session with simulated progress (this is the long operation)
      val sessionDetailMessages = listOf(
        "Optimizing GPU kernels for your device...",
        "Loading model weights to GPU memory...",
        "Preparing neural network layers...",
        "Configuring memory buffers...", 
        "Finalizing GPU optimization..."
      )
      
      val session = simulateProgressDuringOperation(
        backend = backendName,
        startProgress = 30,
        endProgress = 90,
        baseMessage = "optimizing",
        detailMessages = sessionDetailMessages,
        onProgress = onProgress
      ) {
        withContext(Dispatchers.IO) {
          LlmInferenceSession.createFromOptions(
            llmInference,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
              .setTopK(topK)
              .setTopP(topP)
              .setTemperature(temperature)
              .setGraphOptions(
                GraphOptions.builder()
                  .setEnableVisionModality(model.llmSupportImage)
                  .build()
              )
              .build(),
          )
        }
      }
      
      onProgress?.invoke(backendName, "ready", 95, "Finalizing model setup...")
      model.instance = LlmModelInstance(engine = llmInference, session = session)
      onProgress?.invoke(backendName, "completed", 100, "Model ready for inference!")
    } catch (e: Exception) {
      val errorMessage = e.message ?: "Unknown error"
      Log.e(TAG, "Failed to initialize model", e)
      
      // Check for common error patterns
      if (errorMessage.contains("memory", ignoreCase = true) || 
          errorMessage.contains("resource", ignoreCase = true) ||
          errorMessage.contains("allocation", ignoreCase = true)) {
        onDone("Insufficient device resources. Your device may not have enough memory to run this model.")
      } else {
        onDone(cleanUpMediapipeTaskErrorMessage(errorMessage))
      }
      return
    }
    onDone("")
  }

  fun resetSession(model: Model) {
    try {
      Log.d(TAG, "Resetting session for model '${model.name}'")

      val instance = model.instance as LlmModelInstance? ?: return
      val session = instance.session
      session.close()

      val inference = instance.engine
      val topK = model.getIntConfigValue(key = ConfigKey.TOPK, defaultValue = DEFAULT_TOPK)
      val topP = model.getFloatConfigValue(key = ConfigKey.TOPP, defaultValue = DEFAULT_TOPP)
      val temperature =
        model.getFloatConfigValue(key = ConfigKey.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
      val newSession =
        LlmInferenceSession.createFromOptions(
          inference,
          LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(topK)
            .setTopP(topP)
            .setTemperature(temperature)
            .setGraphOptions(
              GraphOptions.builder()
                .setEnableVisionModality(model.llmSupportImage)
                .build()
            )
            .build(),
        )
      instance.session = newSession
      Log.d(TAG, "Resetting done")
    } catch (e: Exception) {
      Log.d(TAG, "Failed to reset session", e)
    }
  }

  fun cleanUp(model: Model) {
    if (model.instance == null) {
      return
    }

    val instance = model.instance as LlmModelInstance

    try {
      instance.session.close()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to close the LLM Inference session: ${e.message}")
    }

    try {
      instance.engine.close()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to close the LLM Inference engine: ${e.message}")
    }

    val onCleanUp = cleanUpListeners.remove(model.name)
    if (onCleanUp != null) {
      onCleanUp()
    }
    model.instance = null
    Log.d(TAG, "Clean up done.")
  }

  fun runInference(
    model: Model,
    input: String,
    resultListener: ResultListener,
    cleanUpListener: CleanUpListener,
    images: List<Bitmap> = listOf(),
    audioClips: List<ByteArray> = listOf(),
  ) {
    val instance = model.instance as LlmModelInstance

    // Set listener.
    if (!cleanUpListeners.containsKey(model.name)) {
      cleanUpListeners[model.name] = cleanUpListener
    }

    // Start async inference.
    //
    // For a model that supports image modality, we need to add the text query chunk before adding
    // image.
    val session = instance.session
    if (input.trim().isNotEmpty()) {
      session.addQueryChunk(input)
    }
    for (image in images) {
      session.addImage(BitmapImageBuilder(image).build())
    }
    for (audioClip in audioClips) {
      // Uncomment when audio is supported.
      // session.addAudio(audioClip)
    }
    val unused = session.generateResponseAsync(resultListener)
  }
}
