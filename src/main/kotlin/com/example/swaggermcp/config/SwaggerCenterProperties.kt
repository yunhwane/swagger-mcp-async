package com.example.swaggermcp.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "swagger-center")
data class SwaggerCenterProperties(
    val services: List<ServiceEntry> = emptyList(),
    val webhook: WebhookProperties = WebhookProperties(),
    val cache: CacheProperties = CacheProperties()
) {
    data class ServiceEntry(
        val name: String,
        val url: String,
        val swaggerPath: String = "/v3/api-docs",
        val description: String = "",
        val repository: String = ""
    )

    data class WebhookProperties(
        val secret: String = "",
        val enabled: Boolean = true
    )

    data class CacheProperties(
        val ttlMinutes: Long = 60
    )
}
