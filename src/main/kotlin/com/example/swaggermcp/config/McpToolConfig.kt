package com.example.swaggermcp.config

import com.example.swaggermcp.tool.SwaggerMcpTools
import org.springframework.ai.tool.ToolCallbackProvider
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
}
