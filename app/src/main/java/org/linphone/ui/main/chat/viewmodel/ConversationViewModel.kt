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
package org.linphone.ui.main.chat.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.ChatRoom
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.Event

class ConversationViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Conversation ViewModel]"
    }

    val showBackButton = MutableLiveData<Boolean>()

    val avatarModel = MutableLiveData<ContactAvatarModel>()

    val chatRoomFoundEvent = MutableLiveData<Event<Boolean>>()

    private lateinit var chatRoom: ChatRoom

    @UiThread
    fun findChatRoom(localSipUri: String, remoteSipUri: String) {
        coreContext.postOnCoreThread { core ->
            Log.i(
                "$TAG Looking for chat room with local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
            )

            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteAddress = Factory.instance().createAddress(remoteSipUri)
            if (localAddress != null && remoteAddress != null) {
                val found = core.searchChatRoom(
                    null,
                    localAddress,
                    remoteAddress,
                    arrayOfNulls(
                        0
                    )
                )
                if (found != null) {
                    chatRoom = found
                    configureChatRoom()
                    chatRoomFoundEvent.postValue(Event(true))
                } else {
                    Log.e("Failed to find chat room given local & remote addresses!")
                    chatRoomFoundEvent.postValue(Event(false))
                }
            } else {
                Log.e("Failed to parse local or remote SIP URI as Address!")
                chatRoomFoundEvent.postValue(Event(false))
            }
        }
    }

    @WorkerThread
    private fun configureChatRoom() {
        val address = if (chatRoom.hasCapability(ChatRoom.Capabilities.Basic.toInt())) {
            chatRoom.peerAddress
        } else {
            val firstParticipant = chatRoom.participants.firstOrNull()
            firstParticipant?.address ?: chatRoom.peerAddress
        }

        val friend = coreContext.contactsManager.findContactByAddress(address)
        if (friend != null) {
            avatarModel.postValue(ContactAvatarModel(friend))
        } else {
            val fakeFriend = coreContext.core.createFriend()
            fakeFriend.address = address
            avatarModel.postValue(ContactAvatarModel(fakeFriend))
        }
    }
}
