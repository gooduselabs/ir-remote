package vn.irremote.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

enum class DeviceType(val label: String) { TV("TV"), STB("Đầu thu"), FAN("Quạt"), AC("Điều hoà"), PROJECTOR("Máy chiếu"), OTHER("Khác") }

@Serializable @Entity(tableName = "rooms")
data class RoomEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Serializable @Entity(tableName = "remotes")
data class RemoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val deviceType: String, val brand: String = "", val roomId: Long? = null,
    val favorite: Boolean = false, val source: String = "",
    val acState: String = "", // JSON AcState
)

@Serializable @Entity(tableName = "buttons", indices = [Index("remoteId")])
data class ButtonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: Long, val label: String, val protocol: String,
    val address: Long = 0, val command: Long = 0, val frequency: Int = 38_000, val data: String = "",
    val position: Int = 0, val favorite: Boolean = false,
)

@Serializable data class MacroStep(val buttonId: Long, val delayMs: Long = 400)

@Serializable @Entity(tableName = "macros")
data class MacroEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val steps: String = "[]")

@Dao
interface IrDao {
    @Query("SELECT * FROM rooms ORDER BY name") fun rooms(): Flow<List<RoomEntity>>
    @Query("SELECT * FROM remotes ORDER BY favorite DESC, name") fun remotes(): Flow<List<RemoteEntity>>
    @Query("SELECT * FROM remotes WHERE id=:id") fun remote(id: Long): Flow<RemoteEntity?>
    @Query("SELECT * FROM buttons WHERE remoteId=:id ORDER BY position, id") fun buttons(id: Long): Flow<List<ButtonEntity>>
    @Query("SELECT * FROM buttons WHERE remoteId=:id ORDER BY position, id") suspend fun buttonsNow(id: Long): List<ButtonEntity>
    @Query("SELECT * FROM buttons WHERE favorite=1 ORDER BY id") suspend fun favoriteButtons(): List<ButtonEntity>
    @Query("SELECT * FROM buttons WHERE favorite=1 ORDER BY id") fun favoriteButtonsFlow(): Flow<List<ButtonEntity>>
    @Query("SELECT * FROM buttons WHERE id=:id") suspend fun button(id: Long): ButtonEntity?
    @Query("SELECT * FROM buttons") suspend fun allButtons(): List<ButtonEntity>
    @Query("SELECT * FROM macros ORDER BY name") fun macros(): Flow<List<MacroEntity>>
    @Query("SELECT * FROM rooms") suspend fun roomsNow(): List<RoomEntity>
    @Query("SELECT * FROM remotes") suspend fun remotesNow(): List<RemoteEntity>
    @Query("SELECT * FROM macros") suspend fun macrosNow(): List<MacroEntity>
    @Upsert suspend fun upsertRoom(r: RoomEntity): Long
    @Upsert suspend fun upsertRemote(r: RemoteEntity): Long
    @Upsert suspend fun upsertButton(b: ButtonEntity): Long
    @Upsert suspend fun upsertButtons(b: List<ButtonEntity>)
    @Upsert suspend fun upsertMacro(m: MacroEntity): Long
    @Upsert suspend fun upsertRooms(r: List<RoomEntity>)
    @Upsert suspend fun upsertRemotes(r: List<RemoteEntity>)
    @Upsert suspend fun upsertMacros(m: List<MacroEntity>)
    @Delete suspend fun deleteRoom(r: RoomEntity)
    @Delete suspend fun deleteButton(b: ButtonEntity)
    @Delete suspend fun deleteMacro(m: MacroEntity)
    @Query("DELETE FROM remotes WHERE id=:id") suspend fun deleteRemote(id: Long)
    @Query("DELETE FROM buttons WHERE remoteId=:id") suspend fun deleteButtonsOf(id: Long)
    @Query("UPDATE remotes SET roomId=NULL WHERE roomId=:id") suspend fun clearRoom(id: Long)
    @Query("DELETE FROM rooms") suspend fun wipeRooms()
    @Query("DELETE FROM remotes") suspend fun wipeRemotes()
    @Query("DELETE FROM buttons") suspend fun wipeButtons()
    @Query("DELETE FROM macros") suspend fun wipeMacros()

    @Transaction suspend fun restore(b: Backup) {
        wipeButtons(); wipeRemotes(); wipeRooms(); wipeMacros()
        upsertRooms(b.rooms); upsertRemotes(b.remotes); upsertButtons(b.buttons); upsertMacros(b.macros)
    }
}

@Serializable data class Backup(
    val app: String = "vn.irremote", val version: Int = 1,
    val rooms: List<RoomEntity>, val remotes: List<RemoteEntity>, val buttons: List<ButtonEntity>, val macros: List<MacroEntity>,
)

@Database(entities = [RoomEntity::class, RemoteEntity::class, ButtonEntity::class, MacroEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): IrDao
    companion object {
        fun create(c: Context) = Room.databaseBuilder(c, AppDb::class.java, "irremote.db").build()
    }
}
