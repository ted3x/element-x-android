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

package io.element.android.features.messages.impl.media.viewer

import io.element.android.features.messages.impl.media.local.LocalMedia
import io.element.android.features.messages.impl.media.local.MediaInfo
import io.element.android.libraries.architecture.Async
import io.element.android.libraries.designsystem.utils.SnackbarMessage
import io.element.android.libraries.matrix.api.media.MediaSource

data class MediaViewerState(
    val mediaInfo: MediaInfo,
    val thumbnailSource: MediaSource?,
    val downloadedMedia: Async<LocalMedia>,
    val snackbarMessage: SnackbarMessage?,
    val eventSink: (MediaViewerEvents) -> Unit,
)
