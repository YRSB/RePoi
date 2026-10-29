package repoi.buildlogic

/**
 * RePoi Windows 文件关联表（M6，PodAura `WindowsMediaFileAssociations.kt` 范式移植）。
 *
 * PoiShelf 无媒体播放器、无文档类型：FileKit 仅用于聊天图片选择（无扩展名注册需求），
 * 故返回空表。空表使下游三处钩子（`windows{}` fileAssociation、MSIX manifest、
 * MSI OpenWith）均为类型完备的 no-op，保留 PodAura 线路图形状以便将来注册扩展时补行。
 */
fun repoiFileAssociations(): Map<String, String> = emptyMap()
