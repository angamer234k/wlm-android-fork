package com.yourapp.wlm.presentation.screen.contactlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.wlm.domain.model.Contact
import com.yourapp.wlm.domain.model.ContactGroup
import com.yourapp.wlm.domain.model.PresenceStatus
import com.yourapp.wlm.domain.repository.ConnectionState
import com.yourapp.wlm.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactListUiState(
    val groups: List<ContactGroup> = emptyList(),
    val userStatus: PresenceStatus = PresenceStatus.ONLINE,
    val userDisplayName: String = "",
    val userPersonalMessage: String = "",
    val userEmail: String = "",
    val isLoading: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val showAddContactDialog: Boolean = false,
    val showStatusSelector: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ContactListViewModel @Inject constructor(
    private val getContactListUseCase: GetContactListUseCase,
    private val changeStatusUseCase: ChangeStatusUseCase,
    private val setPersonalMessageUseCase: SetPersonalMessageUseCase,
    private val addContactUseCase: AddContactUseCase,
    private val removeContactUseCase: RemoveContactUseCase,
    private val blockContactUseCase: BlockContactUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactListUiState())
    val uiState: StateFlow<ContactListUiState> = _uiState.asStateFlow()

    private val _logoutEvent = MutableStateFlow(false)
    val logoutEvent = _logoutEvent.asStateFlow()

    init {
        viewModelScope.launch {
            getContactListUseCase().collect { groups ->
                _uiState.value = _uiState.value.copy(groups = groups)
            }
        }
    }

    fun onChangeStatus(status: PresenceStatus) {
        viewModelScope.launch {
            val result = changeStatusUseCase(status)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(userStatus = status)
            }
        }
    }

    fun onPersonalMessageChange(pm: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(userPersonalMessage = pm)
        }
    }

    fun savePersonalMessage() {
        viewModelScope.launch {
            val pm = _uiState.value.userPersonalMessage
            val result = setPersonalMessageUseCase(pm)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun showAddContactDialog() {
        _uiState.value = _uiState.value.copy(showAddContactDialog = true)
    }

    fun hideAddContactDialog() {
        _uiState.value = _uiState.value.copy(showAddContactDialog = false)
    }

    fun addContact(email: String) {
        viewModelScope.launch {
            val result = addContactUseCase(email)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(showAddContactDialog = false)
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun removeContact(email: String) {
        viewModelScope.launch {
            val result = removeContactUseCase(email)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun blockContact(email: String) {
        viewModelScope.launch {
            val result = blockContactUseCase(email)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _logoutEvent.value = true
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun updateConnectionState(state: ConnectionState) {
        _uiState.value = _uiState.value.copy(connectionState = state)
    }

    fun toggleStatusSelector() {
        _uiState.value = _uiState.value.copy(showStatusSelector = !_uiState.value.showStatusSelector)
    }
}
