package top.mattuy.oneminute.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mattuy.oneminute.data.AppCatalog
import top.mattuy.oneminute.data.InstalledApp
import top.mattuy.oneminute.data.SettingsStore
import top.mattuy.oneminute.data.WaitingSettings

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SettingsStore(application)
    private val mutableSettings = MutableStateFlow<WaitingSettings?>(null)
    private val mutableApps = MutableStateFlow<List<InstalledApp>?>(null)
    private val mutableError = MutableStateFlow<String?>(null)
    val settings = mutableSettings.asStateFlow()
    val apps = mutableApps.asStateFlow()
    val error = mutableError.asStateFlow()

    init {
        viewModelScope.launch {
            try { store.settings.collect { mutableSettings.value = it } }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { fail("无法读取设置，请关闭应用后重试", error) }
        }
        refreshApps()
    }
    fun refreshApps() = viewModelScope.launch {
        try { mutableApps.value = withContext(Dispatchers.IO) { AppCatalog.load(getApplication()) } }
        catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { fail("无法读取应用列表，请重试", error) }
    }
    fun select(packageName: String, selected: Boolean) = viewModelScope.launch {
        try { store.setSelected(packageName, selected) }
        catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { fail("保存失败，应用选择未更改", error) }
    }
    fun setSeconds(seconds: Int) = viewModelScope.launch {
        try { store.setSeconds(seconds) }
        catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { fail("保存失败，等待时长未更改", error) }
    }
    fun dismissError() { mutableError.value = null }
    private fun fail(message: String, error: Exception) {
        Log.e("OneMinute.Settings", message, error); mutableError.value = message
    }
}
