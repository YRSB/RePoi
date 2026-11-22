package com.nobody.repoi.ui.collect

import com.nobody.repoi.data.remote.model.UserCollectCategory

sealed interface UserCollectIntent {

    data class SwitchCategory(val category: UserCollectCategory) : UserCollectIntent

    data object ToggleViewMode : UserCollectIntent

    data class SwitchCollectState(val state: String) : UserCollectIntent

    data class SwitchOrder(val order: String) : UserCollectIntent

    data class SwitchSortDirection(val sortType: Int) : UserCollectIntent

    data class SelectSubCategory(val id: Int?) : UserCollectIntent

    data class SelectTag(val tag: String?) : UserCollectIntent

    data object ResetFilter : UserCollectIntent

    data class SetFilterSheetVisible(val visible: Boolean) : UserCollectIntent

    data class LoadHobbyTags(val force: Boolean) : UserCollectIntent

    data class SwitchAlbumOrder(val order: String) : UserCollectIntent

    data class SwitchPicOrder(val order: String) : UserCollectIntent

    data class SwitchCommonOrder(val order: String) : UserCollectIntent

    data class SwitchWorksState(val state: Int) : UserCollectIntent

    data class SwitchWorksOrderAsc(val asc: Boolean) : UserCollectIntent

    data class SelectWorksTag(val tag: String?) : UserCollectIntent

    data object ResetWorksTag : UserCollectIntent

    data class SetWorksFilterSheetVisible(val visible: Boolean) : UserCollectIntent

    data class LoadWorksTags(val force: Boolean) : UserCollectIntent

    data class LoadFirstPage(val category: UserCollectCategory) : UserCollectIntent

    data class Refresh(val category: UserCollectCategory) : UserCollectIntent

    data class LoadNextPage(val category: UserCollectCategory) : UserCollectIntent

    data class DismissError(val category: UserCollectCategory) : UserCollectIntent

    data class ToggleSelectionMode(val category: UserCollectCategory) : UserCollectIntent

    data class ToggleSelect(val id: Long) : UserCollectIntent

    data class SelectAll(val category: UserCollectCategory) : UserCollectIntent

    data class ClearSelection(val category: UserCollectCategory) : UserCollectIntent

    data object ConfirmBatchCancel : UserCollectIntent

    data object DismissBatchCancelError : UserCollectIntent

    data object OnResume : UserCollectIntent
}
