package llm.slop.spirals.platform

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.OnConflictStrategy as RoomOnConflictStrategy


actual typealias Entity = androidx.room.Entity
actual typealias PrimaryKey = androidx.room.PrimaryKey
actual typealias Dao = androidx.room.Dao
actual typealias Query = androidx.room.Query
actual typealias Insert = androidx.room.Insert
actual typealias Delete = androidx.room.Delete

actual enum class OnConflictStrategy {
    REPLACE
}

fun OnConflictStrategy.toRoomStrategy(): RoomOnConflictStrategy {
    return when(this) {
        OnConflictStrategy.REPLACE -> RoomOnConflictStrategy.REPLACE
    }
}
