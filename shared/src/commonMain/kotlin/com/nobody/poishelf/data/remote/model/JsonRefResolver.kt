package com.nobody.poishelf.data.remote.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 解析服务端 JSON 引用去重（`@id`/`@ref`）。
 *
 * 部分列表接口对重复出现的对象只在首次全量返回（附 `@id`），后续以 `{"@ref": id}` 简写。
 * kotlinx.serialization 会把简写体解析成全默认值的空对象，导致关联封面/名称丢失，
 * 因此在解码为 DTO 之前先把引用还原为完整对象。
 */
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
                // 服务端引用去重体以 `@ref` 或 `\$ref` 指向首次出现的 `@id` 对象。
                // 同一对象可被引用多次，因此仅用递归栈防循环，不做全局去重。
                val ref = (element["@ref"] as? JsonPrimitive)?.content
                    ?: (element["${'$'}ref"] as? JsonPrimitive)?.content
                if (ref != null) {
                    val target = byId[ref] ?: return element
                    if (!stack.add(ref)) return element
                    try {
                        val resolved = resolve(target, byId, stack)
                        // 引用体可能携带补充字段，合并覆盖避免静默丢失
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
