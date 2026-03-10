package com.example.swaggermcp.dto

data class ServiceSummary(
    val name: String,
    val description: String,
    val url: String,
    val apiCount: Int
)

data class ApiSummary(
    val method: String,
    val path: String,
    val summary: String?,
    val tag: String?,
    val deprecated: Boolean = false
)

data class ApiDetail(
    val method: String,
    val path: String,
    val summary: String?,
    val description: String?,
    val tag: String?,
    val parameters: List<ParameterInfo>,
    val requestBody: Map<String, Any>?,
    val responses: Map<String, Any>?
)

data class ParameterInfo(
    val name: String,
    val `in`: String,
    val required: Boolean,
    val description: String?,
    val schema: Map<String, Any>?
)
