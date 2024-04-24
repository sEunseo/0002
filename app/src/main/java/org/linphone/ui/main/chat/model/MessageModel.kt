/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.main.chat.model

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.media.AudioFocusRequestCompat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageListenerStub
import org.linphone.core.ChatMessageReaction
import org.linphone.core.ChatRoom
import org.linphone.core.ConferenceInfo
import org.linphone.core.Content
import org.linphone.core.Factory
import org.linphone.core.Player
import org.linphone.core.PlayerListener
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.model.isEndToEndEncryptionMandatory
import org.linphone.utils.AppUtils
import org.linphone.utils.AudioUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.PatternClickableSpan
import org.linphone.utils.SpannableClickedListener
import org.linphone.utils.TimestampUtils

class MessageModel @WorkerThread constructor(
    val chatMessage: ChatMessage,
    val avatarModel: ContactAvatarModel,
    val isFromGroup: Boolean,
    val isReply: Boolean,
    val replyTo: String,
    val replyText: String,
    val replyToMessageId: String?,
    val isForward: Boolean,
    isGroupedWithPreviousOne: Boolean,
    isGroupedWithNextOne: Boolean,
    private val onContentClicked: ((file: String) -> Unit)? = null,
    private val onJoinConferenceClicked: ((uri: String) -> Unit)? = null,
    private val onWebUrlClicked: ((url: String) -> Unit)? = null,
    private val onContactClicked: ((friendRefKey: String) -> Unit)? = null,
    private val onRedToastToShow: ((pair: Pair<String, Int>) -> Unit)? = null
) {
    companion object {
        private const val TAG = "[Message Model]"

        private const val SIP_URI_REGEXP = "(<?sips?:)[a-zA-Z0-9+_.\\-]+(?:@([a-zA-Z0-9+_.\\-;=~]+))+(>)?"
        private const val HTTP_LINK_REGEXP = "https?://(www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_+.~#?&/=]*)"
        private const val MENTION_REGEXP = "@([A-Za-z0-9._-]+)"
    }

    val id = chatMessage.messageId

    var isRead = chatMessage.isRead

    val isOutgoing = chatMessage.isOutgoing

    val isInError = chatMessage.state == ChatMessage.State.NotDelivered

    val timestamp = chatMessage.time

    val time = TimestampUtils.toString(timestamp)

    val chatRoomIsReadOnly = chatMessage.chatRoom.isReadOnly ||
        (!chatMessage.chatRoom.hasCapability(ChatRoom.Capabilities.Encrypted.toInt()) && LinphoneUtils.getDefaultAccount()?.isEndToEndEncryptionMandatory() == true)

    val groupedWithNextMessage = MutableLiveData<Boolean>()

    val groupedWithPreviousMessage = MutableLiveData<Boolean>()

    val statusIcon = MutableLiveData<Int>()

    val text = MutableLiveData<Spannable>()

    val reactions = MutableLiveData<String>()

    val filesList = MutableLiveData<ArrayList<FileModel>>()

    val firstFileModel = MediatorLiveData<FileModel>()

    val isSelected = MutableLiveData<Boolean>()

    // Below are for conferences info
    val meetingFound = MutableLiveData<Boolean>()

    val meetingUpdated = MutableLiveData<Boolean>()

    val meetingCancelled = MutableLiveData<Boolean>()

    val meetingDay = MutableLiveData<String>()

    val meetingDayNumber = MutableLiveData<String>()

    val meetingSubject = MutableLiveData<String>()

    val meetingDate = MutableLiveData<String>()

    val meetingTime = MutableLiveData<String>()

    val meetingDescription = MutableLiveData<String>()

    val meetingParticipants = MutableLiveData<String>()

    private lateinit var meetingConferenceUri: Address
    // End of conference info related fields

    // Voice record related fields
    val isVoiceRecord = MutableLiveData<Boolean>()

    val isPlayingVoiceRecord = MutableLiveData<Boolean>()

    val voiceRecordPlayerPosition = MutableLiveData<Int>()

    val voiceRecordingDuration = MutableLiveData<Int>()

    val formattedVoiceRecordingDuration = MutableLiveData<String>()

    val dismissLongPressMenuEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private var voiceRecordAudioFocusRequest: AudioFocusRequestCompat? = null

    private lateinit var voiceRecordPath: String

    private lateinit var voiceRecordPlayer: Player

    private val playerListener = PlayerListener {
        Log.i("$TAG End of file reached")
        stopVoiceRecordPlayer()
    }
    // End of voice record related fields

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var transferringFileModel: FileModel? = null

    private var allFilesDownloaded = true

    private val chatMessageListener = object : ChatMessageListenerStub() {
        @WorkerThread
        override fun onMsgStateChanged(message: ChatMessage, messageState: ChatMessage.State?) {
            if (messageState != ChatMessage.State.FileTransferDone && messageState != ChatMessage.State.FileTransferInProgress) {
                statusIcon.postValue(LinphoneUtils.getChatIconResId(chatMessage.state))

                if (messageState == ChatMessage.State.Displayed) {
                    isRead = chatMessage.isRead
                }
            } else if (messageState == ChatMessage.State.FileTransferDone) {
                Log.i("$TAG File transfer is done")
                transferringFileModel?.transferProgress?.postValue(-1)
                transferringFileModel = null
                if (!allFilesDownloaded) {
                    computeContentsList()
                }
            }
        }

        @WorkerThread
        override fun onNewMessageReaction(message: ChatMessage, reaction: ChatMessageReaction) {
            Log.i(
                "$TAG New reaction [${reaction.body}] from [${reaction.fromAddress.asStringUriOnly()}] for message with ID [$id]"
            )
            updateReactionsList()
        }

        @WorkerThread
        override fun onReactionRemoved(message: ChatMessage, address: Address) {
            Log.i("$TAG A reaction was removed for message with ID [$id]")
            updateReactionsList()
        }

        @WorkerThread
        override fun onFileTransferProgressIndication(
            message: ChatMessage,
            content: Content,
            offset: Int,
            total: Int
        ) {
            val percent = ((offset * 100.0) / total).toInt() // Conversion from int to double and back to int is required

            val model = transferringFileModel
            if (model == null) {
                Log.w("$TAG A file is being uploaded/downloaded but no transferringFileModel set!")
                val found = filesList.value.orEmpty().find {
                    it.fileName == content.name
                }
                if (found != null) {
                    transferringFileModel = found
                    Log.i("$TAG Found matching FileModel in files list using content name")
                } else {
                    Log.w(
                        "$TAG Failed to find a matching FileModel in files list with content name [${content.name}]"
                    )
                }
            }
            model?.transferProgress?.postValue(percent)
        }
    }

    init {
        groupedWithNextMessage.postValue(isGroupedWithNextOne)
        groupedWithPreviousMessage.postValue(isGroupedWithPreviousOne)
        isPlayingVoiceRecord.postValue(false)

        chatMessage.addListener(chatMessageListener)
        statusIcon.postValue(LinphoneUtils.getChatIconResId(chatMessage.state))
        updateReactionsList()

        computeContentsList()

        coreContext.postOnMainThread {
            firstFileModel.addSource(filesList) {
                firstFileModel.value = it.firstOrNull()
            }
        }
    }

    @WorkerThread
    fun destroy() {
        scope.cancel()

        filesList.value.orEmpty().forEach(FileModel::destroy)

        if (::voiceRecordPlayer.isInitialized) {
            stopVoiceRecordPlayer()
            voiceRecordPlayer.removeListener(playerListener)
        }

        chatMessage.removeListener(chatMessageListener)
    }

    @UiThread
    fun sendReaction(emoji: String) {
        coreContext.postOnCoreThread {
            Log.i("$TAG Sending reaction [$emoji] to message with ID [$id]")
            val reaction = chatMessage.createReaction(emoji)
            reaction.send()
            dismissLongPressMenuEvent.postValue(Event(true))
        }
    }

    @UiThread
    fun resend() {
        coreContext.postOnCoreThread {
            Log.i("$TAG Re-sending message with ID [$id]")
            chatMessage.send()
        }
    }

    @UiThread
    fun joinConference() {
        coreContext.postOnCoreThread {
            if (::meetingConferenceUri.isInitialized) {
                val uri = meetingConferenceUri.asStringUriOnly()
                coreContext.postOnMainThread {
                    onJoinConferenceClicked?.invoke(uri)
                }
            }
        }
    }

    @UiThread
    fun togglePlayPauseVoiceRecord() {
        coreContext.postOnCoreThread {
            if (isPlayingVoiceRecord.value == false) {
                startVoiceRecordPlayer()
            } else {
                pauseVoiceRecordPlayer()
            }
        }
    }

    @WorkerThread
    private fun computeContentsList() {
        Log.d("$TAG Computing message contents list")
        text.postValue(Spannable.Factory.getInstance().newSpannable(""))
        filesList.postValue(arrayListOf())

        var displayableContentFound = false
        var filesContentCount = 0
        val filesPath = arrayListOf<FileModel>()

        val contents = chatMessage.contents
        allFilesDownloaded = true
        for (content in contents) {
            val isFileEncrypted = content.isFileEncrypted

            if (content.isIcalendar) {
                Log.d("$TAG Found iCal content")
                parseConferenceInvite(content)

                displayableContentFound = true
            } else if (content.isText && !content.isFile) {
                Log.d("$TAG Found plain text content")
                computeTextContent(content)

                displayableContentFound = true
            } else if (content.isVoiceRecording) {
                Log.d("$TAG Found voice recording content")
                isVoiceRecord.postValue(true)
                voiceRecordPath = content.filePath ?: ""

                val duration = content.fileDuration
                voiceRecordingDuration.postValue(duration)

                val formattedDuration = SimpleDateFormat(
                    "mm:ss",
                    Locale.getDefault()
                ).format(duration) // duration is in ms
                formattedVoiceRecordingDuration.postValue(formattedDuration)
                displayableContentFound = true
            } else {
                if (content.isFile) {
                    Log.d("$TAG Found file content with type [${content.type}/${content.subtype}]")
                    filesContentCount += 1

                    checkAndRepairFilePathIfNeeded(content)

                    val path = if (isFileEncrypted) {
                        Log.i(
                            "$TAG [VFS] Content is encrypted, requesting plain file path for file [${content.filePath}]"
                        )
                        content.exportPlainFile()
                    } else {
                        content.filePath ?: ""
                    }
                    val name = content.name ?: ""
                    if (path.isNotEmpty()) {
                        Log.d(
                            "$TAG Found file ready to be displayed [$path] with MIME [${content.type}/${content.subtype}] for message [${chatMessage.messageId}]"
                        )

                        val fileSize = content.fileSize.toLong()
                        when (content.type) {
                            "image", "video" -> {
                                val fileModel = FileModel(path, name, fileSize, isFileEncrypted) { model ->
                                    onContentClicked?.invoke(model.file)
                                }
                                filesPath.add(fileModel)

                                displayableContentFound = true
                            }
                            else -> {
                                val fileModel = FileModel(path, name, fileSize, isFileEncrypted) { model ->
                                    onContentClicked?.invoke(model.file)
                                }
                                filesPath.add(fileModel)

                                displayableContentFound = true
                            }
                        }
                    } else {
                        Log.e("$TAG No path found for File Content!")
                    }
                } else if (content.isFileTransfer) {
                    Log.d(
                        "$TAG Found file content (not downloaded yet) with type [${content.type}/${content.subtype}] and name [${content.name}]"
                    )
                    allFilesDownloaded = false
                    filesContentCount += 1
                    val name = content.name ?: ""
                    if (name.isNotEmpty()) {
                        val fileModel = if (isOutgoing && chatMessage.isFileTransferInProgress) {
                            val path = content.filePath ?: ""
                            FileModel(path, name, content.fileSize.toLong(), isFileEncrypted, false) { model ->
                                onContentClicked?.invoke(model.file)
                            }
                        } else {
                            FileModel(name, name, content.fileSize.toLong(), isFileEncrypted, true) { model ->
                                downloadContent(model, content)
                            }
                        }
                        filesPath.add(fileModel)

                        displayableContentFound = true
                    } else {
                        Log.e("$TAG No name found for FileTransfer Content!")
                    }
                } else {
                    Log.w("$TAG Content [${content.name}] is not a File")
                }
            }
        }

        filesList.postValue(filesPath)

        if (!displayableContentFound) { // Temporary workaround to prevent empty bubbles
            val describe = LinphoneUtils.getTextDescribingMessage(chatMessage)
            Log.w(
                "$TAG No displayable content found, generating text based description [$describe]"
            )
            val spannable = Spannable.Factory.getInstance().newSpannable(describe)
            text.postValue(spannable)
        }
    }

    @WorkerThread
    private fun downloadContent(model: FileModel, content: Content) {
        Log.d("$TAG Starting downloading content for file [${model.fileName}]")

        if (content.filePath.orEmpty().isEmpty()) {
            val contentName = content.name
            if (contentName != null) {
                val isImage = FileUtils.isExtensionImage(contentName)
                val file = FileUtils.getFileStoragePath(contentName, isImage)
                content.filePath = file.path
                Log.i(
                    "$TAG File [$contentName] will be downloaded at [${content.filePath}]"
                )

                model.transferProgress.postValue(0)
                transferringFileModel = model
                chatMessage.downloadContent(content)
            } else {
                Log.e("$TAG Content name is null, can't download it!")
            }
        }
    }

    @WorkerThread
    private fun updateReactionsList() {
        var reactionsList = ""
        val allReactions = chatMessage.reactions

        var sameReactionTwiceOrMore = false
        if (allReactions.isNotEmpty()) {
            for (reaction in allReactions) {
                val body = reaction.body
                if (!reactionsList.contains(body)) {
                    reactionsList += body
                } else {
                    sameReactionTwiceOrMore = true
                }
            }

            if (sameReactionTwiceOrMore) {
                val count = allReactions.size.toString()
                reactionsList += " $count"
            }
        }

        Log.d("$TAG Reactions for message [$id] are [$reactionsList]")
        reactions.postValue(reactionsList)
    }

    @WorkerThread
    private fun computeTextContent(content: Content) {
        val textContent = content.utf8Text.orEmpty().trim()
        val spannableBuilder = SpannableStringBuilder(textContent)

        // Check for mentions
        val chatRoom = chatMessage.chatRoom
        val matcher = Pattern.compile(MENTION_REGEXP).matcher(textContent)
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val source = textContent.subSequence(start + 1, end) // +1 to remove @
            Log.d("$TAG Found mention [$source]")

            // Find address matching username
            val address = if (chatRoom.localAddress.username == source) {
                coreContext.core.accountList.find {
                    it.params.identityAddress?.username == source
                }?.params?.identityAddress
            } else if (chatRoom.peerAddress.username == source) {
                chatRoom.peerAddress
            } else {
                chatRoom.participants.find {
                    it.address.username == source
                }?.address
            }
            // Find display name for address
            if (address != null) {
                val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(
                    address
                )
                val friend = avatarModel.friend
                val displayName = friend.name ?: LinphoneUtils.getDisplayName(address)
                Log.d(
                    "$TAG Using display name [$displayName] instead of username [$source]"
                )

                spannableBuilder.replace(start, end, "@$displayName")
                val span = PatternClickableSpan.StyledClickableSpan(
                    object :
                        SpannableClickedListener {
                        override fun onSpanClicked(text: String) {
                            val friendRefKey = friend.refKey ?: ""
                            Log.i(
                                "$TAG Clicked on [$text] span, matching friend ref key is [$friendRefKey]"
                            )
                            if (friendRefKey.isNotEmpty()) {
                                onContactClicked?.invoke(friendRefKey)
                            }
                        }
                    }
                )
                spannableBuilder.setSpan(
                    span,
                    start,
                    start + displayName.length + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // Add clickable span for SIP URIs
        text.postValue(
            PatternClickableSpan()
                .add(
                    Pattern.compile(
                        SIP_URI_REGEXP
                    ),
                    object : SpannableClickedListener {
                        @UiThread
                        override fun onSpanClicked(text: String) {
                            coreContext.postOnCoreThread {
                                Log.i("$TAG Clicked on SIP URI: $text")
                                val address = coreContext.core.interpretUrl(text, false)
                                if (address != null) {
                                    coreContext.startAudioCall(address)
                                } else {
                                    Log.w("$TAG Failed to parse [$text] as SIP URI")
                                }
                            }
                        }
                    }
                )
                .add(
                    Pattern.compile(
                        HTTP_LINK_REGEXP
                    ),
                    object : SpannableClickedListener {
                        override fun onSpanClicked(text: String) {
                            Log.i("$TAG Clicked on web URL: $text")
                            onWebUrlClicked?.invoke(text)
                        }
                    }
                )
                .build(spannableBuilder)
        )
    }

    @WorkerThread
    private fun parseConferenceInvite(content: Content) {
        val conferenceInfo = Factory.instance().createConferenceInfoFromIcalendarContent(content)
        val conferenceAddress = conferenceInfo?.uri
        val conferenceUri = conferenceAddress?.asStringUriOnly()
        if (conferenceInfo != null && conferenceAddress != null) {
            Log.i(
                "$TAG Found conference info with URI [$conferenceUri] and subject [${conferenceInfo.subject}]"
            )
            meetingConferenceUri = conferenceAddress

            meetingSubject.postValue(conferenceInfo.subject)
            meetingDescription.postValue(conferenceInfo.description)

            meetingUpdated.postValue(conferenceInfo.state == ConferenceInfo.State.Updated)
            meetingCancelled.postValue(conferenceInfo.state == ConferenceInfo.State.Cancelled)

            val timestamp = conferenceInfo.dateTime
            val duration = conferenceInfo.duration
            val date = TimestampUtils.toString(
                timestamp,
                onlyDate = true,
                shortDate = false,
                hideYear = false
            )
            val startTime = TimestampUtils.timeToString(timestamp)
            val end = timestamp + duration
            val endTime = TimestampUtils.timeToString(end)
            meetingDate.postValue(date)
            meetingTime.postValue("$startTime - $endTime")

            meetingDay.postValue(TimestampUtils.dayOfWeek(timestamp))
            meetingDayNumber.postValue(TimestampUtils.dayOfMonth(timestamp))

            var count = 0
            for (info in conferenceInfo.participantInfos) {
                count += 1
            }
            meetingParticipants.postValue(
                AppUtils.getStringWithPlural(
                    R.plurals.conference_participants_list_title,
                    count,
                    "$count"
                )
            )

            meetingFound.postValue(true)
        }
    }

    @WorkerThread
    private fun initVoiceRecordPlayer() {
        if (!::voiceRecordPath.isInitialized) {
            Log.e("$TAG No voice record path was set!")
            return
        }

        Log.i("$TAG Creating player for voice record")

        val playbackSoundCard = AudioUtils.getAudioPlaybackDeviceIdForCallRecordingOrVoiceMessage()
        Log.i(
            "$TAG Using device $playbackSoundCard to make the voice message playback"
        )

        val localPlayer = coreContext.core.createLocalPlayer(playbackSoundCard, null, null)
        if (localPlayer != null) {
            voiceRecordPlayer = localPlayer
        } else {
            Log.e("$TAG Couldn't create local player!")
            return
        }
        voiceRecordPlayer.addListener(playerListener)
        Log.i("$TAG Voice record player created")

        val path = voiceRecordPath
        Log.i("$TAG Opening voice record file [$path]")
        voiceRecordPlayer.open(path)

        val duration = voiceRecordPlayer.duration
        voiceRecordingDuration.postValue(duration)
        val formattedDuration = SimpleDateFormat("mm:ss", Locale.getDefault()).format(duration) // duration is in ms
        formattedVoiceRecordingDuration.postValue(formattedDuration)
    }

    @WorkerThread
    private fun startVoiceRecordPlayer() {
        if (isPlayerClosed()) {
            Log.w("$TAG Player closed, let's open it first")
            initVoiceRecordPlayer()
        }

        val lowMediaVolume = AudioUtils.isMediaVolumeLow(coreContext.context)
        if (lowMediaVolume) {
            Log.w("$TAG Media volume is low, notifying user as they may not hear voice message")
            val message = AppUtils.getString(R.string.toast_low_media_volume)
            onRedToastToShow?.invoke(Pair(message, R.drawable.speaker_slash))
        }

        if (voiceRecordAudioFocusRequest == null) {
            voiceRecordAudioFocusRequest = AudioUtils.acquireAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context
            )
        }

        Log.i("$TAG Playing voice record")
        isPlayingVoiceRecord.postValue(true)
        voiceRecordPlayer.start()

        playerTickerFlow().onEach {
            coreContext.postOnCoreThread {
                voiceRecordPlayerPosition.postValue(voiceRecordPlayer.currentPosition)
            }
        }.launchIn(scope)
    }

    @WorkerThread
    private fun pauseVoiceRecordPlayer() {
        if (!isPlayerClosed()) {
            Log.i("$TAG Pausing voice record")
            voiceRecordPlayer.pause()
        }

        val request = voiceRecordAudioFocusRequest
        if (request != null) {
            AudioUtils.releaseAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context,
                request
            )
            voiceRecordAudioFocusRequest = null
        }

        isPlayingVoiceRecord.postValue(false)
    }

    @WorkerThread
    private fun isPlayerClosed(): Boolean {
        return !::voiceRecordPlayer.isInitialized || voiceRecordPlayer.state == Player.State.Closed
    }

    @WorkerThread
    private fun stopVoiceRecordPlayer() {
        if (!isPlayerClosed()) {
            Log.i("$TAG Stopping voice record")
            voiceRecordPlayer.pause()
            voiceRecordPlayer.seek(0)
            voiceRecordPlayerPosition.postValue(0)
            voiceRecordPlayer.close()
        }

        voiceRecordPlayerPosition.postValue(0)
        isPlayingVoiceRecord.postValue(false)

        val request = voiceRecordAudioFocusRequest
        if (request != null) {
            AudioUtils.releaseAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context,
                request
            )
            voiceRecordAudioFocusRequest = null
        }

        isPlayingVoiceRecord.postValue(false)
    }

    private fun playerTickerFlow() = flow {
        while (isPlayingVoiceRecord.value == true) {
            emit(Unit)
            delay(50)
        }
    }

    @WorkerThread
    private fun checkAndRepairFilePathIfNeeded(content: Content): String {
        val path = content.filePath ?: ""
        if (path.isEmpty()) return ""
        val name = content.name ?: ""
        if (name.isEmpty()) return ""

        val extension = FileUtils.getExtensionFromFileName(path)
        if (extension.contains("/")) {
            Log.w(
                "$TAG Weird extension [$extension] found for file [$path], trying with file name [$name]"
            )
            val fileExtension = FileUtils.getExtensionFromFileName(name)
            if (!fileExtension.contains("/")) {
                Log.w("$TAG File extension [$fileExtension] seems better, renaming file")
                val newPath = FileUtils.renameFile(path, name)
                if (newPath.isNotEmpty()) {
                    content.filePath = newPath
                    Log.w("$TAG File [$path] has been renamed [${content.filePath}]")
                    return newPath
                } else {
                    Log.e("$TAG Failed to rename file!")
                }
            }
        }

        return ""
    }
}
