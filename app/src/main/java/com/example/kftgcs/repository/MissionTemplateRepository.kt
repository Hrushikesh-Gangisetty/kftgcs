package com.example.kftgcs.repository

import com.example.kftgcs.database.MissionTemplateDao
import com.example.kftgcs.database.MissionTemplateEntity
import com.example.kftgcs.database.GridParameters
import com.divpundir.mavlink.definitions.common.MissionItemInt
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.Flow

/**
 * Repository for Mission Template data operations
 * Provides a clean API for the ViewModel to access mission template data
 */
class MissionTemplateRepository(
    private val missionTemplateDao: MissionTemplateDao
) {

    fun getAllTemplates(): Flow<List<MissionTemplateEntity>> {
        return missionTemplateDao.getAllTemplates()
    }

    suspend fun getTemplateById(id: Long): MissionTemplateEntity? {
        return missionTemplateDao.getTemplateById(id)
    }

    suspend fun getTemplateByNames(projectName: String, plotName: String): MissionTemplateEntity? {
        return missionTemplateDao.getTemplateByNames(projectName, plotName)
    }

    suspend fun saveTemplate(
        projectName: String,
        plotName: String,
        waypoints: List<MissionItemInt>,
        waypointPositions: List<LatLng>,
        isGridSurvey: Boolean = false,
        gridParameters: GridParameters? = null
    ): Result<Long> {
        return try {
            val template = MissionTemplateEntity(
                projectName = projectName.trim(),
                plotName = plotName.trim(),
                waypoints = waypoints,
                waypointPositions = waypointPositions,
                isGridSurvey = isGridSurvey,
                gridParameters = gridParameters,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val id = missionTemplateDao.insertTemplate(template)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTemplate(template: MissionTemplateEntity): Result<Unit> {
        return try {
            val updatedTemplate = template.copy(updatedAt = System.currentTimeMillis())
            missionTemplateDao.updateTemplate(updatedTemplate)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTemplate(template: MissionTemplateEntity): Result<Unit> {
        return try {
            missionTemplateDao.deleteTemplate(template)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTemplateById(id: Long): Result<Unit> {
        return try {
            missionTemplateDao.deleteTemplateById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTemplateCount(): Int {
        return missionTemplateDao.getTemplateCount()
    }
}