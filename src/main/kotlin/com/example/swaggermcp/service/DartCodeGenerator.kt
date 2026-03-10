package com.example.swaggermcp.service

import org.springframework.stereotype.Component

@Component
class DartCodeGenerator {

    /**
     * Converts a single schema to a Dart class.
     */
    fun generateDartClass(
        schemaName: String,
        schema: Map<String, Any>,
        allSchemas: Map<String, Any>? = null,
        useJsonSerializable: Boolean = true,
        useFreezed: Boolean = false
    ): String {
        return if (useFreezed) {
            generateFreezedClass(schemaName, schema)
        } else {
            generatePlainClass(schemaName, schema, useJsonSerializable)
        }
    }

    /**
     * Converts multiple schemas into a single Dart file (automatically includes dependent schemas).
     */
    fun generateDartFile(
        schemaNames: List<String>,
        allSchemas: Map<String, Any>,
        useJsonSerializable: Boolean = true,
        useFreezed: Boolean = false
    ): String {
        val sb = StringBuilder()
        val generated = mutableSetOf<String>()
        val toGenerate = ArrayDeque(schemaNames)

        // Collect dependent schemas
        while (toGenerate.isNotEmpty()) {
            val name = toGenerate.removeFirst()
            if (name in generated) continue
            generated.add(name)

            val schema = allSchemas[name] as? Map<String, Any> ?: continue
            val refs = collectRefs(schema)
            refs.forEach { ref ->
                if (ref !in generated) toGenerate.addLast(ref)
            }
        }

        // imports
        if (useFreezed) {
            val fileName = schemaNames.first().toSnakeCase()
            sb.appendLine("import 'package:freezed_annotation/freezed_annotation.dart';")
            sb.appendLine()
            sb.appendLine("part '${fileName}.freezed.dart';")
            sb.appendLine("part '${fileName}.g.dart';")
        } else if (useJsonSerializable) {
            val fileName = schemaNames.first().toSnakeCase()
            sb.appendLine("import 'package:json_annotation/json_annotation.dart';")
            sb.appendLine()
            sb.appendLine("part '${fileName}.g.dart';")
        }
        sb.appendLine()

        // Generate classes (requested schemas first, then dependencies)
        val ordered = schemaNames + (generated - schemaNames.toSet())
        for (name in ordered) {
            val schema = allSchemas[name] as? Map<String, Any> ?: continue
            sb.appendLine(generateDartClass(name, schema, allSchemas, useJsonSerializable, useFreezed))
            sb.appendLine()
        }

        return sb.toString().trimEnd()
    }

    // --- Plain / json_serializable class generation ---

    @Suppress("UNCHECKED_CAST")
    private fun generatePlainClass(
        className: String,
        schema: Map<String, Any>,
        useJsonSerializable: Boolean
    ): String {
        val sb = StringBuilder()
        val properties = schema["properties"] as? Map<String, Map<String, Any>> ?: emptyMap()
        val requiredFields = (schema["required"] as? List<String>)?.toSet() ?: emptySet()
        val description = schema["description"] as? String

        // enum handling
        val enumValues = schema["enum"] as? List<Any>
        if (enumValues != null) {
            return generateEnum(className, enumValues, schema)
        }

        if (description != null) {
            sb.appendLine("/// $description")
        }

        if (useJsonSerializable) {
            sb.appendLine("@JsonSerializable()")
        }
        sb.appendLine("class $className {")

        // Field declarations
        for ((fieldName, fieldSchema) in properties) {
            val isRequired = fieldName in requiredFields
            val dartType = mapToDartType(fieldName, fieldSchema)
            val nullable = if (!isRequired) "?" else ""
            val fieldDesc = fieldSchema["description"] as? String

            if (fieldDesc != null) {
                sb.appendLine("  /// $fieldDesc")
            }

            val jsonKey = fieldName
            val dartFieldName = fieldName.toCamelCase()
            if (jsonKey != dartFieldName && useJsonSerializable) {
                sb.appendLine("  @JsonKey(name: '$jsonKey')")
            }
            sb.appendLine("  final $dartType$nullable $dartFieldName;")
            sb.appendLine()
        }

        // Constructor
        sb.appendLine("  const $className({")
        for ((fieldName, _) in properties) {
            val isRequired = fieldName in requiredFields
            val dartFieldName = fieldName.toCamelCase()
            if (isRequired) {
                sb.appendLine("    required this.$dartFieldName,")
            } else {
                sb.appendLine("    this.$dartFieldName,")
            }
        }
        sb.appendLine("  });")

        // fromJson / toJson
        if (useJsonSerializable) {
            sb.appendLine()
            sb.appendLine("  factory $className.fromJson(Map<String, dynamic> json) =>")
            sb.appendLine("      _\$${className}FromJson(json);")
            sb.appendLine()
            sb.appendLine("  Map<String, dynamic> toJson() => _\$${className}ToJson(this);")
        }

        sb.appendLine("}")
        return sb.toString()
    }

    // --- Freezed class generation ---

    @Suppress("UNCHECKED_CAST")
    private fun generateFreezedClass(className: String, schema: Map<String, Any>): String {
        val sb = StringBuilder()
        val properties = schema["properties"] as? Map<String, Map<String, Any>> ?: emptyMap()
        val requiredFields = (schema["required"] as? List<String>)?.toSet() ?: emptySet()
        val description = schema["description"] as? String

        val enumValues = schema["enum"] as? List<Any>
        if (enumValues != null) {
            return generateEnum(className, enumValues, schema)
        }

        if (description != null) {
            sb.appendLine("/// $description")
        }

        sb.appendLine("@freezed")
        sb.appendLine("class $className with _\$${className} {")
        sb.appendLine("  const factory $className({")

        for ((fieldName, fieldSchema) in properties) {
            val isRequired = fieldName in requiredFields
            val dartType = mapToDartType(fieldName, fieldSchema)
            val dartFieldName = fieldName.toCamelCase()
            val jsonKey = fieldName

            if (jsonKey != dartFieldName) {
                sb.appendLine("    @JsonKey(name: '$jsonKey')")
            }

            if (isRequired) {
                sb.appendLine("    required $dartType $dartFieldName,")
            } else {
                sb.appendLine("    $dartType? $dartFieldName,")
            }
        }

        sb.appendLine("  }) = _$className;")
        sb.appendLine()
        sb.appendLine("  factory $className.fromJson(Map<String, dynamic> json) =>")
        sb.appendLine("      _\$${className}FromJson(json);")
        sb.appendLine("}")

        return sb.toString()
    }

    // --- Enum generation ---

    private fun generateEnum(name: String, values: List<Any>, schema: Map<String, Any>): String {
        val sb = StringBuilder()
        val description = schema["description"] as? String

        if (description != null) {
            sb.appendLine("/// $description")
        }
        sb.appendLine("enum $name {")
        for (value in values) {
            val enumValue = value.toString()
            val dartName = enumValue.toCamelCase()
            if (dartName != enumValue) {
                sb.appendLine("  @JsonValue('$enumValue')")
            }
            sb.appendLine("  $dartName,")
        }
        sb.appendLine("}")
        return sb.toString()
    }

    // --- Type mapping ---

    @Suppress("UNCHECKED_CAST")
    private fun mapToDartType(fieldName: String, schema: Map<String, Any>): String {
        // $ref reference
        val ref = schema["\$ref"] as? String
        if (ref != null) {
            return ref.substringAfterLast("/")
        }

        val type = schema["type"] as? String
        val format = schema["format"] as? String

        return when (type) {
            "string" -> when (format) {
                "date-time", "date" -> "DateTime"
                "binary" -> "List<int>"
                "uri", "url" -> "Uri"
                else -> "String"
            }
            "integer" -> when (format) {
                "int64" -> "int"
                else -> "int"
            }
            "number" -> when (format) {
                "float" -> "double"
                "double" -> "double"
                else -> "double"
            }
            "boolean" -> "bool"
            "array" -> {
                val items = schema["items"] as? Map<String, Any>
                val itemType = if (items != null) mapToDartType(fieldName, items) else "dynamic"
                "List<$itemType>"
            }
            "object" -> {
                val additionalProperties = schema["additionalProperties"] as? Map<String, Any>
                if (additionalProperties != null) {
                    val valueType = mapToDartType(fieldName, additionalProperties)
                    "Map<String, $valueType>"
                } else {
                    "Map<String, dynamic>"
                }
            }
            else -> {
                // allOf, oneOf, anyOf handling
                val allOf = schema["allOf"] as? List<Map<String, Any>>
                if (allOf != null && allOf.isNotEmpty()) {
                    val refItem = allOf.find { it.containsKey("\$ref") }
                    if (refItem != null) {
                        return (refItem["\$ref"] as String).substringAfterLast("/")
                    }
                }
                "dynamic"
            }
        }
    }

    // --- $ref collection ---

    @Suppress("UNCHECKED_CAST")
    private fun collectRefs(node: Any?): Set<String> {
        val refs = mutableSetOf<String>()
        when (node) {
            is Map<*, *> -> {
                val ref = node["\$ref"] as? String
                if (ref != null && ref.startsWith("#/components/schemas/")) {
                    refs.add(ref.substringAfterLast("/"))
                }
                node.values.forEach { refs.addAll(collectRefs(it)) }
            }
            is List<*> -> {
                node.forEach { refs.addAll(collectRefs(it)) }
            }
        }
        return refs
    }

    // --- Utilities ---

    private fun String.toSnakeCase(): String {
        return this.replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
            .lowercase()
    }

    private fun String.toCamelCase(): String {
        if (!this.contains('_') && !this.contains('-')) {
            return this.replaceFirstChar { it.lowercase() }
        }
        return this.split(Regex("[_\\-]"))
            .mapIndexed { index, part ->
                if (index == 0) part.lowercase()
                else part.replaceFirstChar { it.uppercase() }
            }
            .joinToString("")
    }
}
