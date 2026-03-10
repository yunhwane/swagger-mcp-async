package com.example.swaggermcp.service

import com.example.swaggermcp.config.SwaggerCenterProperties
import com.example.swaggermcp.dto.ApiDetail
import com.example.swaggermcp.dto.ApiSummary
import com.example.swaggermcp.dto.PagedResult
import com.example.swaggermcp.dto.ParameterInfo
import com.example.swaggermcp.dto.ServiceSummary
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactive.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

@Service
class SwaggerFetchService(
    private val webClient: WebClient,
    private val properties: SwaggerCenterProperties,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private data class CachedSpec(val spec: Map<String, Any>, val cachedAt: Instant)

    private val specCache = ConcurrentHashMap<String, CachedSpec>()

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        log.info("Application ready - warming up swagger spec cache...")
        kotlinx.coroutines.runBlocking { refreshCache() }
    }

    /**
     * Step 1: Retrieves the list of all registered services (async parallel fetch).
     */
    suspend fun getServiceList(): List<ServiceSummary> = coroutineScope {
        properties.services.map { entry ->
            async {
                val spec = getSpec(entry.name) ?: fetchAndCacheSpec(entry.name, entry.url, entry.swaggerPath)
                val cached = specCache[entry.name]
                ServiceSummary(
                    name = entry.name,
                    description = entry.description,
                    url = entry.url,
                    apiCount = countApis(spec),
                    cachedAt = cached?.cachedAt?.let { formatInstant(it) }
                )
            }
        }.awaitAll()
    }

    /**
     * Step 2: Retrieves the API list for a specific service (with pagination and tag filtering).
     */
    suspend fun getApiList(serviceName: String, tag: String? = null, page: Int = 0, size: Int = 20): PagedResult<ApiSummary> {
        val spec = getSpec(serviceName) ?: return PagedResult(emptyList(), 0, page, size, false)
        val paths = spec["paths"] as? Map<String, Any> ?: return PagedResult(emptyList(), 0, page, size, false)

        val apis = collectApis(paths, tag)
        val sorted = apis.sortedWith(compareBy({ it.tag }, { it.path }, { it.method }))
        val total = sorted.size
        val paged = sorted.drop(page * size).take(size)

        return PagedResult(
            items = paged,
            total = total,
            page = page,
            size = size,
            hasNext = (page + 1) * size < total
        )
    }

    /**
     * Step 2-1: Retrieves the tag (group) list for a specific service.
     */
    suspend fun getTagList(serviceName: String): List<Map<String, String>> {
        val spec = getSpec(serviceName) ?: return emptyList()
        val tags = spec["tags"] as? List<Map<String, Any>> ?: return emptyList()

        return tags.map { tag ->
            mapOf(
                "name" to (tag["name"] as? String ?: ""),
                "description" to (tag["description"] as? String ?: "")
            )
        }
    }

    /**
     * Step 3: Retrieves detailed information for a specific API.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun getApiDetail(serviceName: String, path: String, method: String, resolveRefs: Boolean = true): ApiDetail? {
        val spec = getSpec(serviceName) ?: return null
        val paths = spec["paths"] as? Map<String, Any> ?: return null
        val methods = paths[path] as? Map<String, Any> ?: return null
        val op = methods[method.lowercase()] as? Map<String, Any> ?: return null

        val tags = op["tags"] as? List<String>
        val parameters = (op["parameters"] as? List<Map<String, Any>>)?.map { param ->
            ParameterInfo(
                name = param["name"] as? String ?: "",
                `in` = param["in"] as? String ?: "",
                required = param["required"] as? Boolean ?: false,
                description = param["description"] as? String,
                schema = simplifySchema(param["schema"] as? Map<String, Any>)
            )
        } ?: emptyList()

        val requestBody = simplifyNode(op["requestBody"] as? Map<String, Any>)
        val responses = simplifyNode(op["responses"] as? Map<String, Any>)

        val resolvedSchemas = if (resolveRefs) {
            val collected = mutableMapOf<String, Any>()
            val visited = mutableSetOf<String>()
            if (requestBody != null) collectRefs(requestBody, spec, collected, visited)
            if (responses != null) collectRefs(responses, spec, collected, visited)
            parameters.forEach { p -> p.schema?.let { collectRefs(it, spec, collected, visited) } }
            if (collected.isNotEmpty()) collected else null
        } else null

        return ApiDetail(
            method = method.uppercase(),
            path = path,
            summary = op["summary"] as? String,
            description = op["description"] as? String,
            tag = tags?.firstOrNull(),
            parameters = parameters,
            requestBody = requestBody,
            responses = responses,
            resolvedSchemas = resolvedSchemas
        )
    }

    /**
     * Step 4: Retrieves a component schema by name.
     */
    suspend fun getComponentSchema(serviceName: String, schemaName: String): Map<String, Any>? {
        val spec = getSpec(serviceName) ?: return null
        val components = spec["components"] as? Map<String, Any> ?: return null
        val schemas = components["schemas"] as? Map<String, Any> ?: return null
        return schemas[schemaName] as? Map<String, Any>
    }

    /**
     * Retrieves the full schema map (used for Dart code generation).
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun getAllSchemas(serviceName: String): Map<String, Any> {
        val spec = getSpec(serviceName) ?: return emptyMap()
        val components = spec["components"] as? Map<String, Any> ?: return emptyMap()
        return components["schemas"] as? Map<String, Any> ?: emptyMap()
    }

    /**
     * Retrieves the list of all schema names.
     */
    suspend fun getSchemaList(serviceName: String): List<String> {
        val spec = getSpec(serviceName) ?: return emptyList()
        val components = spec["components"] as? Map<String, Any> ?: return emptyList()
        val schemas = components["schemas"] as? Map<String, Any> ?: return emptyList()
        return schemas.keys.sorted()
    }

    /**
     * Searches APIs by keyword across path, summary, description, and operationId.
     */
    suspend fun searchApis(serviceName: String, query: String, page: Int = 0, size: Int = 20): PagedResult<ApiSummary> {
        val spec = getSpec(serviceName) ?: return PagedResult(emptyList(), 0, page, size, false)
        val paths = spec["paths"] as? Map<String, Any> ?: return PagedResult(emptyList(), 0, page, size, false)
        val lowerQuery = query.lowercase()

        val apis = mutableListOf<ApiSummary>()
        for ((path, methods) in paths) {
            val methodMap = methods as? Map<String, Any> ?: continue
            for ((method, detail) in methodMap) {
                if (method in listOf("get", "post", "put", "delete", "patch")) {
                    val op = detail as? Map<String, Any> ?: continue
                    val tags = op["tags"] as? List<String>
                    val summary = op["summary"] as? String
                    val description = op["description"] as? String
                    val operationId = op["operationId"] as? String

                    val matches = listOfNotNull(path, summary, description, operationId)
                        .any { it.lowercase().contains(lowerQuery) }

                    if (matches) {
                        apis.add(
                            ApiSummary(
                                method = method.uppercase(),
                                path = path,
                                summary = summary,
                                tag = tags?.firstOrNull(),
                                deprecated = op["deprecated"] as? Boolean ?: false
                            )
                        )
                    }
                }
            }
        }

        val sorted = apis.sortedWith(compareBy({ it.tag }, { it.path }, { it.method }))
        val total = sorted.size
        val paged = sorted.drop(page * size).take(size)

        return PagedResult(
            items = paged,
            total = total,
            page = page,
            size = size,
            hasNext = (page + 1) * size < total
        )
    }

    /**
     * Refreshes the cached spec (async parallel).
     */
    suspend fun refreshCache(serviceName: String? = null) = coroutineScope {
        if (serviceName != null) {
            val resolvedName = resolveServiceName(serviceName)
            if (resolvedName != null) {
                specCache.remove(resolvedName)
                val entry = properties.services.find { it.name == resolvedName }
                if (entry != null) fetchAndCacheSpec(entry.name, entry.url, entry.swaggerPath)
            }
        } else {
            specCache.clear()
            properties.services.map { entry ->
                async { fetchAndCacheSpec(entry.name, entry.url, entry.swaggerPath) }
            }.awaitAll()
        }
    }

    /**
     * Returns available service names for error messages.
     */
    fun getAvailableServiceNames(): List<String> = properties.services.map { it.name }

    // --- internal ---

    /**
     * Resolves service name with fuzzy matching:
     * exact match → case-insensitive match → contains match
     */
    fun resolveServiceName(serviceName: String): String? {
        // exact match
        if (properties.services.any { it.name == serviceName }) return serviceName
        // case-insensitive match
        val caseInsensitive = properties.services.find { it.name.equals(serviceName, ignoreCase = true) }
        if (caseInsensitive != null) return caseInsensitive.name
        // contains match
        val lowerInput = serviceName.lowercase()
        val containsMatch = properties.services.filter { it.name.lowercase().contains(lowerInput) }
        if (containsMatch.size == 1) return containsMatch.first().name
        return null
    }

    private suspend fun getSpec(serviceName: String): Map<String, Any>? {
        val resolvedName = resolveServiceName(serviceName) ?: return null
        val cached = specCache[resolvedName]
        if (cached != null) {
            val ttlMinutes = properties.cache.ttlMinutes
            if (ttlMinutes > 0 && cached.cachedAt.plusSeconds(ttlMinutes * 60).isBefore(Instant.now())) {
                log.info("Cache expired for service: {}, re-fetching...", resolvedName)
                specCache.remove(resolvedName)
            } else {
                return cached.spec
            }
        }
        val entry = properties.services.find { it.name == resolvedName } ?: return null
        return fetchAndCacheSpec(entry.name, entry.url, entry.swaggerPath)
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun fetchAndCacheSpec(name: String, baseUrl: String, swaggerPath: String): Map<String, Any> {
        return try {
            val json = webClient.get()
                .uri("$baseUrl$swaggerPath")
                .retrieve()
                .bodyToMono(String::class.java)
                .awaitSingle()

            val spec = objectMapper.readValue(json, Map::class.java) as Map<String, Any>
            specCache[name] = CachedSpec(spec, Instant.now())
            log.info("Swagger spec cached for service: {}", name)
            spec
        } catch (e: Exception) {
            log.error("Failed to fetch swagger spec for {}: {}", name, e.message)
            specCache[name]?.spec ?: emptyMap()
        }
    }

    private fun countApis(spec: Map<String, Any>): Int {
        val paths = spec["paths"] as? Map<String, Any> ?: return 0
        return paths.values.sumOf { methods ->
            (methods as? Map<String, Any>)?.keys
                ?.count { it in listOf("get", "post", "put", "delete", "patch") } ?: 0
        }
    }

    private fun collectApis(paths: Map<String, Any>, tag: String?): List<ApiSummary> {
        val apis = mutableListOf<ApiSummary>()
        for ((path, methods) in paths) {
            val methodMap = methods as? Map<String, Any> ?: continue
            for ((method, detail) in methodMap) {
                if (method in listOf("get", "post", "put", "delete", "patch")) {
                    val op = detail as? Map<String, Any> ?: continue
                    val tags = op["tags"] as? List<String>
                    val firstTag = tags?.firstOrNull()

                    if (tag != null && firstTag != tag) continue

                    apis.add(
                        ApiSummary(
                            method = method.uppercase(),
                            path = path,
                            summary = op["summary"] as? String,
                            tag = firstTag,
                            deprecated = op["deprecated"] as? Boolean ?: false
                        )
                    )
                }
            }
        }
        return apis
    }

    @Suppress("UNCHECKED_CAST")
    private fun collectRefs(node: Any?, spec: Map<String, Any>, collected: MutableMap<String, Any>, visited: MutableSet<String>) {
        when (node) {
            is Map<*, *> -> {
                val map = node as Map<String, Any>
                val ref = map["\$ref"] as? String
                if (ref != null && ref.startsWith("#/components/schemas/")) {
                    val schemaName = ref.removePrefix("#/components/schemas/")
                    if (schemaName !in visited) {
                        visited.add(schemaName)
                        val components = spec["components"] as? Map<String, Any>
                        val schemas = components?.get("schemas") as? Map<String, Any>
                        val schema = schemas?.get(schemaName) as? Map<String, Any>
                        if (schema != null) {
                            collected[schemaName] = schema
                            collectRefs(schema, spec, collected, visited)
                        }
                    }
                }
                map.values.forEach { collectRefs(it, spec, collected, visited) }
            }
            is List<*> -> {
                node.forEach { collectRefs(it, spec, collected, visited) }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun simplifySchema(schema: Map<String, Any>?): Map<String, Any>? {
        if (schema == null) return null
        val ref = schema["\$ref"] as? String
        if (ref != null) return mapOf("\$ref" to ref)
        return schema
    }

    @Suppress("UNCHECKED_CAST")
    private fun simplifyNode(node: Map<String, Any>?): Map<String, Any>? {
        if (node == null) return null
        return node.mapValues { (_, value) ->
            when (value) {
                is Map<*, *> -> {
                    val map = value as Map<String, Any>
                    val ref = map["\$ref"] as? String
                    if (ref != null) mapOf("\$ref" to ref)
                    else simplifyNode(map) ?: value
                }
                else -> value
            }
        }
    }

    private fun formatInstant(instant: Instant): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneOffset.UTC)
            .format(instant)
}
