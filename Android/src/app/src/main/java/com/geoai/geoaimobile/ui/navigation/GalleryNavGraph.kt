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

package com.geoai.geoaimobile.ui.navigation

import android.util.Log
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.geoai.geoaimobile.data.Model
import com.geoai.geoaimobile.data.TASK_LLM_ASK_AUDIO
import com.geoai.geoaimobile.data.TASK_LLM_ASK_IMAGE
import com.geoai.geoaimobile.data.TASK_LLM_CHAT
import com.geoai.geoaimobile.data.TASK_LLM_PROMPT_LAB
import com.geoai.geoaimobile.data.Task
import com.geoai.geoaimobile.data.TaskType
import com.geoai.geoaimobile.data.getModelByName
import com.geoai.geoaimobile.ui.common.ErrorScreen
import com.geoai.geoaimobile.ui.common.ErrorType
import com.geoai.geoaimobile.ui.common.InstallationScreen
import com.geoai.geoaimobile.ui.common.LoadingScreen
import com.geoai.geoaimobile.ui.modelmanager.PreloadedModelError
import com.geoai.geoaimobile.ui.llmchat.LlmAskAudioDestination
import com.geoai.geoaimobile.ui.llmchat.LlmAskAudioScreen
import com.geoai.geoaimobile.ui.llmchat.LlmAskAudioViewModel
import com.geoai.geoaimobile.ui.llmchat.LlmAskImageDestination
import com.geoai.geoaimobile.ui.llmchat.LlmAskImageScreen
import com.geoai.geoaimobile.ui.llmchat.LlmAskImageViewModel
import com.geoai.geoaimobile.ui.llmchat.LlmChatDestination
import com.geoai.geoaimobile.ui.llmchat.LlmChatScreen
import com.geoai.geoaimobile.ui.llmchat.LlmChatViewModel
import com.geoai.geoaimobile.ui.llmsingleturn.LlmSingleTurnDestination
import com.geoai.geoaimobile.ui.llmsingleturn.LlmSingleTurnScreen
import com.geoai.geoaimobile.ui.llmsingleturn.LlmSingleTurnViewModel
import com.geoai.geoaimobile.ui.modelmanager.ModelManager
import com.geoai.geoaimobile.ui.modelmanager.ModelManagerViewModel

private const val TAG = "AGGalleryNavGraph"
private const val ROUTE_PLACEHOLDER = "placeholder"
private const val ENTER_ANIMATION_DURATION_MS = 500
private val ENTER_ANIMATION_EASING = EaseOutExpo
private const val ENTER_ANIMATION_DELAY_MS = 100

private const val EXIT_ANIMATION_DURATION_MS = 500
private val EXIT_ANIMATION_EASING = EaseOutExpo

private fun enterTween(): FiniteAnimationSpec<IntOffset> {
  return tween(
    ENTER_ANIMATION_DURATION_MS,
    easing = ENTER_ANIMATION_EASING,
    delayMillis = ENTER_ANIMATION_DELAY_MS,
  )
}

private fun exitTween(): FiniteAnimationSpec<IntOffset> {
  return tween(EXIT_ANIMATION_DURATION_MS, easing = EXIT_ANIMATION_EASING)
}

private fun AnimatedContentTransitionScope<*>.slideEnter(): EnterTransition {
  return slideIntoContainer(
    animationSpec = enterTween(),
    towards = AnimatedContentTransitionScope.SlideDirection.Left,
  )
}

private fun AnimatedContentTransitionScope<*>.slideExit(): ExitTransition {
  return slideOutOfContainer(
    animationSpec = exitTween(),
    towards = AnimatedContentTransitionScope.SlideDirection.Right,
  )
}

/** Navigation routes. */
@Composable
fun GalleryNavHost(
  navController: NavHostController,
  modifier: Modifier = Modifier,
  modelManagerViewModel: ModelManagerViewModel = hiltViewModel(),
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  val uiState by modelManagerViewModel.uiState.collectAsState()

  // Track whether app is in foreground.
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START,
        Lifecycle.Event.ON_RESUME -> {
          modelManagerViewModel.setAppInForeground(foreground = true)
        }
        Lifecycle.Event.ON_STOP,
        Lifecycle.Event.ON_PAUSE -> {
          modelManagerViewModel.setAppInForeground(foreground = false)
        }
        else -> {
          /* Do nothing for other events */
        }
      }
    }

    lifecycleOwner.lifecycle.addObserver(observer)

    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // Handle model installation and initialization states
  when {
    uiState.isInstallingModel -> {
      InstallationScreen(
        progress = uiState.installationProgress,
        message = if (uiState.installationPhase.isNotEmpty()) uiState.installationPhase else "Installing AI model...",
        bytesDownloaded = uiState.installationBytesDownloaded,
        totalBytes = uiState.installationTotalBytes,
        networkType = uiState.networkType,
        isRetrying = uiState.isRetrying
      )
    }
    uiState.isPreloadedModelInitializing && uiState.selectedModel.name.isNotEmpty() -> {
      // For existing models, go directly to chat interface with initialization status
      // This allows users to start typing while model loads in background
      LaunchedEffect(uiState.selectedModel) {
        navController.navigate("${LlmAskImageDestination.route}/${uiState.selectedModel.name}") {
          popUpTo(ROUTE_PLACEHOLDER) { inclusive = true }
        }
      }
    }
    uiState.isPreloadedModelInitializing -> {
      // For new installations, show loading screen during initialization
      LoadingScreen(message = "Initializing AI model, please wait...")
    }
    uiState.preloadedModelError != null -> {
      val errorType = when (uiState.preloadedModelError) {
        PreloadedModelError.MODEL_NOT_FOUND -> ErrorType.MODEL_NOT_FOUND
        PreloadedModelError.MODEL_LOAD_FAILED -> ErrorType.MODEL_LOAD_FAILED
        PreloadedModelError.INSUFFICIENT_RESOURCES -> ErrorType.INSUFFICIENT_RESOURCES
        PreloadedModelError.INSTALLATION_FAILED -> ErrorType.MODEL_NOT_FOUND // Show as installation issue
        else -> ErrorType.MODEL_LOAD_FAILED
      }
      val errorMessage = when (uiState.preloadedModelError) {
        PreloadedModelError.INSTALLATION_FAILED -> "Model installation failed. Please ensure the model file is accessible and restart the app."
        else -> null
      }
      ErrorScreen(
        errorType = errorType,
        customMessage = errorMessage,
        onRetry = if (errorType == ErrorType.MODEL_LOAD_FAILED || uiState.preloadedModelError == PreloadedModelError.INSTALLATION_FAILED) {
          { modelManagerViewModel.retryPreloadedModel() }
        } else null
      )
    }
    uiState.selectedModel.name.isNotEmpty() && !uiState.isPreloadedModelInitializing && !uiState.isInstallingModel -> {
      // Navigate directly to Ask Image chat when model is ready
      LaunchedEffect(uiState.selectedModel) {
        navController.navigate("${LlmAskImageDestination.route}/${uiState.selectedModel.name}") {
          popUpTo(ROUTE_PLACEHOLDER) { inclusive = true }
        }
      }
    }
  }

  NavHost(
    navController = navController,
    // Default to open home screen.
    startDestination = ROUTE_PLACEHOLDER,
    enterTransition = { EnterTransition.None },
    exitTransition = { ExitTransition.None },
    modifier = modifier.zIndex(1f),
  ) {
    // Placeholder root screen
    composable(route = ROUTE_PLACEHOLDER) { Text("") }

    // LLM chat demos.
    composable(
      route = "${LlmChatDestination.route}/{modelName}",
      arguments = listOf(navArgument("modelName") { type = NavType.StringType }),
      enterTransition = { slideEnter() },
      exitTransition = { slideExit() },
    ) { backStackEntry ->
      val viewModel: LlmChatViewModel = hiltViewModel(backStackEntry)

      getModelFromNavigationParam(backStackEntry, TASK_LLM_CHAT)?.let { defaultModel ->
        modelManagerViewModel.selectModel(defaultModel)

        LlmChatScreen(
          viewModel = viewModel,
          modelManagerViewModel = modelManagerViewModel,
          navigateUp = { navController.navigateUp() },
        )
      }
    }

    // LLM single turn.
    composable(
      route = "${LlmSingleTurnDestination.route}/{modelName}",
      arguments = listOf(navArgument("modelName") { type = NavType.StringType }),
      enterTransition = { slideEnter() },
      exitTransition = { slideExit() },
    ) { backStackEntry ->
      val viewModel: LlmSingleTurnViewModel = hiltViewModel(backStackEntry)

      getModelFromNavigationParam(backStackEntry, TASK_LLM_PROMPT_LAB)?.let { defaultModel ->
        modelManagerViewModel.selectModel(defaultModel)

        LlmSingleTurnScreen(
          viewModel = viewModel,
          modelManagerViewModel = modelManagerViewModel,
          navigateUp = { navController.navigateUp() },
        )
      }
    }

    // Ask image.
    composable(
      route = "${LlmAskImageDestination.route}/{modelName}",
      arguments = listOf(navArgument("modelName") { type = NavType.StringType }),
      enterTransition = { slideEnter() },
      exitTransition = { slideExit() },
    ) { backStackEntry ->
      val viewModel: LlmAskImageViewModel = hiltViewModel()

      getModelFromNavigationParam(backStackEntry, TASK_LLM_ASK_IMAGE)?.let { defaultModel ->
        modelManagerViewModel.selectModel(defaultModel)

        LlmAskImageScreen(
          viewModel = viewModel,
          modelManagerViewModel = modelManagerViewModel,
          navigateUp = { navController.navigateUp() },
        )
      }
    }

    // Ask audio.
    composable(
      route = "${LlmAskAudioDestination.route}/{modelName}",
      arguments = listOf(navArgument("modelName") { type = NavType.StringType }),
      enterTransition = { slideEnter() },
      exitTransition = { slideExit() },
    ) { backStackEntry ->
      val viewModel: LlmAskAudioViewModel = hiltViewModel()

      getModelFromNavigationParam(backStackEntry, TASK_LLM_ASK_AUDIO)?.let { defaultModel ->
        modelManagerViewModel.selectModel(defaultModel)

        LlmAskAudioScreen(
          viewModel = viewModel,
          modelManagerViewModel = modelManagerViewModel,
          navigateUp = { navController.navigateUp() },
        )
      }
    }
  }

  // Handle incoming intents for deep links
  val intent = androidx.activity.compose.LocalActivity.current?.intent
  val data = intent?.data
  if (data != null) {
    intent.data = null
    Log.d(TAG, "navigation link clicked: $data")
    if (data.toString().startsWith("com.geoai.geoaimobile://model/")) {
      val modelName = data.pathSegments.last()
      getModelByName(modelName)?.let { model ->
        // TODO(jingjin): need to show a list of possible tasks for this model.
        navigateToTaskScreen(
          navController = navController,
          taskType = TaskType.LLM_CHAT,
          model = model,
        )
      }
    }
  }
}

fun navigateToTaskScreen(
  navController: NavHostController,
  taskType: TaskType,
  model: Model? = null,
) {
  val modelName = model?.name ?: ""
  when (taskType) {
    TaskType.LLM_CHAT -> navController.navigate("${LlmChatDestination.route}/${modelName}")
    TaskType.LLM_ASK_IMAGE -> navController.navigate("${LlmAskImageDestination.route}/${modelName}")
    TaskType.LLM_ASK_AUDIO -> navController.navigate("${LlmAskAudioDestination.route}/${modelName}")
    TaskType.LLM_PROMPT_LAB ->
      navController.navigate("${LlmSingleTurnDestination.route}/${modelName}")
    TaskType.TEST_TASK_1 -> {}
    TaskType.TEST_TASK_2 -> {}
  }
}

fun getModelFromNavigationParam(entry: NavBackStackEntry, task: Task): Model? {
  var modelName = entry.arguments?.getString("modelName") ?: ""
  if (modelName.isEmpty()) {
    modelName = task.models[0].name
  }
  val model = getModelByName(modelName)
  return model
}
