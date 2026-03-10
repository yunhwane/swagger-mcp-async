package com.example.swaggermcp.prompt

import io.modelcontextprotocol.spec.McpSchema.Role
import io.modelcontextprotocol.spec.McpSchema.TextContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springaicommunity.mcp.annotation.McpArg
import org.springaicommunity.mcp.annotation.McpPrompt

class SwaggerMcpPromptsTest {

    private lateinit var prompts: SwaggerMcpPrompts

    @BeforeEach
    fun setUp() {
        prompts = SwaggerMcpPrompts()
    }

    @Nested
    inner class SearchApis {

        @Test
        fun `should return result with description containing service and query`() {
            val result = prompts.searchApis("petstore", "user login")

            assertEquals("Search APIs matching 'user login' in petstore", result.description())
        }

        @Test
        fun `should return single USER message`() {
            val result = prompts.searchApis("petstore", "user login")

            assertEquals(1, result.messages().size)
            assertEquals(Role.USER, result.messages()[0].role())
        }

        @Test
        fun `should include service name and description in message text`() {
            val result = prompts.searchApis("my-service", "create order")
            val text = (result.messages()[0].content() as TextContent).text()

            assertTrue(text.contains("my-service"))
            assertTrue(text.contains("create order"))
            assertTrue(text.contains("searchApis"))
            assertTrue(text.contains("getApiDetail"))
        }
    }

    @Nested
    inner class ExploreService {

        @Test
        fun `should return result with service name in description`() {
            val result = prompts.exploreService("petstore")

            assertEquals("Explore the structure of petstore", result.description())
        }

        @Test
        fun `should return single USER message`() {
            val result = prompts.exploreService("petstore")

            assertEquals(1, result.messages().size)
            assertEquals(Role.USER, result.messages()[0].role())
        }

        @Test
        fun `should include step-by-step tool instructions`() {
            val result = prompts.exploreService("petstore")
            val text = (result.messages()[0].content() as TextContent).text()

            assertTrue(text.contains("listServices"))
            assertTrue(text.contains("listTags"))
            assertTrue(text.contains("listApis"))
            assertTrue(text.contains("petstore"))
        }
    }

    @Nested
    inner class GetApiSpec {

        @Test
        fun `should return result with method path and service in description`() {
            val result = prompts.getApiSpec("petstore", "/api/v1/pets", "get")

            assertEquals("Get API spec for GET /api/v1/pets in petstore", result.description())
        }

        @Test
        fun `should uppercase the method in description`() {
            val result = prompts.getApiSpec("petstore", "/api/v1/pets", "post")

            assertTrue(result.description().contains("POST"))
        }

        @Test
        fun `should return single USER message with endpoint details`() {
            val result = prompts.getApiSpec("petstore", "/api/v1/pets", "get")
            val text = (result.messages()[0].content() as TextContent).text()

            assertEquals(1, result.messages().size)
            assertEquals(Role.USER, result.messages()[0].role())
            assertTrue(text.contains("petstore"))
            assertTrue(text.contains("/api/v1/pets"))
            assertTrue(text.contains("GET"))
            assertTrue(text.contains("getApiDetail"))
            assertTrue(text.contains("resolveRefs=true"))
        }
    }

    @Nested
    inner class GenerateDartModels {

        @Test
        fun `should return result with schema names and service in description`() {
            val result = prompts.generateDartModels("petstore", "Pet,Category", "json_serializable")

            assertEquals("Generate Dart models for Pet,Category from petstore", result.description())
        }

        @Test
        fun `should include specified style in message`() {
            val result = prompts.generateDartModels("petstore", "Pet", "freezed")
            val text = (result.messages()[0].content() as TextContent).text()

            assertTrue(text.contains("freezed"))
            assertTrue(text.contains("generateDartCode"))
        }

        @Test
        fun `should default to json_serializable when style is blank`() {
            val result = prompts.generateDartModels("petstore", "Pet", "")
            val text = (result.messages()[0].content() as TextContent).text()

            assertTrue(text.contains("json_serializable"))
        }

        @Test
        fun `should include tool usage instructions`() {
            val result = prompts.generateDartModels("petstore", "Pet,Category", "plain")
            val text = (result.messages()[0].content() as TextContent).text()

            assertTrue(text.contains("listSchemas"))
            assertTrue(text.contains("generateDartCode"))
            assertTrue(text.contains("Pet,Category"))
        }
    }

    @Nested
    inner class Annotations {

        @Test
        fun `class should have Component annotation`() {
            val componentAnnotation = SwaggerMcpPrompts::class.java
                .getAnnotation(org.springframework.stereotype.Component::class.java)

            assertNotNull(componentAnnotation)
        }

        @Test
        fun `searchApis should have McpPrompt annotation with correct name`() {
            val method = SwaggerMcpPrompts::class.java
                .getMethod("searchApis", String::class.java, String::class.java)
            val annotation = method.getAnnotation(McpPrompt::class.java)

            assertNotNull(annotation)
            assertEquals("search-apis", annotation.name)
            assertTrue(annotation.description.isNotBlank())
        }

        @Test
        fun `exploreService should have McpPrompt annotation with correct name`() {
            val method = SwaggerMcpPrompts::class.java
                .getMethod("exploreService", String::class.java)
            val annotation = method.getAnnotation(McpPrompt::class.java)

            assertNotNull(annotation)
            assertEquals("explore-service", annotation.name)
        }

        @Test
        fun `getApiSpec should have McpPrompt annotation with correct name`() {
            val method = SwaggerMcpPrompts::class.java
                .getMethod("getApiSpec", String::class.java, String::class.java, String::class.java)
            val annotation = method.getAnnotation(McpPrompt::class.java)

            assertNotNull(annotation)
            assertEquals("get-api-spec", annotation.name)
        }

        @Test
        fun `generateDartModels should have McpPrompt annotation with correct name`() {
            val method = SwaggerMcpPrompts::class.java
                .getMethod("generateDartModels", String::class.java, String::class.java, String::class.java)
            val annotation = method.getAnnotation(McpPrompt::class.java)

            assertNotNull(annotation)
            assertEquals("generate-dart-models", annotation.name)
        }

        @Test
        fun `searchApis parameters should have McpArg annotations`() {
            val method = SwaggerMcpPrompts::class.java
                .getMethod("searchApis", String::class.java, String::class.java)
            val params = method.parameters

            val arg0 = params[0].getAnnotation(McpArg::class.java)
            assertNotNull(arg0)
            assertEquals("serviceName", arg0.name)

            val arg1 = params[1].getAnnotation(McpArg::class.java)
            assertNotNull(arg1)
            assertEquals("description", arg1.name)
        }

        @Test
        fun `getApiSpec parameters should have McpArg annotations`() {
            val method = SwaggerMcpPrompts::class.java
                .getMethod("getApiSpec", String::class.java, String::class.java, String::class.java)
            val params = method.parameters

            val names = params.map { it.getAnnotation(McpArg::class.java)?.name }
            assertEquals(listOf("serviceName", "path", "method"), names)
        }
    }
}
