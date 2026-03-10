# Swagger MCP Server (Async)

An MCP (Model Context Protocol) server that exposes Swagger/OpenAPI specs as AI-callable tools. Built with Spring Boot and Spring AI, using SSE (Server-Sent Events) for async communication.

Register your microservices once, and let AI assistants explore APIs, inspect schemas, and even generate Dart client code — all through MCP.

## Features

| Tool | Description |
|------|-------------|
| `listServices` | List all registered services with API counts |
| `listTags` | List tags (API groups) for a service |
| `listApis` | List APIs with tag filtering and pagination |
| `getApiDetail` | Get full API details (params, request body, responses) |
| `getComponentSchema` | Inspect a component schema definition |
| `listSchemas` | List all schema names in a service |
| `generateDartCode` | Generate Dart classes from schemas (json_serializable / freezed / plain) |
| `refreshSwaggerCache` | Refresh cached Swagger specs |

## Quick Start

### Prerequisites

- Java 21+
- Gradle (wrapper included)

### Local

```bash
# 1. Configure your services in src/main/resources/application.yml
# 2. Build and run
./gradlew bootRun
```

The server starts at `http://localhost:8090` with SSE endpoint at `/sse`.

### Docker

```bash
# 1. Edit config/application.yml with your services
#    (see config/application.yml.example for reference)

# 2. Build and run
docker compose up -d

# Or with custom port
SERVER_PORT=9090 docker compose up -d
```

## Configuration

Add your Swagger/OpenAPI services to `swagger-center.services`:

```yaml
swagger-center:
  services:
    - name: petstore
      url: https://petstore3.swagger.io
      swagger-path: /api/v3/openapi.json
      description: Swagger Petstore sample API
    - name: my-backend
      url: http://localhost:8080
      swagger-path: /v3/api-docs
      description: My backend service API
```

Each entry requires:
- **name** — Unique identifier for the service
- **url** — Base URL of the service
- **swagger-path** — Path to the OpenAPI/Swagger JSON endpoint
- **description** — Human-readable description

## MCP Client Integration

### Claude Desktop

Add to your Claude Desktop config (`claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "swagger": {
      "url": "http://localhost:8090/sse"
    }
  }
}
```

### Other MCP Clients

Connect to the SSE endpoint:
- **SSE URL**: `http://localhost:8090/sse`
- **Message endpoint**: `http://localhost:8090/mcp/messages`

## Tech Stack

- Kotlin + Spring Boot 3
- Spring AI MCP Server (SSE/Async)
- WebClient for non-blocking Swagger spec fetching
- Coroutines for async parallel processing

## License

[MIT](LICENSE)
