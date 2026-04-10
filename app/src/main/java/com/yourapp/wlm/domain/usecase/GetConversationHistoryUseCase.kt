package com.yourapp.wlm.domain.usecase

import com.yourapp.wlm.domain.model.Message
import com.yourapp.wlm.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetConversationHistoryUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    operator fun invoke(contactEmail: String): Flow<List<Message>> {
        return messageRepository.getMessagesForConversation(contactEmail)
    }
}
