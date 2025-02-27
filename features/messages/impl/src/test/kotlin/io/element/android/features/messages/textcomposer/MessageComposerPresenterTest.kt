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

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.element.android.features.messages.textcomposer

import android.net.Uri
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.messagecomposer.AttachmentsState
import io.element.android.features.messages.impl.messagecomposer.MessageComposerContextImpl
import io.element.android.features.messages.impl.messagecomposer.MessageComposerEvents
import io.element.android.features.messages.impl.messagecomposer.MessageComposerPresenter
import io.element.android.features.messages.impl.messagecomposer.MessageComposerState
import io.element.android.features.messages.media.FakeLocalMediaFactory
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.designsystem.utils.SnackbarDispatcher
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.TransactionId
import io.element.android.libraries.matrix.api.media.ImageInfo
import io.element.android.libraries.matrix.api.media.VideoInfo
import io.element.android.libraries.matrix.api.room.MatrixRoom
import io.element.android.libraries.matrix.test.ANOTHER_MESSAGE
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.libraries.matrix.test.A_MESSAGE
import io.element.android.libraries.matrix.test.A_REPLY
import io.element.android.libraries.matrix.test.A_TRANSACTION_ID
import io.element.android.libraries.matrix.test.A_USER_NAME
import io.element.android.libraries.matrix.test.room.FakeMatrixRoom
import io.element.android.libraries.mediapickers.api.PickerProvider
import io.element.android.libraries.mediapickers.test.FakePickerProvider
import io.element.android.libraries.mediaupload.api.MediaPreProcessor
import io.element.android.libraries.mediaupload.api.MediaSender
import io.element.android.libraries.mediaupload.api.MediaUploadInfo
import io.element.android.libraries.mediaupload.test.FakeMediaPreProcessor
import io.element.android.libraries.textcomposer.Message
import io.element.android.libraries.textcomposer.MessageComposerMode
import io.element.android.services.analytics.test.FakeAnalyticsService
import io.element.android.tests.testutils.WarmUpRule
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.File

class MessageComposerPresenterTest {

    @Rule
    @JvmField
    val warmUpRule = WarmUpRule()

    private val pickerProvider = FakePickerProvider().apply {
        givenResult(mockk()) // Uri is not available in JVM, so the only way to have a non-null Uri is using Mockk
    }
    private val featureFlagService = FakeFeatureFlagService(
        mapOf(FeatureFlags.LocationSharing.key to true)
    )
    private val mediaPreProcessor = FakeMediaPreProcessor()
    private val snackbarDispatcher = SnackbarDispatcher()
    private val mockMediaUrl: Uri = mockk("localMediaUri")
    private val localMediaFactory = FakeLocalMediaFactory(mockMediaUrl)
    private val analyticsService = FakeAnalyticsService()

    @Test
    fun `present - initial state`() = runTest {
        val presenter = createPresenter(this)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.isFullScreen).isFalse()
            assertThat(initialState.richTextEditorState.messageHtml).isEqualTo("")
            assertThat(initialState.mode).isEqualTo(MessageComposerMode.Normal(""))
            assertThat(initialState.showAttachmentSourcePicker).isFalse()
            assertThat(initialState.canShareLocation).isTrue()
            assertThat(initialState.attachmentsState).isEqualTo(AttachmentsState.None)
            assertThat(initialState.canSendMessage).isFalse()
        }
    }

    @Test
    fun `present - toggle fullscreen`() = runTest {
        val presenter = createPresenter(this)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val initialState = awaitItem()
            initialState.eventSink.invoke(MessageComposerEvents.ToggleFullScreenState)
            val fullscreenState = awaitItem()
            assertThat(fullscreenState.isFullScreen).isTrue()
            fullscreenState.eventSink.invoke(MessageComposerEvents.ToggleFullScreenState)
            val notFullscreenState = awaitItem()
            assertThat(notFullscreenState.isFullScreen).isFalse()
        }
    }

    @Test
    fun `present - change message`() = runTest {
        val presenter = createPresenter(this)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val initialState = awaitItem()
            initialState.richTextEditorState.setHtml(A_MESSAGE)
            val withMessageState = awaitItem()
            assertThat(withMessageState.richTextEditorState.messageHtml).isEqualTo(A_MESSAGE)
            assertThat(withMessageState.canSendMessage).isTrue()
            withMessageState.richTextEditorState.setHtml("")
            val withEmptyMessageState = awaitItem()
            assertThat(withEmptyMessageState.richTextEditorState.messageHtml).isEqualTo("")
            assertThat(withEmptyMessageState.canSendMessage).isFalse()
        }
    }

    @Test
    fun `present - change mode to edit`() = runTest {
        val presenter = createPresenter(this)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            var state = awaitItem()
            val mode = anEditMode()
            state.eventSink.invoke(MessageComposerEvents.SetMode(mode))
            state = awaitItem()
            assertThat(state.mode).isEqualTo(mode)
            state = awaitItem()
            assertThat(state.richTextEditorState.messageHtml).isEqualTo(A_MESSAGE)
            assertThat(state.canSendMessage).isTrue()
            backToNormalMode(state, skipCount = 1)
        }
    }

    @Test
    fun `present - change mode to reply`() = runTest {
        val presenter = createPresenter(this)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            var state = awaitItem()
            val mode = aReplyMode()
            state.eventSink.invoke(MessageComposerEvents.SetMode(mode))
            state = awaitItem()
            assertThat(state.mode).isEqualTo(mode)
            assertThat(state.richTextEditorState.messageHtml).isEqualTo("")
            assertThat(state.canSendMessage).isFalse()
            backToNormalMode(state)
        }
    }

    @Test
    fun `present - change mode to quote`() = runTest {
        val presenter = createPresenter(this)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            var state = awaitItem()
            val mode = aQuoteMode()
            state.eventSink.invoke(MessageComposerEvents.SetMode(mode))
            state = awaitItem()
            assertThat(state.mode).isEqualTo(mode)
            assertThat(state.richTextEditorState.messageHtml).isEqualTo("")
            assertThat(state.canSendMessage).isFalse()
            backToNormalMode(state)
        }
    }

    @Test
    fun `present - send message`() = runTest {
        val presenter = createPresenter(this)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val initialState = awaitItem()
            initialState.richTextEditorState.setHtml(A_MESSAGE)
            val withMessageState = awaitItem()
            assertThat(withMessageState.richTextEditorState.messageHtml).isEqualTo(A_MESSAGE)
            assertThat(withMessageState.canSendMessage).isTrue()
            withMessageState.eventSink.invoke(MessageComposerEvents.SendMessage(A_MESSAGE.toMessage()))
            val messageSentState = awaitItem()
            assertThat(messageSentState.richTextEditorState.messageHtml).isEqualTo("")
            assertThat(messageSentState.canSendMessage).isFalse()
        }
    }

    @Test
    fun `present - edit sent message`() = runTest {
        val fakeMatrixRoom = FakeMatrixRoom()
        val presenter = createPresenter(
            this,
            fakeMatrixRoom,
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.richTextEditorState.messageHtml).isEqualTo("")
            val mode = anEditMode()
            initialState.eventSink.invoke(MessageComposerEvents.SetMode(mode))
            skipItems(1)
            val withMessageState = awaitItem()
            assertThat(withMessageState.mode).isEqualTo(mode)
            assertThat(withMessageState.richTextEditorState.messageHtml).isEqualTo(A_MESSAGE)
            assertThat(withMessageState.canSendMessage).isTrue()
            withMessageState.richTextEditorState.setHtml(ANOTHER_MESSAGE)
            val withEditedMessageState = awaitItem()
            assertThat(withEditedMessageState.richTextEditorState.messageHtml).isEqualTo(ANOTHER_MESSAGE)
            withEditedMessageState.eventSink.invoke(MessageComposerEvents.SendMessage(ANOTHER_MESSAGE.toMessage()))
            skipItems(1)
            val messageSentState = awaitItem()
            assertThat(messageSentState.richTextEditorState.messageHtml).isEqualTo("")
            assertThat(messageSentState.canSendMessage).isFalse()
            assertThat(fakeMatrixRoom.editMessageCalls.first()).isEqualTo(ANOTHER_MESSAGE to ANOTHER_MESSAGE)
        }
    }

    @Test
    fun `present - edit not sent message`() = runTest {
        val fakeMatrixRoom = FakeMatrixRoom()
        val presenter = createPresenter(
            this,
            fakeMatrixRoom,
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.richTextEditorState.messageHtml).isEqualTo("")
            val mode = anEditMode(eventId = null, transactionId = A_TRANSACTION_ID)
            initialState.eventSink.invoke(MessageComposerEvents.SetMode(mode))
            skipItems(1)
            val withMessageState = awaitItem()
            assertThat(withMessageState.mode).isEqualTo(mode)
            assertThat(withMessageState.richTextEditorState.messageHtml).isEqualTo(A_MESSAGE)
            assertThat(withMessageState.canSendMessage).isTrue()
            withMessageState.richTextEditorState.setHtml(ANOTHER_MESSAGE)
            val withEditedMessageState = awaitItem()
            assertThat(withEditedMessageState.richTextEditorState.messageHtml).isEqualTo(ANOTHER_MESSAGE)
            withEditedMessageState.eventSink.invoke(MessageComposerEvents.SendMessage(ANOTHER_MESSAGE.toMessage()))
            skipItems(1)
            val messageSentState = awaitItem()
            assertThat(messageSentState.richTextEditorState.messageHtml).isEqualTo("")
            assertThat(messageSentState.canSendMessage).isFalse()
            assertThat(fakeMatrixRoom.editMessageCalls.first()).isEqualTo(ANOTHER_MESSAGE to ANOTHER_MESSAGE)
        }
    }

    @Test
    fun `present - reply message`() = runTest {
        val fakeMatrixRoom = FakeMatrixRoom()
        val presenter = createPresenter(
            this,
            fakeMatrixRoom,
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.richTextEditorState.messageHtml).isEqualTo("")
            val mode = aReplyMode()
            initialState.eventSink.invoke(MessageComposerEvents.SetMode(mode))
            val state = awaitItem()
            assertThat(state.mode).isEqualTo(mode)
            assertThat(state.richTextEditorState.messageHtml).isEqualTo("")
            assertThat(state.canSendMessage).isFalse()
            state.richTextEditorState.setHtml(A_REPLY)
            val withMessageState = awaitItem()
            assertThat(withMessageState.richTextEditorState.messageHtml).isEqualTo(A_REPLY)
            assertThat(withMessageState.canSendMessage).isTrue()
            withMessageState.eventSink.invoke(MessageComposerEvents.SendMessage(A_REPLY.toMessage()))
            skipItems(1)
            val messageSentState = awaitItem()
            assertThat(messageSentState.richTextEditorState.messageHtml).isEqualTo("")
            assertThat(messageSentState.canSendMessage).isFalse()
            assertThat(fakeMatrixRoom.replyMessageParameter).isEqualTo(A_REPLY to A_REPLY)
        }
    }

    @Test
    fun `present - Open attachments menu`() = runTest {
        val presenter = createPresenter(this)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.showAttachmentSourcePicker).isEqualTo(false)
            initialState.eventSink(MessageComposerEvents.AddAttachment)
            assertThat(awaitItem().showAttachmentSourcePicker).isEqualTo(true)
        }
    }

    @Test
    fun `present - Dismiss attachments menu`() = runTest {
        val presenter = createPresenter(this)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val initialState = awaitItem()
            initialState.eventSink(MessageComposerEvents.AddAttachment)
            skipItems(1)

            initialState.eventSink(MessageComposerEvents.DismissAttachmentMenu)
            assertThat(awaitItem().showAttachmentSourcePicker).isFalse()
        }
    }

    @Test
    fun `present - Pick image from gallery`() = runTest {
        val room = FakeMatrixRoom()
        val presenter = createPresenter(this, room = room)
        pickerProvider.givenMimeType(MimeTypes.Images)
        mediaPreProcessor.givenResult(
            Result.success(
                MediaUploadInfo.Image(
                    file = File("/some/path"),
                    imageInfo = ImageInfo(
                        width = null,
                        height = null,
                        mimetype = null,
                        size = null,
                        thumbnailInfo = null,
                        thumbnailSource = null,
                        blurhash = null,
                    ),
                    thumbnailFile = File("/some/path")
                )
            )
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val initialState = awaitItem()
            initialState.eventSink(MessageComposerEvents.PickAttachmentSource.FromGallery)
            val previewingState = awaitItem()
            assertThat(previewingState.showAttachmentSourcePicker).isFalse()
            assertThat(previewingState.attachmentsState).isInstanceOf(AttachmentsState.Previewing::class.java)
        }
    }

    @Test
    fun `present - Pick video from gallery`() = runTest {
        val room = FakeMatrixRoom()
        val presenter = createPresenter(this, room = room)
        pickerProvider.givenMimeType(MimeTypes.Videos)
        mediaPreProcessor.givenResult(
            Result.success(
                MediaUploadInfo.Video(
                    file = File("/some/path"),
                    videoInfo = VideoInfo(
                        width = null,
                        height = null,
                        mimetype = null,
                        duration = null,
                        size = null,
                        thumbnailInfo = null,
                        thumbnailSource = null,
                        blurhash = null,
                    ),
                    thumbnailFile = File("/some/path")
                )
            )
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val initialState = awaitItem()
            initialState.eventSink(MessageComposerEvents.PickAttachmentSource.FromGallery)
            val previewingState = awaitItem()
            assertThat(previewingState.showAttachmentSourcePicker).isFalse()
            assertThat(previewingState.attachmentsState).isInstanceOf(AttachmentsState.Previewing::class.java)
        }
    }

    @Test
    fun `present - Pick media from gallery & cancel does nothing`() = runTest {
        val presenter = createPresenter(this)
        with(pickerProvider) {
            givenResult(null) // Simulate a user canceling the flow
            givenMimeType(MimeTypes.Images)
        }
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val initialState = awaitItem()
            initialState.eventSink(MessageComposerEvents.PickAttachmentSource.FromGallery)
            // No crashes here, otherwise it fails
        }
    }

    @Test
    fun `present - Pick file from storage`() = runTest {
        val room = FakeMatrixRoom()
        room.givenProgressCallbackValues(
            listOf(
                Pair(0, 10),
                Pair(5, 10),
                Pair(10, 10)
            )
        )
        val presenter = createPresenter(this, room = room)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val initialState = awaitItem()
            initialState.eventSink(MessageComposerEvents.PickAttachmentSource.FromFiles)
            val sendingState = awaitItem()
            assertThat(sendingState.showAttachmentSourcePicker).isFalse()
            assertThat(sendingState.attachmentsState).isInstanceOf(AttachmentsState.Sending.Processing::class.java)
            assertThat(awaitItem().attachmentsState).isEqualTo(AttachmentsState.Sending.Uploading(0f))
            assertThat(awaitItem().attachmentsState).isEqualTo(AttachmentsState.Sending.Uploading(0.5f))
            assertThat(awaitItem().attachmentsState).isEqualTo(AttachmentsState.Sending.Uploading(1f))
            val sentState = awaitItem()
            assertThat(sentState.attachmentsState).isEqualTo(AttachmentsState.None)
            assertThat(room.sendMediaCount).isEqualTo(1)
        }
    }

    @Test
    fun `present - Take photo`() = runTest {
        val room = FakeMatrixRoom()
        val presenter = createPresenter(this, room = room)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val initialState = awaitItem()
            initialState.eventSink(MessageComposerEvents.PickAttachmentSource.PhotoFromCamera)
            val previewingState = awaitItem()
            assertThat(previewingState.showAttachmentSourcePicker).isFalse()
            assertThat(previewingState.attachmentsState).isInstanceOf(AttachmentsState.Previewing::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - Record video`() = runTest {
        val room = FakeMatrixRoom()
        val presenter = createPresenter(this, room = room)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val initialState = awaitItem()
            initialState.eventSink(MessageComposerEvents.PickAttachmentSource.VideoFromCamera)
            val previewingState = awaitItem()
            assertThat(previewingState.showAttachmentSourcePicker).isFalse()
            assertThat(previewingState.attachmentsState).isInstanceOf(AttachmentsState.Previewing::class.java)
        }
    }

    @Test
    fun `present - Uploading media failure can be recovered from`() = runTest {
        val room = FakeMatrixRoom().apply {
            givenSendMediaResult(Result.failure(Exception()))
        }
        val presenter = createPresenter(this, room = room)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val initialState = awaitItem()
            initialState.eventSink(MessageComposerEvents.PickAttachmentSource.FromFiles)
            val sendingState = awaitItem()
            assertThat(sendingState.attachmentsState).isInstanceOf(AttachmentsState.Sending::class.java)
            val finalState = awaitItem()
            assertThat(finalState.attachmentsState).isInstanceOf(AttachmentsState.None::class.java)
            snackbarDispatcher.snackbarMessage.test {
                // Assert error message received
                assertThat(awaitItem()).isNotNull()
            }
        }
    }

    @Test
    fun `present - CancelSendAttachment stops media upload`() = runTest {
        val presenter = createPresenter(this)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val initialState = awaitItem()
            initialState.eventSink(MessageComposerEvents.PickAttachmentSource.FromFiles)
            val sendingState = awaitItem()
            assertThat(sendingState.showAttachmentSourcePicker).isFalse()
            assertThat(sendingState.attachmentsState).isInstanceOf(AttachmentsState.Sending.Processing::class.java)
            sendingState.eventSink(MessageComposerEvents.CancelSendAttachment)
            assertThat(awaitItem().attachmentsState).isEqualTo(AttachmentsState.None)
        }
    }

    @Test
    fun `present - errors are tracked`() = runTest {
        val testException = Exception("Test error")
        val presenter = createPresenter(this)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val initialState = awaitItem()
            initialState.eventSink(MessageComposerEvents.Error(testException))
            assertThat(analyticsService.trackedErrors).containsExactly(testException)
        }
    }

    @Test
    fun `present - ToggleTextFormatting toggles text formatting`() = runTest {
        val presenter = createPresenter(this)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.showTextFormatting).isFalse()
            initialState.eventSink(MessageComposerEvents.AddAttachment)
            val composerOptions = awaitItem()
            assertThat(composerOptions.showAttachmentSourcePicker).isTrue()
            composerOptions.eventSink(MessageComposerEvents.ToggleTextFormatting(true))
            awaitItem() // composer options closed
            val showTextFormatting = awaitItem()
            assertThat(showTextFormatting.showAttachmentSourcePicker).isFalse()
            assertThat(showTextFormatting.showTextFormatting).isTrue()
            showTextFormatting.eventSink(MessageComposerEvents.ToggleTextFormatting(false))
            val finished = awaitItem()
            assertThat(finished.showTextFormatting).isFalse()
        }
    }

    private suspend fun ReceiveTurbine<MessageComposerState>.backToNormalMode(state: MessageComposerState, skipCount: Int = 0) {
        state.eventSink.invoke(MessageComposerEvents.CloseSpecialMode)
        skipItems(skipCount)
        val normalState = awaitItem()
        assertThat(normalState.mode).isEqualTo(MessageComposerMode.Normal(""))
        assertThat(normalState.richTextEditorState.messageHtml).isEqualTo("")
        assertThat(normalState.canSendMessage).isFalse()
    }

    private fun createPresenter(
        coroutineScope: CoroutineScope,
        room: MatrixRoom = FakeMatrixRoom(),
        pickerProvider: PickerProvider = this.pickerProvider,
        featureFlagService: FeatureFlagService = this.featureFlagService,
        mediaPreProcessor: MediaPreProcessor = this.mediaPreProcessor,
        snackbarDispatcher: SnackbarDispatcher = this.snackbarDispatcher,
    ) = MessageComposerPresenter(
        coroutineScope,
        room,
        pickerProvider,
        featureFlagService,
        localMediaFactory,
        MediaSender(mediaPreProcessor, room),
        snackbarDispatcher,
        analyticsService,
        MessageComposerContextImpl(),
        TestRichTextEditorStateFactory(),
    )
}

fun anEditMode(
    eventId: EventId? = AN_EVENT_ID,
    message: String = A_MESSAGE,
    transactionId: TransactionId? = null,
) = MessageComposerMode.Edit(eventId, message, transactionId)

fun aReplyMode() = MessageComposerMode.Reply(A_USER_NAME, null, AN_EVENT_ID, A_MESSAGE)
fun aQuoteMode() = MessageComposerMode.Quote(AN_EVENT_ID, A_MESSAGE)

private fun String.toMessage() = Message(
    html = this,
    markdown = this,
)
