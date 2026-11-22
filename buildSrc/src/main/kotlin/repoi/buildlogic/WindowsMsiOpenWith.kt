package repoi.buildlogic

import java.io.File

fun configureWindowsMsiOpenWith(msi: File, temporaryDirectory: File, extensions: Set<String>) {
    if (extensions.isEmpty()) return
    error(
        "MSI Open With 配置需要 windows-open-with.ps1 脚本资源（M6 未携带：" +
            "PoiShelf 无文件关联需求）。目标 MSI：${msi.absolutePath}"
    )
}
