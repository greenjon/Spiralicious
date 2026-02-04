package llm.slop.spirals.platform

import androidx.room.ColumnInfo as RoomColumnInfo
import androidx.room.Dao as RoomDao
import androidx.room.Delete as RoomDelete
import androidx.room.Entity as RoomEntity
import androidx.room.Insert as RoomInsert
import androidx.room.OnConflictStrategy as RoomOnConflictStrategy
import androidx.room.PrimaryKey as RoomPrimaryKey
import androidx.room.Query as RoomQuery

actual typealias Entity = RoomEntity
actual typealias PrimaryKey = RoomPrimaryKey
actual typealias ColumnInfo = RoomColumnInfo
actual typealias Dao = RoomDao
actual typealias Query = RoomQuery
actual typealias Insert = RoomInsert
actual typealias Delete = RoomDelete
actual typealias OnConflictStrategy = RoomOnConflictStrategy
