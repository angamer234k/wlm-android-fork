package com.yourapp.wlm.domain.model

data class ContactGroup(
    val groupId: String,
    val name: String,
    val sortOrder: Int = 0,
    val contacts: List<Contact> = emptyList(),
    val isExpanded: Boolean = true
)
