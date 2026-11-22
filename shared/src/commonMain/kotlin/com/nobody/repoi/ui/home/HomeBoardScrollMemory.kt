package com.nobody.repoi.ui.home

import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.ui.board.session.BoardScrollPosition

data class HomeBoardScrollMemory(
    val index: Int = 0,
    val offset: Int = 0,
    val filterCollapseFraction: Float = 0f,
    val filterRowScrollX: Int = 0
)

fun BoardScrollPosition.toHomeScrollMemory(): HomeBoardScrollMemory = HomeBoardScrollMemory(
    index = index,
    offset = offset,
    filterCollapseFraction = filterCollapseFraction,
    filterRowScrollX = filterRowScrollX
)

fun homeBoardMemoryKey(scope: ZoneQueryScope, board: ZoneBoard): String =
    "${scope.stateKey()}|${board.type}"
