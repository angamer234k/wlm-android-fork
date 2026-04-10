package com.yourapp.wlm.presentation.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.wlm.domain.model.PresenceStatus
import com.yourapp.wlm.domain.model.UserProfile
import com.yourapp.wlm.domain.repository.AuthRepository
import com.yourapp.wlm.domain.usecase.ChangeStatusUseCase
import com.yourapp.wlm.domain.usecase.SetPersonalMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val userProfile: UserProfile? = null,
    val displayName: String = "",
    val personalMessage: String = "",
    val status: PresenceStatus = PresenceStatus.ONLINE,
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val changeStatusUseCase: ChangeStatusUseCase,
    private val setPersonalMessageUseCase: SetPersonalMessageUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.getUserProfile().collect { profile ->
                if (profile != null) {
                    _uiState.value = ProfileUiState(
                        userProfile = profile,
                        displayName = profile.displayName,
                        personalMessage = profile.personalMessage,
                        status = profile.status
                    )
                }
            }
        }
    }

    fun onDisplayNameChange(name: String) {
        _uiState.value = _uiState.value.copy(displayName = name)
    }

    fun onPersonalMessageChange(pm: String) {
        _uiState.value = _uiState.value.copy(personalMessage = pm)
    }

    fun onStatusChange(status: PresenceStatus) {
        _uiState.value = _uiState.value.copy(status = status)
        viewModelScope.launch {
            changeStatusUseCase(status)
        }
    }

    fun startEditing() {
        _uiState.value = _uiState.value.copy(isEditing = true)
    }

    fun cancelEditing() {
        val profile = _uiState.value.userProfile
        _uiState.value = _uiState.value.copy(
            isEditing = false,
            displayName = profile?.displayName ?: "",
            personalMessage = profile?.personalMessage ?: ""
        )
    }

    fun saveProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val pmResult = setPersonalMessageUseCase(_uiState.value.personalMessage)
            if (pmResult.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isEditing = false,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = pmResult.exceptionOrNull()?.message
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
