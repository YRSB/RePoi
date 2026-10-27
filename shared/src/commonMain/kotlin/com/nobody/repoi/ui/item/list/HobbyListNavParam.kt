package com.nobody.repoi.ui.item.list

import io.ktor.http.decodeURLPart
import kotlinx.serialization.Serializable

@Serializable
data class HobbyListNavParam(
    val title: String = "周边列表",
    val order: String = "hits7Day",
    val categoryId: Int = 10000,
    val releaseYear: Int? = null,
    val releaseMonth: Int? = null,
    val releaseStart: String? = null,
    val releaseEnd: String? = null,
    val hasShopItem: Int? = null,
    val keyword: String? = null,
    val isSearchFocused: Boolean = false,
    val sex: Int? = null,
    val specs: String? = null,
    val scale: String? = null,
    val r18: Int? = null,
    val workers: String? = null,
    val works: String? = null,
    val tag: String? = null,
    val character: String? = null
) {
    companion object {
        fun isHobbyListLink(link: String?): Boolean {
            if (link.isNullOrBlank()) return false
            return link.contains("hobby/all")
        }

        fun fromHobbyLink(link: String, title: String): HobbyListNavParam? {
            if (!isHobbyListLink(link)) return null
            val query = link.substringAfter("?", "")
            if (query.isBlank()) return HobbyListNavParam(title = title.ifBlank { "周边列表" })
            val pairs = query.split("&").mapNotNull { part ->
                val idx = part.indexOf("=")
                if (idx <= 0) return@mapNotNull null
                val key = part.substring(0, idx)
                val raw = part.substring(idx + 1)
                val value = try {
                    raw.decodeURLPart()
                } catch (_: Exception) {
                    raw
                }
                key to value
            }.toMap()
            val order = pairs["order"]?.ifBlank { null } ?: "hits7Day"
            val sex = pairs["sex"]?.trim()?.trim(',')?.toIntOrNull()?.takeIf { it != -1 }
            val specs = joinSpecs(parseSpecsSet(pairs["specs"]))
            val scale = pairs["scale"]?.trim()?.trim(',')?.ifBlank { null }?.takeIf { it != "0" && it != "0.0" }
            val releaseYear = pairs["releaseYear"]?.trim()?.trim(',')?.toIntOrNull()?.takeIf { it != 0 }
            val releaseMonth = pairs["releaseMonth"]?.trim()?.trim(',')?.toIntOrNull()?.takeIf { it != 0 }
            val categoryId = pairs["category"]?.trim()?.trim(',')?.toIntOrNull()?.takeIf { it != 0 } ?: 10000
            val r18 = pairs["r18"]?.trim()?.trim(',')?.toIntOrNull()?.takeIf { it != -1 && it != 399 }
            val workers = pairs["workers"]?.trim()?.trim(',')?.ifBlank { null }
            val tag = pairs["tag"]?.trim()?.ifBlank { null }
            return HobbyListNavParam(
                title = title.ifBlank { "周边列表" },
                order = order,
                categoryId = categoryId,
                releaseYear = releaseYear,
                releaseMonth = releaseMonth,
                sex = sex,
                specs = specs,
                scale = scale,
                r18 = r18,
                workers = workers,
                tag = tag
            )
        }

        fun parseSpecsSet(specs: String?): Set<String> {
            if (specs.isNullOrBlank()) return emptySet()
            return specs.split(",").map { it.trim().trim(',') }.filter { it.isNotBlank() }.toSet()
        }

        fun joinSpecs(codes: Set<String>): String? {
            val cleaned = codes.map { it.trim().trim(',') }.filter { it.isNotBlank() }.sorted()
            return cleaned.joinToString(",").ifBlank { null }
        }
    }
}

object FilterLabels {
    val sexOrder = listOf(0, 1, 2, 3)
    val sexNames = mapOf(0 to "妹子", 1 to "汉子", 2 to "无性别", 3 to "未知")
    val specsOrder = listOf("649223", "649224", "649231", "649234", "649225")
    val specsNames = mapOf(
        "649223" to "景品",
        "649224" to "GK",
        "649231" to "可动",
        "649234" to "可脱",
        "649225" to "可变形"
    )
    val scaleOrder = listOf("1.0", "2.5", "4.0", "5.0", "6.0", "7.0", "8.0", "10.0", "12.0", "60.0", "100.0", "144.0", "300.0")
    val r18Order = listOf(300, 312, 315, 318, 320, 15, 18, 215, 218)
    val r18Names = mapOf(
        300 to "观感安心",
        312 to "观感温和",
        315 to "观感成熟",
        318 to "观感谨慎",
        320 to "观感谨慎+",
        15 to "低于观感温和",
        18 to "低于观感成熟",
        215 to "高于观感成熟",
        218 to "高于观感谨慎"
    )

    fun sexName(sex: Int?): String? {
        if (sex == null || sex == -1) return null
        return sexNames[sex] ?: sex.toString()
    }

    fun scaleName(scale: String?): String? {
        val clean = scale?.trim()?.trim(',')?.ifBlank { null }?.takeIf { it != "0" && it != "0.0" } ?: return null
        val number = clean.toFloatOrNull()
        val shown = if (number != null && number == number.toInt().toFloat()) number.toInt().toString() else clean
        return "1/$shown"
    }

    fun r18Name(r18: Int?): String? {
        if (r18 == null || r18 == -1 || r18 == 399) return null
        return r18Names[r18] ?: r18.toString()
    }

    fun composeTitle(base: String, sex: Int?, specs: String?, scale: String?, r18: Int?, tag: String?): String {
        val parts = mutableListOf<String>()
        r18Name(r18)?.let { parts.add(it) }
        HobbyListNavParam.parseSpecsSet(specs).sorted().mapNotNull { specsNames[it] ?: it.ifBlank { null } }.forEach { parts.add(it) }
        sexName(sex)?.let { parts.add(it) }
        scaleName(scale)?.let { parts.add(it) }
        tag?.trim()?.ifBlank { null }?.let { parts.add(it) }
        if (parts.isEmpty()) return base
        return "$base(${parts.joinToString(" ")})"
    }
}
