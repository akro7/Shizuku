package moe.shizuku.manager.hide

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * RootHideViewModel — بيشغل الـ scan في background ويعرض النتائج.
 */
class RootHideViewModel : ViewModel() {

    enum class ScanState { IDLE, SCANNING, DONE, ERROR }

    private val _scanState = MutableLiveData(ScanState.IDLE)
    val scanState: LiveData<ScanState> = _scanState

    private val _hideStatus = MutableLiveData<RootHideManager.HideStatus?>()
    val hideStatus: LiveData<RootHideManager.HideStatus?> = _hideStatus

    private val _errorMsg = MutableLiveData<String?>()
    val errorMsg: LiveData<String?> = _errorMsg

    fun runScan(context: Context) {
        if (_scanState.value == ScanState.SCANNING) return
        _scanState.value = ScanState.SCANNING
        _hideStatus.value = null
        _errorMsg.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = RootHideManager.runFullScan(context)
                _hideStatus.postValue(result)
                _scanState.postValue(ScanState.DONE)
            } catch (e: Exception) {
                _errorMsg.postValue(e.message ?: "Unknown error")
                _scanState.postValue(ScanState.ERROR)
            }
        }
    }

    fun reset() {
        _scanState.value = ScanState.IDLE
        _hideStatus.value = null
        _errorMsg.value = null
    }
}
