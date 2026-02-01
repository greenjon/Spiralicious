package llm.slop.spirals.platform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

actual abstract class PlatformViewModel : ViewModel() {
    actual val viewModelScope: CoroutineScope = this.viewModelScope
}
