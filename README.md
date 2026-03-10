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

### Cursor

Add to `.cursor/mcp.json` in your project root:

```json
{
  "mcpServers": {
    "swagger": {
      "url": "http://localhost:8090/sse"
    }
  }
}
```

### Claude Code (CLI)

Add to your Claude Code settings (`.claude/settings.json`):

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

## Tool Usage & Response Examples

### Step 1: `listServices` — Discover registered services

**Parameters:** None

**Response:**
```json
[
  {
    "name": "petstore",
    "description": "Swagger Petstore sample API",
    "url": "https://petstore3.swagger.io",
    "apiCount": 19
  }
]
```

### Step 2-1: `listTags` — Browse API groups

**Parameters:**
| Name | Required | Description |
|------|----------|-------------|
| `serviceName` | Yes | Service name from `listServices` |

**Response:**
```json
[
  { "name": "pet", "description": "Everything about your Pets" },
  { "name": "store", "description": "Access to Petstore orders" },
  { "name": "user", "description": "Operations about user" }
]
```

### Step 2-2: `listApis` — List APIs with filtering

**Parameters:**
| Name | Required | Description |
|------|----------|-------------|
| `serviceName` | Yes | Service name |
| `tag` | No | Filter by tag name |
| `page` | No | Page number (0-based, default: 0) |
| `size` | No | Page size (default: 20) |

**Response:**
```json
[
  {
    "method": "GET",
    "path": "/pet/{petId}",
    "summary": "Find pet by ID",
    "tag": "pet",
    "deprecated": false
  },
  {
    "method": "POST",
    "path": "/pet",
    "summary": "Add a new pet to the store",
    "tag": "pet",
    "deprecated": false
  }
]
```

### Step 3: `getApiDetail` — Full API specification

**Parameters:**
| Name | Required | Description |
|------|----------|-------------|
| `serviceName` | Yes | Service name |
| `path` | Yes | API path (e.g. `/pet/{petId}`) |
| `method` | Yes | HTTP method (GET, POST, PUT, DELETE, PATCH) |

**Response:**
```json
{
  "method": "GET",
  "path": "/pet/{petId}",
  "summary": "Find pet by ID",
  "description": "Returns a single pet",
  "tag": "pet",
  "parameters": [
    {
      "name": "petId",
      "in": "path",
      "required": true,
      "description": "ID of pet to return",
      "schema": { "type": "integer", "format": "int64" }
    }
  ],
  "requestBody": null,
  "responses": {
    "200": {
      "description": "successful operation",
      "content": {
        "application/json": {
          "schema": { "$ref": "#/components/schemas/Pet" }
        }
      }
    }
  }
}
```

### Step 4: `getComponentSchema` — Resolve schema details

**Parameters:**
| Name | Required | Description |
|------|----------|-------------|
| `serviceName` | Yes | Service name |
| `schemaName` | Yes | Schema name from `$ref` (e.g. `Pet`) |

**Response:**
```json
{
  "type": "object",
  "required": ["name", "photoUrls"],
  "properties": {
    "id": { "type": "integer", "format": "int64" },
    "name": { "type": "string", "example": "doggie" },
    "status": { "type": "string", "enum": ["available", "pending", "sold"] }
  }
}
```

### `listSchemas` — List all schema names

**Parameters:**
| Name | Required | Description |
|------|----------|-------------|
| `serviceName` | Yes | Service name |

**Response:**
```json
["Address", "ApiResponse", "Category", "Customer", "Order", "Pet", "Tag", "User"]
```

### `generateDartCode` — Generate Dart model classes

**Parameters:**
| Name | Required | Description |
|------|----------|-------------|
| `serviceName` | Yes | Service name |
| `schemaNames` | Yes | Comma-separated schema names (e.g. `Pet,Category`) |
| `style` | No | `json_serializable` (default), `freezed`, or `plain` |

**Response** (returns Dart source code as string):
```dart
import 'package:json_annotation/json_annotation.dart';

part 'pet.g.dart';

@JsonSerializable()
class Pet {
  final int? id;
  final String name;
  final List<String> photoUrls;

  const Pet({
    this.id,
    required this.name,
    required this.photoUrls,
  });

  factory Pet.fromJson(Map<String, dynamic> json) => _$PetFromJson(json);
  Map<String, dynamic> toJson() => _$PetToJson(this);
}
```

### `refreshSwaggerCache` — Refresh cached specs

**Parameters:**
| Name | Required | Description |
|------|----------|-------------|
| `serviceName` | No | Specific service to refresh (omit for all) |

**Response:**
```json
{ "status": "ok", "message": "Cache refreshed for all services" }
```

## Tech Stack

- Kotlin + Spring Boot 3
- Spring AI MCP Server (SSE/Async)
- WebClient for non-blocking Swagger spec fetching
- Coroutines for async parallel processing

## License

[MIT](LICENSE)
