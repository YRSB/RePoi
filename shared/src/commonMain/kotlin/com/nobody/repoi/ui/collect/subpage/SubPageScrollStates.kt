package com.nobody.repoi.ui.collect.subpage

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable

internal fun scrollStateSaveableKey(scope: String, keys: Array<out Any?>): String =
    buildString {
        append(scope)
        for (key in keys) {
            if (key == null) {
                append(":n")
            } else {
                val text = key.toString()
                append(':').append(text.length).append(':').append(text)
            }
        }
    }

@Composable
internal fun rememberKeyedLazyListState(scope: String, vararg keys: Any?): LazyListState =
    key(scrollStateSaveableKey(scope, keys)) {
        rememberSaveable(saver = LazyListState.Saver) {
            LazyListState()
        }
    }

@Composable
internal fun rememberKeyedLazyGridState(scope: String, vararg keys: Any?): LazyGridState =
    key(scrollStateSaveableKey(scope, keys)) {
        rememberSaveable(saver = LazyGridState.Saver) {
            LazyGridState()
        }
    }

@Composable
internal fun rememberKeyedLazyStaggeredGridState(scope: String, vararg keys: Any?): LazyStaggeredGridState =
    key(scrollStateSaveableKey(scope, keys)) {
        rememberSaveable(saver = LazyStaggeredGridState.Saver) {
            LazyStaggeredGridState()
        }
    }
