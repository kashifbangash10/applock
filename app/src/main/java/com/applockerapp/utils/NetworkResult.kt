package com.applockerapp.utils

/**
 * Sealed class representing network/data operation states.
 * Used across ViewModels to communicate result states to the UI.
 *
 * Usage in ViewModel:
 *   private val _state = MutableStateFlow<NetworkResult<List<Item>>>(NetworkResult.Loading)
 *   val state: StateFlow<NetworkResult<List<Item>>> = _state.asStateFlow()
 *
 * Usage in Fragment:
 *   when (result) {
 *       is NetworkResult.Success -> showData(result.data)
 *       is NetworkResult.Error   -> showError(result.message)
 *       is NetworkResult.Loading -> showLoader()
 *   }
 */
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val code: Int? = null) : NetworkResult<Nothing>()
    data object Loading : NetworkResult<Nothing>()
}
