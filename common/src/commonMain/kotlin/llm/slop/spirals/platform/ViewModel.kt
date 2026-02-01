package llm.slop.spirals.platform

import kotlinx.coroutines.CoroutineScope

expect abstract class PlatformViewModel() {
    val viewModelScope: CoroutineScope
}
