package com.urbansidequest.app.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.urbansidequest.app.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = mutableUiState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<LoginEvent>()
    val events: SharedFlow<LoginEvent> = mutableEvents.asSharedFlow()

    fun onPhoneChange(value: String) {
        val normalized = value.filter { it.isDigit() }.take(PHONE_MAX_LENGTH)
        mutableUiState.update {
            it.copy(phone = normalized, errorMessage = null)
        }
    }

    fun onCodeChange(value: String) {
        val normalized = value.filter { it.isDigit() }.take(CODE_MAX_LENGTH)
        mutableUiState.update {
            it.copy(code = normalized, errorMessage = null)
        }
    }

    fun requestVerificationCode() {
        mutableUiState.update {
            it.copy(errorMessage = "开发阶段验证码为 246810")
        }
    }

    fun login() {
        val state = mutableUiState.value
        if (state.phone.isBlank() || state.code.isBlank()) {
            mutableUiState.update {
                it.copy(errorMessage = "请输入手机号和验证码")
            }
            return
        }

        viewModelScope.launch {
            mutableUiState.update {
                it.copy(isLoading = true, errorMessage = null)
            }
            runCatching {
                authRepository.login(phone = state.phone, code = state.code)
            }.onSuccess {
                mutableUiState.update {
                    it.copy(isLoading = false)
                }
                mutableEvents.emit(LoginEvent.LoginSuccess)
            }.onFailure { throwable ->
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "登录失败，请稍后重试"
                    )
                }
            }
        }
    }

    fun loginWithWeChat() {
        mutableUiState.update {
            it.copy(errorMessage = "微信登录暂未接入，先使用手机号验证码登录")
        }
    }

    companion object {
        private const val PHONE_MAX_LENGTH = 11
        private const val CODE_MAX_LENGTH = 6
    }
}

data class LoginUiState(
    val phone: String = "",
    val code: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val canLogin: Boolean
        get() = phone.isNotBlank() && code.isNotBlank() && !isLoading
}

sealed interface LoginEvent {
    data object LoginSuccess : LoginEvent
}

class LoginViewModelFactory(
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LoginViewModel(authRepository) as T
    }
}
