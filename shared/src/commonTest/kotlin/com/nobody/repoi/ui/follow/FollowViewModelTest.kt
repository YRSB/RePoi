package com.nobody.repoi.ui.follow

import com.nobody.repoi.data.repository.FollowRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.session.FakeUserSession
import kotlin.test.Test
import kotlin.test.assertEquals

class FollowViewModelTest {

    @Test
    fun scrollPosition_saveAndReset() {
        val mock = MockApi()
        val session = FakeUserSession("token")
        val viewModel = FollowViewModel(
            FollowRepository(mock.api(), mock.json, session),
            session
        )

        viewModel.saveScrollPosition(3, 7)
        viewModel.saveFilterCollapseFraction(0.5f)
        assertEquals(3, viewModel.savedScrollIndex)
        assertEquals(7, viewModel.savedScrollOffset)
        assertEquals(0.5f, viewModel.savedFilterCollapseFraction)

        viewModel.saveFilterCollapseFraction(2f)
        assertEquals(1f, viewModel.savedFilterCollapseFraction)

        viewModel.resetScrollPosition()
        assertEquals(0, viewModel.savedScrollIndex)
        assertEquals(0, viewModel.savedScrollOffset)
        assertEquals(0f, viewModel.savedFilterCollapseFraction)
    }
}
