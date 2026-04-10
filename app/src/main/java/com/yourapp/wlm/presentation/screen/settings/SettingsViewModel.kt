package com.yourapp.wlm.presentation.screen.settings

import androidx.lifecycle.ViewModel
import com.yourapp.wlm.domain.model.PresenceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class SettingsUiState(
    val useTcpConnection: Boolean = true,
    val enableMessageNotifications: Boolean = true,
    val enableNudgeNotifications: Boolean = true,
    val enablePresenceNotifications: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val privacyAllowAll: Boolean = true,
    val appVersion: String = "1.0"
)

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onTcpToggle(useTcp: Boolean) {
        _uiState.value = _uiState.value.copy(useTcpConnection = useTcp)
    }

    fun onMessageNotificationsToggle(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enableMessageNotifications = enabled)
    }

    fun onNudgeNotificationsToggle(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enableNudgeNotifications = enabled)
    }

    fun onPresenceNotificationsToggle(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enablePresenceNotifications = enabled)
    }

    fun onThemeChange(theme: ThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = theme)
    }

    fun onPrivacyChange(allowAll: Boolean) {
        _uiState.value = _uiState.value.copy(privacyAllowAll = allowAll)
    }
}
