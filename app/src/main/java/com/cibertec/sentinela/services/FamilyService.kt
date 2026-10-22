package com.cibertec.sentinela.services

import com.cibertec.sentinela.models.*
import retrofit2.http.*

interface FamilyService {

    @POST("api/family/create")
    suspend fun createGroup(@Body request: CreateFamilyGroupRequest): FamilyGroupDto

    @POST("api/family/join")
    suspend fun joinGroup(@Body request: JoinFamilyGroupRequest): FamilyGroupDto

    @POST("api/family/ping")
    suspend fun sendPing(@Body request: FamilyPingRequest): FamilyMemberDto

    @GET("api/family/my-group")
    suspend fun getMyGroup(@Query("userId") userId: Long): FamilyGroupDto

    @GET("api/family/members")
    suspend fun getMembers(@Query("userId") userId: Long): List<FamilyMemberDto>

    @POST("api/family/leave")
    suspend fun leaveGroup(@Query("userId") userId: Long): Map<String, String>
}
