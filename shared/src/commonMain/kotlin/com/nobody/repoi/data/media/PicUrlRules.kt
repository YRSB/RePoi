package com.nobody.repoi.data.media

internal object PicUrlRules {

    const val SMALL = "pic/s/"

    const val NORMAL = "pic/n/"

    const val RAW = "pic/raw/"

    val PREFIXES: List<String> = listOf(SMALL, NORMAL, RAW)

    fun resolve(path: String?, purpose: ImagePurpose): String =
        DefaultMediaPipeline.buildVariantUrl(path, PREFIXES, prefixOf(purpose))

    fun clean(path: String): String = DefaultMediaPipeline.cleanVariantPath(path, PREFIXES)

    fun build(path: String?, targetPrefix: String): String =
        DefaultMediaPipeline.buildVariantUrl(path, PREFIXES, targetPrefix)

    private fun prefixOf(purpose: ImagePurpose): String = when (purpose) {
        ImagePurpose.THUMBNAIL -> SMALL
        ImagePurpose.ADAPTIVE_GRID -> NORMAL
        ImagePurpose.CARD_STANDARD -> NORMAL
        ImagePurpose.FULLSCREEN_RAW -> RAW
    }
}
