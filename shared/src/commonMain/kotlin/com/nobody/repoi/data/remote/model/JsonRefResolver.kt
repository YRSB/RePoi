package com.nobody.repoi.data.remote.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object JsonRefResolver {

    fun resolveRefs(root: JsonElement): JsonElement {
        val byId = mutableMapOf<String, JsonObject>()
        collectIds(root, byId)
        if (byId.isEmpty()) return root
        return resolve(root, byId, mutableSetOf())
    }

    private fun collectIds(element: JsonElement, byId: MutableMap<String, JsonObject>) {
        when (element) {
            is JsonObject -> {
                val id = (element["@id"] as? JsonPrimitive)?.content
                    ?: (element["${'$'}id"] as? JsonPrimitive)?.content
                if (id != null) {
                    val isEnvelope = element.containsKey("code") && element.containsKey("success")
                    if (!isEnvelope || !byId.containsKey(id)) {
                        byId[id] = element
                    }
                }
                element.values.forEach { collectIds(it, byId) }
            }
            is JsonArray -> element.forEach { collectIds(it, byId) }
            else -> {}
        }
    }

    private fun resolve(
        element: JsonElement,
        byId: Map<String, JsonObject>,
        stack: MutableSet<String>
    ): JsonElement {
        return when (element) {
            is JsonObject -> {

                val ref = (element["@ref"] as? JsonPrimitive)?.content
                    ?: (element["${'$'}ref"] as? JsonPrimitive)?.content
                if (ref != null) {
                    val target = byId[ref] ?: return element
                    if (!stack.add(ref)) return element
                    try {
                        val resolved = resolve(target, byId, stack)

                        val overrides = element.filterKeys { it != "@ref" && it != "${'$'}ref" }
                        if (overrides.isEmpty() || resolved !is JsonObject) return resolved
                        val mergedOverrides = overrides.mapValues { resolve(it.value, byId, stack) }
                        return JsonObject(buildMap {
                            putAll(resolved)
                            putAll(mergedOverrides)
                        })
                    } finally {
                        stack.remove(ref)
                    }
                } else {
                    JsonObject(element.mapValues { resolve(it.value, byId, stack) })
                }
            }
            is JsonArray -> JsonArray(element.map { resolve(it, byId, stack) })
            else -> element
        }
    }
}
