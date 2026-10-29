package repoi.buildlogic

import java.io.File

/**
 * MSI Open With 注册钩子（M6，PodAura `WindowsMsiOpenWith.kt` 范式移植）。
 *
 * [extensions] 为空时直接返回（PoiShelf 当前无注册扩展，见 [repoiFileAssociations]）；
 * 非空时执行 jpackage MSI 的 OpenWithProgids 回填——该路径需随 `windows-open-with.ps1`
 * 脚本一并补入方可启用，M6 不携带无调用方的脚本资源。
 */
fun configureWindowsMsiOpenWith(msi: File, temporaryDirectory: File, extensions: Set<String>) {
    if (extensions.isEmpty()) return
    error(
        "MSI Open With 配置需要 windows-open-with.ps1 脚本资源（M6 未携带：" +
            "PoiShelf 无文件关联需求）。目标 MSI：${msi.absolutePath}"
    )
}
