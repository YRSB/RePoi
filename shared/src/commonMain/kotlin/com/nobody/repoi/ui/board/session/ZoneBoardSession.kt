package com.nobody.repoi.ui.board.session

import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.ui.board.ZoneBoard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

interface ZoneBoardSession {

    val activeSnapshot: StateFlow<ZoneBoardSnapshot>

    val actionSnapshot: StateFlow<ZoneBoardSnapshot>

    fun snapshotFor(board: ZoneBoard): ZoneBoardSnapshot

    fun dispatch(intent: ZoneBoardIntent)

    fun updateScope(scope: ZoneQueryScope)

    fun resumeInitialLoad()

    interface Factory {
        fun create(
            coroutineScope: CoroutineScope,
            initialScope: ZoneQueryScope = ZoneQueryScope.All,
            initialBoard: ZoneBoard = ZoneBoard.Action,

            deferInitialLoad: Boolean = false
        ): ZoneBoardSession
    }
}
