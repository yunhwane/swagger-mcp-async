package com.example.swaggermcp.config

import com.example.swaggermcp.tool.SwaggerMcpTools
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class McpToolConfig {

    @Bean
    fun toolCallbackProvider(swaggerMcpTools: SwaggerMcpTools): ToolCallbackProvider {
        return MethodToolCallbackProvider.builder()
            .toolObjects(swaggerMcpTools)
            .build()
    }

    @Bean
    fun toolExecutionExceptionProcessor(): ToolExecutionExceptionProcessor {
        // When alwaysThrow=true, exceptions are re-thrown so MCP protocol marks them as isError=true
        return DefaultToolExecutionExceptionProcessor(true)
    }
}
