package com.adulthub.tv.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class MobileNavigationRouteTest {
    @Test
    fun `library tab navigates to a concrete default route`() {
        assertEquals("library", mobileTopLevelNavigationRoute(Screen.Library.route))
    }

    @Test
    fun `search tab navigates without a query placeholder`() {
        assertEquals("search", mobileTopLevelNavigationRoute(Screen.Search.route))
    }
}
