package dev.ezaz.manazone.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import dev.ezaz.manazone.dnd.DndOwnership
import dev.ezaz.manazone.session.FocusSession
import dev.ezaz.manazone.session.SessionStatus
import dev.ezaz.manazone.session.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "sessions", indices = [Index("status"), Index("endWallMillis")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plannedMillis: Long,
    val startWallMillis: Long,
    val anchorElapsedMillis: Long,
    val anchorBootCount: Int,
    val status: String,
    val endWallMillis: Long?,
    val focusedMillis: Long,
    val completionSeen: Boolean,
    val dndPreviousFilter: Int?,
    val dndAppliedFilter: Int?,
)

@Dao
abstract class SessionDao {
    @Query("SELECT * FROM sessions WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    abstract fun observeActive(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    abstract suspend fun active(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status = 'COMPLETED' AND completionSeen = 0 ORDER BY id DESC LIMIT 1")
    abstract fun observeUnseenCompletion(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE status != 'ACTIVE' AND endWallMillis > :wallMillis")
    abstract fun observeFinishedEndingAfter(wallMillis: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY id")
    abstract suspend fun all(): List<SessionEntity>

    @Insert
    protected abstract suspend fun insert(entity: SessionEntity): Long

    @Transaction
    open suspend fun insertIfNoActive(entity: SessionEntity): Long? =
        if (active() != null) null else insert(entity)

    @Query(
        """UPDATE sessions SET status = :status, focusedMillis = :focusedMillis,
           endWallMillis = :endWallMillis, completionSeen = :completionSeen
           WHERE id = :id AND status = 'ACTIVE'"""
    )
    abstract suspend fun finish(
        id: Long,
        status: String,
        focusedMillis: Long,
        endWallMillis: Long,
        completionSeen: Boolean,
    ): Int

    @Query("UPDATE sessions SET anchorElapsedMillis = :anchor, anchorBootCount = :boot WHERE id = :id AND status = 'ACTIVE'")
    abstract suspend fun rebase(id: Long, anchor: Long, boot: Int)

    @Query("UPDATE sessions SET dndPreviousFilter = :previous, dndAppliedFilter = :applied WHERE id = :id")
    abstract suspend fun setDnd(id: Long, previous: Int, applied: Int)

    @Query("UPDATE sessions SET completionSeen = 1 WHERE completionSeen = 0")
    abstract suspend fun markCompletionsSeen()
}

@Database(entities = [SessionEntity::class], version = 1, exportSchema = true)
abstract class ManazoneDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        fun create(context: Context): ManazoneDatabase =
            Room.databaseBuilder(context, ManazoneDatabase::class.java, "manazone.db").build()
    }
}

class RoomSessionStore(private val dao: SessionDao) : SessionStore {
    override fun observeActive() = dao.observeActive().map { it?.toDomain() }
    override fun observeUnseenCompletion() = dao.observeUnseenCompletion().map { it?.toDomain() }
    override fun observeFinishedEndingAfter(wallMillis: Long) =
        dao.observeFinishedEndingAfter(wallMillis).map { list -> list.map { it.toDomain() } }

    override suspend fun active() = dao.active()?.toDomain()
    override suspend fun insertIfNoActive(session: FocusSession) = dao.insertIfNoActive(session.toEntity())

    override suspend fun finish(
        id: Long,
        status: SessionStatus,
        focusedMillis: Long,
        endWallMillis: Long,
        completionSeen: Boolean,
    ) = dao.finish(id, status.name, focusedMillis, endWallMillis, completionSeen) == 1

    override suspend fun rebase(id: Long, anchorElapsedMillis: Long, anchorBootCount: Int) =
        dao.rebase(id, anchorElapsedMillis, anchorBootCount)

    override suspend fun setDnd(id: Long, dnd: DndOwnership) =
        dao.setDnd(id, dnd.previousFilter, dnd.appliedFilter)

    override suspend fun markCompletionsSeen() = dao.markCompletionsSeen()
}

fun SessionEntity.toDomain() = FocusSession(
    id = id,
    plannedMillis = plannedMillis,
    startWallMillis = startWallMillis,
    anchorElapsedMillis = anchorElapsedMillis,
    anchorBootCount = anchorBootCount,
    status = SessionStatus.entries.firstOrNull { it.name == status } ?: SessionStatus.CANCELLED,
    endWallMillis = endWallMillis,
    focusedMillis = focusedMillis,
    completionSeen = completionSeen,
    dnd = if (dndPreviousFilter != null && dndAppliedFilter != null) {
        DndOwnership(dndPreviousFilter, dndAppliedFilter)
    } else null,
)

fun FocusSession.toEntity() = SessionEntity(
    id = id,
    plannedMillis = plannedMillis,
    startWallMillis = startWallMillis,
    anchorElapsedMillis = anchorElapsedMillis,
    anchorBootCount = anchorBootCount,
    status = status.name,
    endWallMillis = endWallMillis,
    focusedMillis = focusedMillis,
    completionSeen = completionSeen,
    dndPreviousFilter = dnd?.previousFilter,
    dndAppliedFilter = dnd?.appliedFilter,
)
