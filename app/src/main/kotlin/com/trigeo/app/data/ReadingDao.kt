package com.trigeo.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    @Query("SELECT * FROM readings WHERE outingId = :outingId ORDER BY createdAtEpochMs ASC")
    fun observeByOuting(outingId: String): Flow<List<ReadingEntity>>

    @Query("SELECT * FROM readings WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ReadingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reading: ReadingEntity)

    @Query("UPDATE readings SET visible = :visible WHERE id = :id")
    suspend fun setVisible(id: String, visible: Boolean)

    @Query("UPDATE readings SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String?)

    @Query("DELETE FROM readings WHERE id = :id")
    suspend fun delete(id: String)
}
