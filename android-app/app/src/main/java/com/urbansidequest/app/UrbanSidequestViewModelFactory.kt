package com.urbansidequest.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.urbansidequest.app.data.auth.AuthRepository
import com.urbansidequest.app.data.route.RouteRepository

internal class UrbanSidequestViewModelFactory(
    private val context: Context,
    private val authRepository: AuthRepository,
    private val routeRepository: RouteRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UrbanSidequestViewModel::class.java)) {
            return UrbanSidequestViewModel(
                context = context,
                authRepository = authRepository,
                routeRepository = routeRepository
            ) as T
        }
        throw IllegalArgumentException("未知的 ViewModel 类型: ${modelClass.name}")
    }
}
