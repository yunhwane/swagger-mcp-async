# Swagger MCP Server (Async)

An MCP (Model Context Protocol) server that exposes Swagger/OpenAPI specs as AI-callable tools. Built with Spring Boot and Spring AI, using SSE (Server-Sent Events) for async communication.

Register your microservices once, and let AI assistants explore APIs, inspect schemas, and even generate Dart client code — all through MCP.

## Features

### Tools

| Tool | Description |
|------|-------------|
| `listServices` | List all registered services with API counts and cache timestamps |
| `listTags` | List tags (API groups) for a service |
| `listApis` | List APIs with tag filtering and pagination |
| `searchApis` | Search APIs by keyword across path, summary, description, and operationId |
| `getApiDetail` | Get full API details with automatic `$ref` schema resolution |
| `getComponentSchema` | Inspect a component schema definition |
| `listSchemas` | List all schema names in a service |
| `generateDartCode` | Generate Dart classes from schemas (json_serializable / freezed / plain) |
| `refreshSwaggerCache` | Refresh cached Swagger specs |

### MCP Prompts

| Prompt | Description |
|--------|-------------|
| `search-apis` | Search relevant APIs from a registered service |
| `explore-service` | Explore the full structure of a service (tags, API list) |
| `get-api-spec` | Get the detailed specification of a specific API |
| `generate-dart-models` | Generate Dart model code from Swagger schemas |

### Other Features

- **Auto `$ref` resolution** — `getApiDetail` automatically resolves referenced schemas and includes them in `resolvedSchemas`
- **Cache TTL** — Configurable cache expiration (`swagger-center.cache.ttl-minutes`, default: 60)
- **Fuzzy service name matching** — Exact → case-insensitive → contains match
- **Structured error handling** — Invalid service names and missing schemas return MCP `isError=true` responses with helpful messages
- **Paginated responses** — `listApis` and `searchApis` return `{ items, total, page, size, hasNext }`

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
  cache:
    ttl-minutes: 60          # Cache expiration (default: 60, 0 = no expiration)
  webhook:
    secret: ""               # GitHub webhook secret (optional)
    enabled: true
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

Each service entry requires:
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
    "apiCount": 19,
    "cachedAt": "2025-01-15T10:30:00Z"
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
{
  "items": [
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
  ],
  "total": 12,
  "page": 0,
  "size": 20,
  "hasNext": false
}
```

### `searchApis` — Search APIs by keyword

**Parameters:**
| Name | Required | Description |
|------|----------|-------------|
| `serviceName` | Yes | Service name |
| `query` | Yes | Search keyword (case-insensitive, matches path/summary/description/operationId) |
| `page` | No | Page number (0-based, default: 0) |
| `size` | No | Page size (default: 20) |

**Response:**
```json
{
  "items": [
    {
      "method": "GET",
      "path": "/pet/findByStatus",
      "summary": "Finds Pets by status",
      "tag": "pet",
      "deprecated": false
    }
  ],
  "total": 1,
  "page": 0,
  "size": 20,
  "hasNext": false
}
```

### Step 3: `getApiDetail` — Full API specification

**Parameters:**
| Name | Required | Description |
|------|----------|-------------|
| `serviceName` | Yes | Service name |
| `path` | Yes | API path (e.g. `/pet/{petId}`) |
| `method` | Yes | HTTP method (GET, POST, PUT, DELETE, PATCH) |
| `resolveRefs` | No | Auto-resolve `$ref` schemas (default: true) |

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
  },
  "resolvedSchemas": {
    "Pet": {
      "type": "object",
      "required": ["name", "photoUrls"],
      "properties": {
        "id": { "type": "integer", "format": "int64" },
        "name": { "type": "string", "example": "doggie" },
        "category": { "$ref": "#/components/schemas/Category" }
      }
    },
    "Category": {
      "type": "object",
      "properties": {
        "id": { "type": "integer", "format": "int64" },
        "name": { "type": "string" }
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
