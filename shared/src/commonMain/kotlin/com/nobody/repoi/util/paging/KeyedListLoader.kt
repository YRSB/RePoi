package com.nobody.repoi.util.paging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

open class KeyedListLoader<T, Key>(
    protected val scope: CoroutineScope,
    protected val initialKey: Key,
    protected val keySelector: ((T) -> Any?)? = null,
    protected val nextKeyProvider: (currentKey: Key, state: PagedListState<T>) -> Key?,
    protected val fetcher: suspend (key: Key) -> Result<PageChunk<T>>
) {

    protected val _state = MutableStateFlow(
        PagedListState<T>(
            page = if (initialKey is Int) initialKey else 1
        )
    )
    val state: StateFlow<PagedListState<T>> = _state.asStateFlow()

    var currentKey: Key = initialKey
        protected set

    private var activeJob: Job? = null

    fun loadInitial(clearItems: Boolean = true) {
        cancelActiveJob()
        currentKey = initialKey
        _state.update {
            it.copy(
                stage = LoadStage.InitialLoading,
                items = if (clearItems) emptyList() else it.items,
                initialError = null,
                appendError = null,
                page = if (initialKey is Int) initialKey else 1
            )
        }
        executeFetch(key = initialKey, isRefresh = false, isAppend = false)
    }

    fun refresh() {
        cancelActiveJob()
        currentKey = initialKey
        _state.update {
            it.copy(
                stage = LoadStage.Refreshing,
                initialError = null,
                appendError = null
            )
        }
        executeFetch(key = initialKey, isRefresh = true, isAppend = false)
    }

    fun loadMore(isSilent: Boolean = false) {
        val snapshot = _state.value
        if (snapshot.stage != LoadStage.Idle || !snapshot.hasMore) return
        if (snapshot.items.isEmpty() && snapshot.initialError != null) return

        val nextKey = nextKeyProvider(currentKey, snapshot) ?: run {
            _state.update { it.copy(hasMore = false) }
            return
        }

        _state.update {
            it.copy(
                stage = LoadStage.Appending,
                appendError = null,
                isSilent = isSilent
            )
        }
        executeFetch(key = nextKey, isRefresh = false, isAppend = true)
    }

    fun retry() {
        val snapshot = _state.value
        when {
            snapshot.initialError != null -> {
                if (snapshot.items.isEmpty()) {
                    loadInitial()
                } else {
                    refresh()
                }
            }
            snapshot.appendError != null -> {
                loadMore()
            }
        }
    }

    fun mutateItems(transform: (List<T>) -> List<T>) {
        _state.update { it.copy(items = transform(it.items)) }
    }

    fun updateItem(predicate: (T) -> Boolean, transform: (T) -> T) {
        mutateItems { list ->
            list.map { item -> if (predicate(item)) transform(item) else item }
        }
    }

    fun removeItem(predicate: (T) -> Boolean) {
        mutateItems { list ->
            list.filterNot(predicate)
        }
    }

    fun setItems(items: List<T>) {
        _state.update { it.copy(items = deduplicate(items)) }
    }

    fun setHasMore(hasMore: Boolean) {
        _state.update { it.copy(hasMore = hasMore) }
    }

    fun clearError() {
        _state.update { it.copy(initialError = null, appendError = null) }
    }

    fun reset(clearItems: Boolean = true) {
        cancelActiveJob()
        currentKey = initialKey
        _state.value = PagedListState(
            items = if (clearItems) emptyList() else _state.value.items,
            stage = LoadStage.Idle,
            hasMore = true,
            page = if (initialKey is Int) initialKey else 1
        )
    }

    private fun cancelActiveJob() {
        activeJob?.cancel()
        activeJob = null
    }

    private fun executeFetch(key: Key, isRefresh: Boolean, isAppend: Boolean) {
        activeJob = scope.launch {
            try {
                val result = fetcher(key)
                if (!isActive) return@launch

                result.fold(
                    onSuccess = { chunk ->
                        if (!isActive) return@fold
                        currentKey = key
                        val sanitizedItems = deduplicate(chunk.items)
                        _state.update { current ->
                            val mergedItems = if (isAppend) {
                                appendDeduplicated(current.items, chunk.items)
                            } else {
                                sanitizedItems
                            }
                            current.copy(
                                items = mergedItems,
                                stage = LoadStage.Idle,
                                hasMore = chunk.hasMore,
                                initialError = null,
                                appendError = null,
                                isSilent = false,
                                page = if (key is Int) key else if (isAppend) current.page + 1 else 1
                            )
                        }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        if (!isActive) return@fold
                        val message = error.message ?: if (isAppend) "加载更多失败" else if (isRefresh) "刷新失败" else "加载失败"
                        _state.update { current ->
                            if (isAppend) {
                                current.copy(
                                    stage = LoadStage.Idle,
                                    appendError = message,
                                    isSilent = false
                                )
                            } else {
                                current.copy(
                                    stage = LoadStage.Idle,
                                    initialError = message,
                                    isSilent = false
                                )
                            }
                        }
                    }
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (!isActive) return@launch
                val message = t.message ?: if (isAppend) "加载更多失败" else "加载失败"
                _state.update { current ->
                    if (isAppend) {
                        current.copy(stage = LoadStage.Idle, appendError = message, isSilent = false)
                    } else {
                        current.copy(stage = LoadStage.Idle, initialError = message, isSilent = false)
                    }
                }
            }
        }
    }

    private fun deduplicate(items: List<T>): List<T> {
        val selector = keySelector ?: return items
        val keys = HashSet<Any>()
        val result = ArrayList<T>(items.size)
        for (item in items) {
            val key = selector(item)
            if (key == null || keys.add(key)) {
                result.add(item)
            }
        }
        return result
    }

    private fun appendDeduplicated(existing: List<T>, fetched: List<T>): List<T> {
        if (fetched.isEmpty()) return existing
        val selector = keySelector ?: return existing + fetched
        val existingKeys = existing.mapNotNullTo(HashSet()) { selector(it) }
        val newUnique = ArrayList<T>(fetched.size)
        for (item in fetched) {
            val key = selector(item)
            if (key == null || existingKeys.add(key)) {
                newUnique.add(item)
            }
        }
        return existing + newUnique
    }
}
