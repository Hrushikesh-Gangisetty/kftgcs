package com.example.kftgcs.parammanagement

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ParamManagementViewModel : ViewModel() {

    private val _loginState = MutableLiveData<ParamLoginState>(ParamLoginState.Idle)
    val loginState: LiveData<ParamLoginState> = _loginState

    private val _isLoggedIn = MutableLiveData(false)
    val isLoggedIn: LiveData<Boolean> = _isLoggedIn

    companion object {
        private const val DUMMY_EMAIL = "kftparam.com"
        private const val DUMMY_PASSWORD = "Kft@1234"
    }

    fun login(email: String, password: String) {
        if (email.isBlank()) {
            _loginState.value = ParamLoginState.Error("Email is required")
            return
        }
        if (password.isBlank()) {
            _loginState.value = ParamLoginState.Error("Password is required")
            return
        }
        _loginState.value = ParamLoginState.Loading
        // Dummy authentication – real endpoint to be wired later
        if (email.trim() == DUMMY_EMAIL && password == DUMMY_PASSWORD) {
            _isLoggedIn.value = true
            _loginState.value = ParamLoginState.Success
        } else {
            _loginState.value = ParamLoginState.Error("Invalid credentials")
        }
    }

    fun logout() {
        _isLoggedIn.value = false
        _loginState.value = ParamLoginState.Idle
    }

    fun resetState() {
        if (_loginState.value is ParamLoginState.Error) {
            _loginState.value = ParamLoginState.Idle
        }
    }
}

sealed class ParamLoginState {
    object Idle : ParamLoginState()
    object Loading : ParamLoginState()
    object Success : ParamLoginState()
    data class Error(val message: String) : ParamLoginState()
}

