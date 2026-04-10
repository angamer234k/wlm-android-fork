package com.yourapp.wlm.domain.usecase

import com.yourapp.wlm.domain.model.ContactGroup
import com.yourapp.wlm.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetContactListUseCase @Inject constructor(
    private val contactRepository: ContactRepository
) {
    operator fun invoke(): Flow<List<ContactGroup>> = contactRepository.getGroupedContacts()
}
