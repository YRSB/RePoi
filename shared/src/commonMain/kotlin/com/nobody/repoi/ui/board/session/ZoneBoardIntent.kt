package com.nobody.repoi.ui.board.session

import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.item.list.components.HobbyAdvancedFilterParam

sealed interface ZoneBoardIntent {

    data class SelectBoard(val board: ZoneBoard) : ZoneBoardIntent

    data class LoadMore(val board: ZoneBoard? = null) : ZoneBoardIntent

    data class RetryInitial(val board: ZoneBoard? = null) : ZoneBoardIntent

    data class Refresh(val board: ZoneBoard? = null) : ZoneBoardIntent

    data class SelectSubType(val subType: String) : ZoneBoardIntent

    data class SelectAlbumOrder(val order: String) : ZoneBoardIntent

    data class SelectAlbumCategory(val categoryId: Int?) : ZoneBoardIntent

    data class SelectPostsOrder(val order: String) : ZoneBoardIntent

    data class SelectResaleType(val type: Int) : ZoneBoardIntent

    data class SelectResalePurity(val purity: String?) : ZoneBoardIntent

    data class UpdateResaleKeyword(val keyword: String) : ZoneBoardIntent

    data object SubmitResaleSearch : ZoneBoardIntent

    data class SelectHobbyOrder(val order: String) : ZoneBoardIntent

    data class SelectHobbyYear(val year: Int?) : ZoneBoardIntent

    data class SelectHobbyMonth(val month: Int?) : ZoneBoardIntent

    data class SelectHobbyCategory(val categoryId: Int, val subCategoryId: Int?) : ZoneBoardIntent

    data class ApplyHobbyFilter(val filterParam: HobbyAdvancedFilterParam) : ZoneBoardIntent

    data object ResetHobbyFilter : ZoneBoardIntent

    data object ToggleHobbyViewMode : ZoneBoardIntent

    data class SetHobbyFilterSheetVisible(val visible: Boolean) : ZoneBoardIntent

    data class SetHobbyCategoryPickerVisible(val visible: Boolean) : ZoneBoardIntent

    data class UpdateHobbyKeyword(val keyword: String) : ZoneBoardIntent

    data object SubmitHobbySearch : ZoneBoardIntent

    data class SaveScrollPosition(val board: ZoneBoard, val index: Int, val offset: Int) : ZoneBoardIntent

    data class SaveFilterCollapseFraction(val board: ZoneBoard, val fraction: Float) : ZoneBoardIntent

    data class SaveFilterRowScroll(val board: ZoneBoard, val scrollX: Int) : ZoneBoardIntent

    data class ResetScrollMemory(val board: ZoneBoard) : ZoneBoardIntent
}
