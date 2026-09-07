package com.bradj.airshift.parser

import org.junit.Assert.assertEquals
import org.junit.Test

/** 全部用“常见姓氏 + 天干地支”的合成姓名，与真实人员无关。 */
class ChineseNameSplitterTest {
    @Test
    fun `three character names are split apart`() {
        assertEquals(listOf("王甲子", "李乙丑", "周丙寅"), ChineseNameSplitter.split("王甲子李乙丑周丙寅"))
    }

    @Test
    fun `two and three character names mix in either order`() {
        assertEquals(listOf("王甲", "李乙丑"), ChineseNameSplitter.split("王甲李乙丑"))
        assertEquals(listOf("王甲子", "李乙"), ChineseNameSplitter.split("王甲子李乙"))
        assertEquals(listOf("王甲", "李乙", "周丙"), ChineseNameSplitter.split("王甲李乙周丙"))
        assertEquals(listOf("吴甲", "吴乙丑", "吴丙寅"), ChineseNameSplitter.split("吴甲吴乙丑吴丙寅"))
    }

    @Test
    fun `an ambiguous cut prefers the more common surname`() {
        // 王甲成 / 李乙 与 王甲 / 成李乙 都合法；李远比成常见。
        assertEquals(listOf("王甲成", "李乙"), ChineseNameSplitter.split("王甲成李乙"))
    }

    @Test
    fun `given name characters that are not surnames block the wrong cut`() {
        // 萱、晨、陇、杉 都不是姓，只有一种切法。
        assertEquals(listOf("霍甲萱", "裴乙"), ChineseNameSplitter.split("霍甲萱裴乙"))
        assertEquals(listOf("牛甲晨", "罗乙"), ChineseNameSplitter.split("牛甲晨罗乙"))
        assertEquals(listOf("窦甲杉", "高乙"), ChineseNameSplitter.split("窦甲杉高乙"))
    }

    @Test
    fun `compound surnames take three or four characters`() {
        assertEquals(listOf("欧阳甲子", "李乙"), ChineseNameSplitter.split("欧阳甲子李乙"))
        assertEquals(listOf("司马甲", "李乙丑"), ChineseNameSplitter.split("司马甲李乙丑"))
    }

    @Test
    fun `short inputs are returned as a single name`() {
        assertEquals(listOf("王甲"), ChineseNameSplitter.split("王甲"))
        assertEquals(listOf("王甲子"), ChineseNameSplitter.split(" 王甲子 "))
    }

    @Test
    fun `unknown surnames and non chinese text are left whole`() {
        assertEquals(listOf("测试甲测试乙"), ChineseNameSplitter.split("测试甲测试乙"))
        assertEquals(listOf("MU6771"), ChineseNameSplitter.split("MU6771"))
        assertEquals(listOf("王甲子MU6771"), ChineseNameSplitter.split("王甲子MU6771"))
    }
}
