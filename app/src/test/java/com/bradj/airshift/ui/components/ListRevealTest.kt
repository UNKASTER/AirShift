package com.bradj.airshift.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 展开跟随每帧推多少：坐标沿用 LazyListLayoutInfo 的约定——0 是顶部内边距之下的第一像素，
 * 视口顶是负的内边距（这里 -45），视口底是容器高度减去顶部内边距（这里 2000），条间距 30。
 */
class ListRevealTest {
    private fun push(itemTop: Int, itemBottom: Int) =
        revealPushPx(itemTop = itemTop, itemBottom = itemBottom, viewportTop = -45, viewportBottom = 2000, spacing = 30)

    @Test
    fun `a strip fully inside the viewport needs no push`() {
        assertEquals(0, push(itemTop = 100, itemBottom = 400))
        assertEquals(0, push(itemTop = 1700, itemBottom = 2000))
    }

    @Test
    fun `the push equals the overflow below the viewport`() {
        assertEquals(120, push(itemTop = 1500, itemBottom = 2120))
    }

    @Test
    fun `a strip taller than the viewport stops one spacing below the top`() {
        // 条顶 100、视口顶 -45：最多推 100 + 45 - 30 = 115，之后条顶离视口顶正好一个条间距。
        assertEquals(115, push(itemTop = 100, itemBottom = 3000))
    }

    @Test
    fun `a strip whose top is already at or above the limit is never pushed further`() {
        assertEquals(0, push(itemTop = -15, itemBottom = 3000))
        assertEquals(0, push(itemTop = -60, itemBottom = 3000))
    }
}
