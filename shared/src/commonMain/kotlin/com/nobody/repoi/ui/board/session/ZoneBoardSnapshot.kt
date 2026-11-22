package com.nobody.repoi.ui.board.session

import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardContent
import com.nobody.repoi.ui.board.ZoneBoardFilters
import com.nobody.repoi.ui.board.ZoneBoardPageView
import com.nobody.repoi.ui.board.itemCount

data class BoardScrollPosition(
    val index: Int = 0,
    val offset: Int = 0,
    val filterCollapseFraction: Float = 0f,
    val filterRowScrollX: Int = 0
)

data class ZoneBoardSnapshot(
    val board: ZoneBoard,
    val content: ZoneBoardContent,
    val filters: ZoneBoardFilters,
    val pageView: ZoneBoardPageView,
    val scrollPosition: BoardScrollPosition = BoardScrollPosition()
)

internal fun ZoneBoardSnapshot.withPendingInitialLoad(): ZoneBoardSnapshot =
    copy(pageView = pageView.copy(isLoading = true, errorMessage = null))

val ZoneBoardSnapshot.itemCount: Int
    get() = content.itemCount
