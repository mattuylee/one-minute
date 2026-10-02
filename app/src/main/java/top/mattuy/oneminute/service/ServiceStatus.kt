package top.mattuy.oneminute.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ServiceStatus {
    private val mutableConnected = MutableStateFlow(false)
    private val mutableError = MutableStateFlow<String?>(null)
    val connected = mutableConnected.asStateFlow()
    val error = mutableError.asStateFlow()
    fun connected(value: Boolean) { mutableConnected.value = value }
    fun error(value: String?) { mutableError.value = value }
}
