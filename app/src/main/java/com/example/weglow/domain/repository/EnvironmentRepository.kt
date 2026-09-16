package com.example.weglow.domain.repository

import com.example.weglow.domain.model.EnvironmentInfo

interface EnvironmentRepository {
    suspend fun getEnvironmentForLocation(latitude: Double, longitude: Double, forceRefresh: Boolean = false): Result<EnvironmentInfo>
    suspend fun getLatestEnvironment(): EnvironmentInfo?
}
