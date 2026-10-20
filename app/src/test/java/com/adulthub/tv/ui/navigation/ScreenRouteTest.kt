package com.adulthub.tv.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRouteTest {

    @Test
    fun `library route without a category has no placeholder`() {
        assertEquals("library", Screen.Library.createRoute())
        assertEquals("library", Screen.Library.createRoute(""))
        assertFalse(Screen.Library.createRoute().contains("{categoryId}"))
    }

    @Test
    fun `library route encodes the category`() {
        assertEquals("library?categoryId=action", Screen.Library.createRoute("action"))
        assertEquals("library?categoryId=a%20b", Screen.Library.createRoute("a b"))
    }

    @Test
    fun `search route without a query has no placeholder`() {
        assertEquals("search", Screen.Search.createRoute())
        assertEquals("search", Screen.Search.createRoute("   "))
        assertFalse(Screen.Search.createRoute().contains("{query}"))
    }

    @Test
    fun `search route encodes the query`() {
        assertEquals("search?query=tag%20name", Screen.Search.createRoute("tag name"))
        assertEquals("search?query=a%2Fb", Screen.Search.createRoute("a/b"))
    }

    @Test
    fun `item detail route encodes the item id`() {
        assertEquals("item_detail/telegram%3A-100123", Screen.ItemDetail.createRoute("telegram:-100123"))
    }

    @Test
    fun `person detail route encodes id and name`() {
        assertEquals(
            "person_detail/abc%201/Jane%20Doe",
            Screen.PersonDetail.createRoute("abc 1", "Jane Doe"),
        )
    }

    @Test
    fun `player route encodes the item id and keeps the resume flag`() {
        assertEquals("player/abc", Screen.Player.createRoute("abc"))
        assertEquals("player/abc?resume=true", Screen.Player.createRoute("abc", true))
        assertEquals("player/abc?resume=false", Screen.Player.createRoute("abc", false))
    }

    @Test
    fun `spaces are encoded as percent twenty and not as plus`() {
        val encoded = RouteEncoder.encode("a b+c")
        assertEquals("a%20b%2Bc", encoded)
        assertFalse(encoded.contains("+"))
    }

    @Test
    fun `unreserved characters stay readable`() {
        assertEquals("abcXYZ019-_.~!*'()", RouteEncoder.encode("abcXYZ019-_.~!*'()"))
    }

    @Test
    fun `non ascii characters are encoded as utf-8`() {
        assertEquals("%C5%BC", RouteEncoder.encode("ż"))
        assertTrue(RouteEncoder.encode("zażółć").all { it.isDigit() || it.isLetter() && it.code < 128 || it == '%' })
    }
}
