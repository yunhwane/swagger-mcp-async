package com.example.swaggermcp.prompt

import io.modelcontextprotocol.spec.McpSchema.GetPromptResult
import io.modelcontextprotocol.spec.McpSchema.PromptMessage
import io.modelcontextprotocol.spec.McpSchema.Role
import io.modelcontextprotocol.spec.McpSchema.TextContent
import org.springaicommunity.mcp.annotation.McpArg
import org.springaicommunity.mcp.annotation.McpPrompt
import org.springframework.stereotype.Component

@Component
class SwaggerMcpPrompts {

    @McpPrompt(
        name = "search-apis",
        description = "Search relevant APIs from a registered service"
    )
    fun searchApis(
        @McpArg(name = "serviceName", description = "Service name (from listServices)") serviceName: String,
        @McpArg(name = "description", description = "API description to search") description: String
    ): GetPromptResult {
        return GetPromptResult(
            "Search APIs matching '$description' in $serviceName",
            listOf(
                PromptMessage(
                    Role.USER,
                    TextContent("""
                        Use the listServices tool first to verify that "$serviceName" exists.
                        Then use the searchApis tool to find APIs in service "$serviceName"
                        that match the description: "$description".
                        For the most relevant results, use getApiDetail to retrieve full specifications.
                        Summarize the found APIs with their paths, methods, and descriptions.
                    """.trimIndent())
                )
            )
        )
    }

    @McpPrompt(
        name = "explore-service",
        description = "Explore the full structure of a service (tags, API list)"
    )
    fun exploreService(
        @McpArg(name = "serviceName", description = "Service name to explore") serviceName: String
    ): GetPromptResult {
        return GetPromptResult(
            "Explore the structure of $serviceName",
            listOf(
                PromptMessage(
                    Role.USER,
                    TextContent("""
                        Explore the full structure of the "$serviceName" service step by step:
                        1. Use listServices to confirm the service exists and see its basic info.
                        2. Use listTags to discover all API groups/tags in the service.
                        3. For each tag, use listApis with the tag filter to list the APIs.
                        4. Provide a structured summary of the service organized by tags,
                           including the total number of APIs and a brief description of each group.
                    """.trimIndent())
                )
            )
        )
    }

    @McpPrompt(
        name = "get-api-spec",
        description = "Get the detailed specification of a specific API"
    )
    fun getApiSpec(
        @McpArg(name = "serviceName", description = "Service name") serviceName: String,
        @McpArg(name = "path", description = "API path (e.g. /api/v1/users)") path: String,
        @McpArg(name = "method", description = "HTTP method (GET, POST, PUT, DELETE, PATCH)") method: String
    ): GetPromptResult {
        return GetPromptResult(
            "Get API spec for ${method.uppercase()} $path in $serviceName",
            listOf(
                PromptMessage(
                    Role.USER,
                    TextContent("""
                        Retrieve the full specification of the API endpoint:
                        - Service: "$serviceName"
                        - Path: "$path"
                        - Method: ${method.uppercase()}

                        Use getApiDetail with resolveRefs=true to get the complete specification
                        including all resolved schemas.

                        Present the result clearly with:
                        - Summary and description
                        - Request parameters and their types
                        - Request body schema (if applicable)
                        - Response schema for each status code
                        - Any referenced component schemas
                    """.trimIndent())
                )
            )
        )
    }

    @McpPrompt(
        name = "generate-dart-models",
        description = "Generate Dart model code from Swagger schemas"
    )
    fun generateDartModels(
        @McpArg(name = "serviceName", description = "Service name") serviceName: String,
        @McpArg(name = "schemaNames", description = "Comma-separated schema names (e.g. UserRequest,UserResponse)") schemaNames: String,
        @McpArg(name = "style", description = "Code style: json_serializable (default), freezed, or plain") style: String
    ): GetPromptResult {
        val effectiveStyle = style.ifBlank { "json_serializable" }
        return GetPromptResult(
            "Generate Dart models for $schemaNames from $serviceName",
            listOf(
                PromptMessage(
                    Role.USER,
                    TextContent("""
                        Generate Dart model code from the following Swagger schemas:
                        - Service: "$serviceName"
                        - Schemas: $schemaNames
                        - Style: $effectiveStyle

                        Steps:
                        1. Use listSchemas to verify the schema names exist in the service.
                        2. Use generateDartCode with the specified schemas and style "$effectiveStyle".
                        3. Present the generated Dart code in a code block.
                        4. If the style is "json_serializable", remind about running build_runner.
                           If the style is "freezed", remind about both freezed and build_runner dependencies.
                    """.trimIndent())
                )
            )
        )
    }
}
