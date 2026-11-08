package com.nobody.repoi.data.media

/**
 * 用户相片（`pic/`）家族的纯 CDN 规则。
 *
 * `MediaCategory` 枚举已在 ADR-0007 冻结，没有「用户相片」独立分类槽位，而动态流附图、
 * 用户相片查看器等路径必须落在 `pic/` 家族；因此把这套规则收敛为**接口默认实现**承载的
 * 深模块（[MediaPipeline.resolvePictureUrl] 默认体即委托本对象），任何 [MediaPipeline] 实现都能
 * 按需覆写，不再依赖「扩展函数强制走默认单例」的旧桥接。
 *
 * 规则与 [DefaultMediaPipeline] 的 `cover` 家族逐字节一致：
 * - 绝对 URL 只把非目标变体段替换成目标段，不改域名；
 * - 相对路径先剥掉已知变体前缀，再拼 `https://rfx.hpoi.net/gk/{目标段}`；
 * - 空/空白路径返回空串。
 */
internal object PicUrlRules {

    /** 小图（`pic/s/`，1:1 方形最小档）。 */
    const val SMALL = "pic/s/"

    /** 中图/大图（`pic/n/`）。 */
    const val NORMAL = "pic/n/"

    /** 原图（`pic/raw/`）。 */
    const val RAW = "pic/raw/"

    /** 用户相片全部变体前缀（服务端无 `pic/sr/`）。 */
    val PREFIXES: List<String> = listOf(SMALL, NORMAL, RAW)

    /**
     * 把用户相片路径解析为目标用途对应的完整 CDN URL。
     *
     * 用途映射：`THUMBNAIL` → `pic/s/`；`ADAPTIVE_GRID` / `CARD_STANDARD` → `pic/n/`；
     * `FULLSCREEN_RAW` → `pic/raw/`。
     */
    fun resolve(path: String?, purpose: ImagePurpose): String =
        DefaultMediaPipeline.buildVariantUrl(path, PREFIXES, prefixOf(purpose))

    /** 清理用户相片路径中的变体前缀（供兼容桥接 `cleanPicPath` 使用）。 */
    fun clean(path: String): String = DefaultMediaPipeline.cleanVariantPath(path, PREFIXES)

    /** 以指定变体前缀构建用户相片 URL（供兼容桥接 `buildPicUrl` 使用）。 */
    fun build(path: String?, targetPrefix: String): String =
        DefaultMediaPipeline.buildVariantUrl(path, PREFIXES, targetPrefix)

    /** `pic/` 家族**唯一**一处「用途 → 变体前缀」映射。 */
    private fun prefixOf(purpose: ImagePurpose): String = when (purpose) {
        ImagePurpose.THUMBNAIL -> SMALL
        ImagePurpose.ADAPTIVE_GRID -> NORMAL
        ImagePurpose.CARD_STANDARD -> NORMAL
        ImagePurpose.FULLSCREEN_RAW -> RAW
    }
}
