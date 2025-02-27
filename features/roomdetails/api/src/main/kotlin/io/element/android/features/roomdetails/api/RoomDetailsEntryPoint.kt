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

package io.element.android.features.roomdetails.api

import android.os.Parcelable
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.libraries.architecture.FeatureEntryPoint
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.parcelize.Parcelize

interface RoomDetailsEntryPoint : FeatureEntryPoint {

    sealed interface InitialTarget : Parcelable {
        @Parcelize
        data object RoomDetails : InitialTarget

        @Parcelize
        data class RoomMemberDetails(val roomMemberId: UserId) : InitialTarget
    }

    data class Inputs(val initialElement: InitialTarget) : NodeInputs

    fun createNode(parentNode: Node, buildContext: BuildContext, inputs: Inputs, plugins: List<Plugin>): Node
}
