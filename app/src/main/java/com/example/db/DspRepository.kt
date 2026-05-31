package com.example.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DspRepository(private val dao: DspSettingsDao) {

    val dspSettingsFlow: Flow<DspSettings> = dao.getSettingsFlow().map { it ?: DspSettings() }

    suspend fun getSettingsDirect(): DspSettings {
        return dao.getSettingsDirect() ?: DspSettings()
    }

    suspend fun updateSettings(settings: DspSettings) {
        dao.saveSettings(settings)
    }

    suspend fun resetSettings() {
        dao.saveSettings(DspSettings())
    }
}
