package com.cibertec.sentinela.models

import java.io.Serializable

data class FamilyMemberDto(
    val userId: Long,
    val fullName: String?,
    val phone: String?,
    val latitude: Double?,
    val longitude: Double?,
    val batteryLevel: Int?,
    val isCharging: Boolean?,
    val isEmergencyActive: Boolean?,
    val currentStatus: String?,
    val lastPing: String?,
    val isAdmin: Boolean?
) : Serializable

data class FamilyGroupDto(
    val groupCode: String,
    val groupName: String,
    val adminUserId: Long,
    val adminName: String?,
    val createdAt: String?,
    val members: List<FamilyMemberDto>
) : Serializable

data class CreateFamilyGroupRequest(
    val userId: Long,
    val groupName: String
)

data class JoinFamilyGroupRequest(
    val userId: Long,
    val groupCode: String
)

data class FamilyPingRequest(
    val userId: Long,
    val latitude: Double?,
    val longitude: Double?,
    val batteryLevel: Int?,
    val isCharging: Boolean?,
    val isEmergencyActive: Boolean?,
    val statusZone: String?
)
