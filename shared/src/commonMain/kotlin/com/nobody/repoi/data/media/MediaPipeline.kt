package com.nobody.repoi.data.media

/**
 * 多媒体资源所属业务实体或内容域的分类。
 *
 * 每个分类对应 Hpoi CDN 上一套独立的路径目录与可用变体档位，具体变体映射与 404 避坑规则
 * 由 [MediaPipeline.resolveUrl] 统一裁决，调用端无需再记忆任何目录差异。
 */
enum class MediaCategory {
    /** 手办/条目封面（`cover/` 家族，四档变体齐全）。 */
    HOBBY,

    /**
     * 相册。
     *
     * Hpoi CDN 未部署相册封面的 `cover/n/` 目录（请求必然 404），因此列表、详情等全部用途
     * 一律落在保留原始纵横比的 `cover/sr/`；仅全屏原图查看沿用历史契约 `cover/raw/`
     * （见 [DefaultMediaPipeline] 中 ALBUM 分支说明）。
     */
    ALBUM,

    /** 用户头像（`head/` 家族）。 */
    AVATAR,

    /** 讨论贴配图：服务端仅部署 `posts/n/`，不存在 `posts/raw/` 与贴图专用 `/s/` 目录。 */
    POST,

    /** 用户主页横幅：服务端仅部署 `bnr/n/`，不存在 `bnr/raw/` 目录。 */
    BANNER,

    /** 二手交易卖家上传的实物照片：服务端仅部署 `resale/n/`。 */
    RESALE,

    /** 专区图标与封面：`zone/` 前缀路径直连 CDN 根，其余回退 `cover/` 缩略图与标准图。 */
    ZONE,

    /** ExHobby 扩展图库：独立域名 `https://res.e39x.com/pic/`，仅有 `s/` 与 `n/` 两档。 */
    EX_HOBBY
}

/**
 * 图像在界面呈现中的目标场景与规格期望的语义类型。
 *
 * 管道会结合 [MediaCategory] 把用途翻译成 CDN 上真实存在的变体，用途只是「期望」，
 * 服务端缺档时自动降级（例如讨论贴配图求原图时降级为 `posts/n/`）。
 */
enum class ImagePurpose {
    /** 方形小缩略图（`cover/s/`、`head/s/`、`pic/s/`、ExHobby `s/`）。 */
    THUMBNAIL,

    /** 瀑布流缩略图（`cover/sr/`，保留原始纵横比）。 */
    ADAPTIVE_GRID,

    /** 标准卡片与详情页展示（`cover/n/`、`posts/n/`、`bnr/n/`、`resale/n/`）。 */
    CARD_STANDARD,

    /** 全屏高清原图（`cover/raw/`、`pic/raw/`、`head/raw/`；讨论贴与横幅强制降级 `/n/`）。 */
    FULLSCREEN_RAW
}

/**
 * 多媒体管道深服务：集中封装 CDN 变体拼装、404 避坑规则、官方缺省图判定与纵横比缓存读写。
 *
 * 契约完全解耦 Compose / Android，可在纯 JVM 环境（含单元测试与非 UI 数据层）直接使用；
 * 提供 [MediaPipeline.Default] 伴生实例，Koin 侧另以单例形式注入同一实现。
 *
 * 用户相片（`pic/`）家族由 [resolvePictureUrl] 的**接口默认实现**承载：默认委托 [PicUrlRules]
 * 的纯规则，任何自定义实现覆写该方法即可生效（`FeedMediaResolver.resolve(item, media)` 的
 * 注入语义因此成立，不再被顶层扩展函数架空）。
 */
interface MediaPipeline {
    companion object {
        /** 默认实现单例（零依赖纯 Kotlin 对象，线程安全无状态）。 */
        val Default: MediaPipeline get() = DefaultMediaPipeline
    }

    /**
     * 把资源路径解析为目标分类与用途对应的完整 CDN URL。
     *
     * 空值/空白路径返回空串（表示「无可请求资源」），绝对 URL 保持域名不变（仅当该家族支持
     * 变体替换时才改写路径段）。
     */
    fun resolveUrl(path: String?, category: MediaCategory, purpose: ImagePurpose): String

    /** 解析全屏原图 URL，等价于 `resolveUrl(path, category, ImagePurpose.FULLSCREEN_RAW)`。 */
    fun resolveRawUrl(path: String?, category: MediaCategory): String

    /** 判断路径是否为空、空白、`null` 字面量或官方缺省吉祥物（`none.jpg`）。 */
    fun isPlaceholder(path: String?): Boolean

    /** 获取指定用途对应的官方缺省封面完整 URL（`cover/{档位}/none.jpg`）。 */
    fun getDefaultCoverUrl(purpose: ImagePurpose): String

    /** 读取纵横比缓存（键通常为资源路径），未命中返回 `null`。 */
    fun getAspectRatio(key: String): Float?

    /** 写入纵横比缓存，写入前统一截断到 `[0.4f, 2.5f]`。 */
    fun recordAspectRatio(key: String, ratio: Float)

    /**
     * 手办/条目封面「带缺省兜底」解析：命中占位符时回退该用途的官方缺省封面。
     */
    fun resolveCoverUrlOrDefault(path: String?, purpose: ImagePurpose): String =
        if (isPlaceholder(path)) getDefaultCoverUrl(purpose) else resolveUrl(path, MediaCategory.HOBBY, purpose)

    /**
     * 表情贴纸 URL（`sticker/item/` 目录，仅此处使用，非通用媒体变体家族）。
     */
    fun resolveStickerUrl(path: String?): String

    /**
     * 解析用户相片（`pic/`）家族 URL：`THUMBNAIL` → `pic/s/`，
     * `ADAPTIVE_GRID` / `CARD_STANDARD` → `pic/n/`，`FULLSCREEN_RAW` → `pic/raw/`。
     *
     * [MediaCategory] 枚举已在 ADR-0007 冻结，没有「用户相片」独立槽位，而动态流附图、
     * 用户相片查看器等路径必须落在 `pic/` 家族，故以**接口默认实现**承载这套纯规则；
     * 需要自定义 CDN 策略的实现覆写本方法即可，调用端无需知道默认单例的存在。
     */
    fun resolvePictureUrl(path: String?, purpose: ImagePurpose): String =
        PicUrlRules.resolve(path, purpose)
}
