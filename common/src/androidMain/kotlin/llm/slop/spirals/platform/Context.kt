package llm.slop.spirals.platform

import android.content.Context

actual typealias AppContext = Context

private lateinit var appContext: AppContext

fun initAppContext(context: Context) {
    appContext = context
}

actual fun getAppContext(): AppContext {
    return appContext
}
