/*
 * Copyright (c) 2022 New Vector Ltd
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

package io.element.android.features.messages.impl.timeline.model.event

import androidx.compose.runtime.Immutable

@Immutable
sealed interface TimelineItemEventContent {
    val type: String
}

/**
 * Only text based content and states can be copied.
 */
fun TimelineItemEventContent.canBeCopied(): Boolean =
    when (this) {
        is TimelineItemTextBasedContent,
        is TimelineItemStateContent,
        is TimelineItemRedactedContent -> true
        else -> false
    }

/**
 * Return true if user can react (i.e. send a reaction) on the event content.
 */
fun TimelineItemEventContent.canReact(): Boolean =
    when (this) {
        is TimelineItemTextBasedContent,
        is TimelineItemAudioContent,
        is TimelineItemEncryptedContent,
        is TimelineItemFileContent,
        is TimelineItemImageContent,
        is TimelineItemLocationContent,
        is TimelineItemPollContent,
        is TimelineItemVideoContent -> true
        is TimelineItemStateContent,
        is TimelineItemRedactedContent,
        TimelineItemUnknownContent -> false
    }
