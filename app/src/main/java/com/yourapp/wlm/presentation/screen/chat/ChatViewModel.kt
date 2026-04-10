package com.yourapp.wlm.presentation.screen.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.wlm.domain.model.Contact
import com.yourapp.wlm.domain.model.Message
import com.yourapp.wlm.domain.model.PresenceStatus
import com.yourapp.wlm.domain.usecase.GetConversationHistoryUseCase
import com.yourapp.wlm.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val contactEmail: String = "",
    val contactDisplayName: String = "",
    val contactStatus: PresenceStatus = PresenceStatus.OFFLINE,
    val contactAvatarUrl: String? = null,
    val isContactTyping: Boolean = false,
    val isLoading: Boolean = false,
    val connectionLost: Boolean = false,
    val showEmojiPicker: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val getConversationHistoryUseCase: GetConversationHistoryUseCase,
    private val sendMessageUseCase: SendMessageUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun initChat(contactEmail: String) {
        _uiState.value = _uiState.value.copy(
            contactEmail = contactEmail,
            contactDisplayName = contactEmail.substringBefore("@")
        )

        viewModelScope.launch {
            getConversationHistoryUseCase(contactEmail).collect { messages ->
                _uiState.value = _uiState.value.copy(messages = messages)
            }
        }
    }

    fun onInputChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val state = _uiState.value
        if (state.inputText.isBlank()) return

        val contactEmail = state.contactEmail
        val messageBody = state.inputText

        viewModelScope.launch {
            _uiState.value = state.copy(inputText = "", isLoading = true)
            val result = sendMessageUseCase(contactEmail, messageBody)
            _uiState.value = _uiState.value.copy(isLoading = false)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(connectionLost = true)
            }
        }
    }

    fun sendNudge() {
        // Nudge is handled by the repository through a separate use case
        // For now we just show a visual indication
    }

    fun toggleEmojiPicker() {
        _uiState.value = _uiState.value.copy(showEmojiPicker = !_uiState.value.showEmojiPicker)
    }

    fun insertEmoji(emoji: String) {
        val current = _uiState.value.inputText
        _uiState.value = _uiState.value.copy(inputText = current + emoji)
    }

    fun updateContactInfo(displayName: String, status: PresenceStatus, avatarUrl: String?) {
        _uiState.value = _uiState.value.copy(
            contactDisplayName = displayName,
            contactStatus = status,
            contactAvatarUrl = avatarUrl
        )
    }

    fun setTypingIndicator(isTyping: Boolean) {
        _uiState.value = _uiState.value.copy(isContactTyping = isTyping)
    }

    fun setConnectionLost(lost: Boolean) {
        _uiState.value = _uiState.value.copy(connectionLost = lost)
    }
}
