package com.example.swaggermcp.tool

import com.example.swaggermcp.service.DartCodeGenerator
import com.example.swaggermcp.service.SwaggerFetchService
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

@Component
class SwaggerMcpTools(
    private val swaggerFetchService: SwaggerFetchService,
    private val objectMapper: ObjectMapper,
    private val dartCodeGenerator: DartCodeGenerator
) {

    @Tool(description = """
        [Step 1] Lists all registered services.
        Returns the name, description, URL, and API count for each service.
        Use this tool first to discover available services before exploring their API specs.
    """)
    fun listServices(): String = runBlocking {
        val services = swaggerFetchService.getServiceList()
        objectMapper.writeValueAsString(services)
    }

    @Tool(description = """
        [Step 2-1] Lists tags (API groups) for a specific service.
        Use this to see what domain/feature groups exist in the service.
        You can filter APIs by tag using the listApis tool.
    """)
    fun listTags(
        @ToolParam(description = "Service name (from listServices)") serviceName: String
    ): String = runBlocking {
        val tags = swaggerFetchService.getTagList(serviceName)
        objectMapper.writeValueAsString(tags)
    }

    @Tool(description = """
        [Step 2-2] Lists APIs for a specific service.
        Supports filtering by tag and pagination.
        Returns HTTP method, path, summary, and tag for each API.
        Pass the path and method from these results to getApiDetail for full details.
    """)
    fun listApis(
        @ToolParam(description = "Service name") serviceName: String,
        @ToolParam(description = "Tag name to filter by (optional, from listTags)") tag: String? = null,
        @ToolParam(description = "Page number (0-based, default: 0)") page: Int = 0,
        @ToolParam(description = "Page size (default: 20)") size: Int = 20
    ): String = runBlocking {
        val apis = swaggerFetchService.getApiList(serviceName, tag, page, size)
        objectMapper.writeValueAsString(apis)
    }

    @Tool(description = """
        [Step 3] Retrieves detailed information for a specific API.
        Includes parameters, request body, and response definitions.
        If requestBody or responses contain ${'$'}ref, use getComponentSchema to resolve the schema.
    """)
    fun getApiDetail(
        @ToolParam(description = "Service name") serviceName: String,
        @ToolParam(description = "API path (e.g. /api/v1/users)") path: String,
        @ToolParam(description = "HTTP method (GET, POST, PUT, DELETE, PATCH)") method: String
    ): String = runBlocking {
        val detail = swaggerFetchService.getApiDetail(serviceName, path, method)
            ?: return@runBlocking """{"error": "API not found: $method $path in $serviceName"}"""
        objectMapper.writeValueAsString(detail)
    }

    @Tool(description = """
        [Step 4] Retrieves the detailed definition of a component schema.
        Use this to inspect field details of schemas referenced via ${'$'}ref in API details.
        Pass the name after '#/components/schemas/' from the ${'$'}ref value as schemaName.
    """)
    fun getComponentSchema(
        @ToolParam(description = "Service name") serviceName: String,
        @ToolParam(description = "Schema name (e.g. CreateUserRequest)") schemaName: String
    ): String = runBlocking {
        val schema = swaggerFetchService.getComponentSchema(serviceName, schemaName)
            ?: return@runBlocking """{"error": "Schema not found: $schemaName in $serviceName"}"""
        objectMapper.writeValueAsString(schema)
    }

    @Tool(description = """
        Lists all component schema names for a service.
        Use this to discover all DTO/model schemas defined in the service.
    """)
    fun listSchemas(
        @ToolParam(description = "Service name") serviceName: String
    ): String = runBlocking {
        val schemas = swaggerFetchService.getSchemaList(serviceName)
        objectMapper.writeValueAsString(schemas)
    }

    @Tool(description = """
        Converts Swagger schemas to Dart class code.
        Generates Dart code including the specified schema and all referenced sub-schemas.
        style: 'json_serializable' (default), 'freezed', or 'plain'.
        Multiple schemas can be converted at once by separating names with commas (e.g. "UserRequest,UserResponse").
    """)
    fun generateDartCode(
        @ToolParam(description = "Service name") serviceName: String,
        @ToolParam(description = "Schema names (comma-separated, e.g. CreateUserRequest,UserResponse)") schemaNames: String,
        @ToolParam(description = "Code style: json_serializable (default), freezed, plain") style: String = "json_serializable"
    ): String = runBlocking {
        val names = schemaNames.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (names.isEmpty()) {
            return@runBlocking """{"error": "schemaNames is empty"}"""
        }

        val allSchemas = swaggerFetchService.getAllSchemas(serviceName)
        if (allSchemas.isEmpty()) {
            return@runBlocking """{"error": "No schemas found for service: $serviceName"}"""
        }

        val missing = names.filter { it !in allSchemas }
        if (missing.isNotEmpty()) {
            return@runBlocking """{"error": "Schemas not found: ${missing.joinToString(", ")}"}"""
        }

        val useJsonSerializable = style != "plain"
        val useFreezed = style == "freezed"

        val dartCode = dartCodeGenerator.generateDartFile(
            schemaNames = names,
            allSchemas = allSchemas,
            useJsonSerializable = useJsonSerializable,
            useFreezed = useFreezed
        )

        dartCode
    }

    @Tool(description = """
        Refreshes the cached Swagger spec.
        Use this when a service's API spec has been updated.
        Specify serviceName to refresh a single service, or omit it to refresh all.
    """)
    fun refreshSwaggerCache(
        @ToolParam(description = "Service name to refresh (omit to refresh all)") serviceName: String? = null
    ): String = runBlocking {
        swaggerFetchService.refreshCache(serviceName)
        """{"status": "ok", "message": "Cache refreshed for ${serviceName ?: "all services"}"}"""
    }
}
