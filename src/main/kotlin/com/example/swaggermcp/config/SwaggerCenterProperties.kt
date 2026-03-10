package com.example.swaggermcp.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "swagger-center")
data class SwaggerCenterProperties(
    val services: List<ServiceEntry> = emptyList()
) {
    data class ServiceEntry(
        val name: String,
        val url: String,
        val swaggerPath: String = "/v3/api-docs",
        val description: String = ""
    )
}
