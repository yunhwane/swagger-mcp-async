package com.example.swaggermcp.controller

import com.example.swaggermcp.config.SwaggerCenterProperties
import com.example.swaggermcp.service.SwaggerFetchService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@RestController
@RequestMapping("/webhook")
class WebhookController(
    private val swaggerFetchService: SwaggerFetchService,
    private val properties: SwaggerCenterProperties,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/github")
    suspend fun handleGithubWebhook(
        @RequestBody body: String,
        @RequestHeader("X-Hub-Signature-256", required = false) signature: String?,
        @RequestHeader("X-GitHub-Event", required = false) event: String?
    ): ResponseEntity<Map<String, String>> {
        if (!properties.webhook.enabled) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("status" to "error", "message" to "Webhook is disabled"))
        }

        // Verify HMAC signature if secret is configured
        if (properties.webhook.secret.isNotBlank()) {
            if (signature == null || !verifySignature(body, signature, properties.webhook.secret)) {
                log.warn("Webhook signature verification failed")
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(mapOf("status" to "error", "message" to "Invalid signature"))
            }
        }

        // Only process push events
        if (event != null && event != "push") {
            log.info("Ignoring GitHub event: {}", event)
            return ResponseEntity.ok(mapOf("status" to "ok", "message" to "Event '$event' ignored"))
        }

        // Extract repository full name from payload
        val payload = objectMapper.readValue(body, Map::class.java)
        val repository = payload["repository"] as? Map<*, *>
        val repoFullName = repository?.get("full_name") as? String ?: ""

        log.info("Received GitHub push webhook for repository: {}", repoFullName)

        // Find matching services by repository name
        val matchedServices = properties.services.filter { service ->
            service.repository.isNotBlank() && service.repository == repoFullName
        }

        if (matchedServices.isNotEmpty()) {
            // Refresh only matched services
            matchedServices.forEach { service ->
                log.info("Refreshing cache for service: {} (repo: {})", service.name, repoFullName)
                swaggerFetchService.refreshCache(service.name)
            }
            return ResponseEntity.ok(mapOf(
                "status" to "ok",
                "message" to "Cache refreshed for services: ${matchedServices.map { it.name }}"
            ))
        } else {
            // No repo mapping found — refresh all
            log.info("No repository mapping found for '{}', refreshing all services", repoFullName)
            swaggerFetchService.refreshCache()
            return ResponseEntity.ok(mapOf(
                "status" to "ok",
                "message" to "Cache refreshed for all services"
            ))
        }
    }

    private fun verifySignature(payload: String, signature: String, secret: String): Boolean {
        return try {
            val algorithm = "HmacSHA256"
            val mac = Mac.getInstance(algorithm)
            mac.init(SecretKeySpec(secret.toByteArray(), algorithm))
            val hash = mac.doFinal(payload.toByteArray())
            val expected = "sha256=" + hash.joinToString("") { "%02x".format(it) }
            // Constant-time comparison to prevent timing attacks
            expected.length == signature.length && expected.indices.all { expected[it] == signature[it] }
        } catch (e: Exception) {
            log.error("Signature verification error: {}", e.message)
            false
        }
    }
}
