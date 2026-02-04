package llm.slop.spirals

import android.app.Application
import llm.slop.spirals.platform.initAppContext

class SpiralsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initAppContext(this)
    }
}
