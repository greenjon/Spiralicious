package llm.slop.spirals.platform

import android.content.Context
import llm.slop.spirals.database.setDatabaseContext

fun init(context: Context) {
    setDatabaseContext(context)
}
