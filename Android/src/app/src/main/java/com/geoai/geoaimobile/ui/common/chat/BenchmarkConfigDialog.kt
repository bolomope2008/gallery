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

package com.geoai.geoaimobile.ui.common.chat

// import androidx.compose.ui.tooling.preview.Preview
// import com.geoai.geoaimobile.ui.theme.GalleryTheme
import androidx.compose.runtime.Composable
import com.geoai.geoaimobile.data.Config
import com.geoai.geoaimobile.data.ConfigKey
import com.geoai.geoaimobile.data.NumberSliderConfig
import com.geoai.geoaimobile.data.ValueType
import com.geoai.geoaimobile.data.convertValueToTargetType
import com.geoai.geoaimobile.ui.common.ConfigDialog

private const val DEFAULT_BENCHMARK_WARM_UP_ITERATIONS = 50f
private const val DEFAULT_BENCHMARK_ITERATIONS = 200f

private val BENCHMARK_CONFIGS: List<Config> =
  listOf(
    NumberSliderConfig(
      key = ConfigKey.WARM_UP_ITERATIONS,
      sliderMin = 10f,
      sliderMax = 200f,
      defaultValue = DEFAULT_BENCHMARK_WARM_UP_ITERATIONS,
      valueType = ValueType.INT,
    ),
    NumberSliderConfig(
      key = ConfigKey.BENCHMARK_ITERATIONS,
      sliderMin = 50f,
      sliderMax = 500f,
      defaultValue = DEFAULT_BENCHMARK_ITERATIONS,
      valueType = ValueType.INT,
    ),
  )

private val BENCHMARK_CONFIGS_INITIAL_VALUES =
  mapOf(
    ConfigKey.WARM_UP_ITERATIONS.label to DEFAULT_BENCHMARK_WARM_UP_ITERATIONS,
    ConfigKey.BENCHMARK_ITERATIONS.label to DEFAULT_BENCHMARK_ITERATIONS,
  )

/**
 * Composable function to display a configuration dialog for benchmarking a chat message.
 *
 * This function renders a configuration dialog specifically tailored for setting up benchmark
 * parameters. It allows users to specify warm-up and benchmark iterations before running a
 * benchmark test on a given chat message.
 */
@Composable
fun BenchmarkConfigDialog(
  onDismissed: () -> Unit,
  messageToBenchmark: ChatMessage?,
  onBenchmarkClicked: (ChatMessage, warmUpIterations: Int, benchmarkIterations: Int) -> Unit,
) {
  ConfigDialog(
    title = "Benchmark configs",
    okBtnLabel = "Start",
    configs = BENCHMARK_CONFIGS,
    initialValues = BENCHMARK_CONFIGS_INITIAL_VALUES,
    onDismissed = onDismissed,
    onOk = { curConfigValues ->
      // Hide config dialog.
      onDismissed()

      // Start benchmark.
      messageToBenchmark?.let { message ->
        val warmUpIterations =
          convertValueToTargetType(
            value = curConfigValues.getValue(ConfigKey.WARM_UP_ITERATIONS.label),
            valueType = ValueType.INT,
          )
            as Int
        val benchmarkIterations =
          convertValueToTargetType(
            value = curConfigValues.getValue(ConfigKey.BENCHMARK_ITERATIONS.label),
            valueType = ValueType.INT,
          )
            as Int
        onBenchmarkClicked(message, warmUpIterations, benchmarkIterations)
      }
    },
  )
}

// @Preview(showBackground = true)
// @Composable
// fun BenchmarkConfigDialogPreview() {
//   GalleryTheme {
//     BenchmarkConfigDialog(
//       onDismissed = {},
//       messageToBenchmark = null,
//       onBenchmarkClicked = { _, _, _ -> },
//     )
//   }
// }
