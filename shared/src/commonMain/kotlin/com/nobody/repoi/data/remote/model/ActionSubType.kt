package com.nobody.repoi.data.remote.model

/**
 * 首页与专区详情「动态」板块共用的筛选子类型（`api/user-action/list-v3` 的 `subType`）。
 *
 * 落位说明：`apiValue` 是接口协议键值，属于数据层协议常量；界面（首页动态筛选 Chip、专区详情
 * 动态筛选 Chip）与偏好持久化（旧值归一）都从本定义取值。放在 `data/remote/model` 可同时被
 * `data/local` 与 `ui` 引用，避免下层偏好管理器反向依赖 ui 层。全仓库动态筛选项只保留这一份。
 *
 * 文案与顺序对齐 Hpoi 2.43（抓包核实，见 `docs/home-hierarchy.md` §1.2）：
 * 推荐 / 评论 / 全部情报 / 制作 / 更图 / 开订 / 延期 / 出荷 / 再版。
 *
 * 兼容说明：2.43 服务端把未知或旧键 `all` **静默按「推荐」处理**（HTTP 200 不报错，但语义错误，
 * 见 `docs/research/aggregate-api-verification.md` §2.3），因此旧持久化值 `all` 必须经
 * [fromPersisted] 归一为 [AllInfo]；发往接口的参数一律使用 [apiValue]，不得发送展示文案或旧键。
 */
enum class ActionSubType(val apiValue: String, val label: String) {
    /** 推荐（默认项）。 */
    Recommend("recommend", "推荐"),

    /** 评论（旧界面文案为「讨论」）。 */
    Comment("comment", "评论"),

    /** 全部情报（旧键 `all`，服务端已不识别，读到即归一为本项）。 */
    AllInfo("allInfo", "全部情报"),

    /** 制作。 */
    Confirm("confirm", "制作"),

    /** 更图（旧界面文案为「官图」）。 */
    OfficialPic("official_pic", "更图"),

    /** 开订（旧界面文案为「预定」）。 */
    Preorder("preorder", "开订"),

    /** 延期。 */
    Delay("delay", "延期"),

    /** 出荷。 */
    Release("release", "出荷"),

    /** 再版。 */
    Reorder("reorder", "再版");

    companion object {
        /** 默认筛选项：首页/专区详情冷启动与非法值回落都指向它。 */
        val DEFAULT: ActionSubType = Recommend

        /** 2.43 顺序的完整筛选列表（界面 Chip 顺序与顺序断言共用）。 */
        val ordered: List<ActionSubType> = entries

        /**
         * 旧键 → 新定义的唯一兼容映射落点。
         *
         * 仅用于读取历史持久化值（服务端对 `all` 静默回落推荐，不能原样发出）；
         * 请求侧仍只发 [apiValue]。
         */
        private val LEGACY_KEYS: Map<String, ActionSubType> = mapOf("all" to AllInfo)

        /** 解析接口使用的协议键值；未知值返回 null（不做回落，供调用方自行决定）。 */
        fun fromApi(raw: String?): ActionSubType? = entries.firstOrNull { it.apiValue == raw }

        /** 解析持久化值或内存值：旧键兼容映射，空值/非法值回落 [DEFAULT]。 */
        fun fromPersisted(raw: String?): ActionSubType =
            fromApi(raw) ?: raw?.let { LEGACY_KEYS[it] } ?: DEFAULT

        /** 把任意来源的筛选值归一为可落盘、可发请求的协议键值。 */
        fun normalizeKey(raw: String?): String = fromPersisted(raw).apiValue
    }
}
