package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DspSettingsDao {
    @Query("SELECT * FROM dsp_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<DspSettings?>

    @Query("SELECT * FROM dsp_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): DspSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: DspSettings)
}
