package com.trigeo.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OutingDao {
    @Query("SELECT * FROM outings ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<OutingEntity>>

    @Query("SELECT * FROM outings WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): OutingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(outing: OutingEntity)

    @Query("UPDATE outings SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String?)

    @Query("DELETE FROM outings WHERE id = :id")
    suspend fun delete(id: String)
}
