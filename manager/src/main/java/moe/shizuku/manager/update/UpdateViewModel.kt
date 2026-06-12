package moe.shizuku.manager.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val _result = MutableLiveData<UpdateResult>()
    val result: LiveData<UpdateResult> = _result

    private val _checking = MutableLiveData(false)
    val checking: LiveData<Boolean> = _checking

    fun checkForUpdate() {
        if (_checking.value == true) return
        _checking.value = true
        viewModelScope.launch {
            _result.value = UpdateChecker.checkForUpdate(getApplication())
            _checking.value = false
        }
    }
}
