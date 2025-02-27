/*
 * Copyright (c) 2023 New Vector Ltd
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

package io.element.android.features.preferences.impl.developer.tracing

import io.element.android.libraries.matrix.api.tracing.LogLevel
import io.element.android.libraries.matrix.api.tracing.Target
import io.element.android.libraries.matrix.api.tracing.TracingFilterConfigurations
import javax.inject.Inject

class TargetLogLevelMapBuilder @Inject constructor(
    private val tracingConfigurationStore: TracingConfigurationStore,
) {
    private val defaultConfig = TracingFilterConfigurations.debug

    fun getDefaultMap(): Map<Target, LogLevel> {
        return Target.entries.associateWith { target ->
            defaultConfig.getLogLevel(target)
                ?: LogLevel.INFO
        }
    }

    fun getCurrentMap(): Map<Target, LogLevel> {
        return Target.entries.associateWith { target ->
            tracingConfigurationStore.getLogLevel(target)
                ?: defaultConfig.getLogLevel(target)
                ?: LogLevel.INFO
        }
    }
}
