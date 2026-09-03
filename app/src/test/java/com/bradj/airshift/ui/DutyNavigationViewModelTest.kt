package com.bradj.airshift.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DutyNavigationViewModelTest {
    @Test
    fun `a fresh instance starts on the current duty section`() {
        assertEquals(DutySection.CURRENT, DutyNavigationViewModel().section.value)
    }

    @Test
    fun `the first foreground transition forces the current duty section`() {
        val viewModel = DutyNavigationViewModel()
        viewModel.selectSection(DutySection.SETTINGS)

        viewModel.onActivityForegrounded()

        assertEquals(DutySection.CURRENT, viewModel.section.value)
    }

    @Test
    fun `returning from the background forces the current duty section`() {
        val viewModel = DutyNavigationViewModel()
        viewModel.onActivityForegrounded()
        viewModel.selectSection(DutySection.ALL)
        viewModel.onActivityBackgrounded(isChangingConfigurations = false)

        viewModel.onActivityForegrounded()

        assertEquals(DutySection.CURRENT, viewModel.section.value)
    }

    @Test
    fun `a configuration change keeps the selected section`() {
        val viewModel = DutyNavigationViewModel()
        viewModel.onActivityForegrounded()
        viewModel.selectSection(DutySection.ALL)
        viewModel.onActivityBackgrounded(isChangingConfigurations = true)

        viewModel.onActivityForegrounded()

        assertEquals(DutySection.ALL, viewModel.section.value)
    }

    @Test
    fun `the shift calendar section can be selected like any other tab`() {
        val viewModel = DutyNavigationViewModel()
        viewModel.onActivityForegrounded()

        viewModel.selectSection(DutySection.CALENDAR)

        assertEquals(DutySection.CALENDAR, viewModel.section.value)
    }

    @Test
    fun `a configuration change keeps the shift calendar selected`() {
        val viewModel = DutyNavigationViewModel()
        viewModel.onActivityForegrounded()
        viewModel.selectSection(DutySection.CALENDAR)
        viewModel.onActivityBackgrounded(isChangingConfigurations = true)

        viewModel.onActivityForegrounded()

        assertEquals(DutySection.CALENDAR, viewModel.section.value)
    }

    @Test
    fun `returning from the background leaves the shift calendar for the current duty`() {
        val viewModel = DutyNavigationViewModel()
        viewModel.onActivityForegrounded()
        viewModel.selectSection(DutySection.CALENDAR)
        viewModel.onActivityBackgrounded(isChangingConfigurations = false)

        viewModel.onActivityForegrounded()

        assertEquals(DutySection.CURRENT, viewModel.section.value)
    }

    @Test
    fun `repeated foreground transitions without a background stop keep the selected section`() {
        val viewModel = DutyNavigationViewModel()
        viewModel.onActivityForegrounded()
        viewModel.selectSection(DutySection.SETTINGS)

        viewModel.onActivityForegrounded()

        assertEquals(DutySection.SETTINGS, viewModel.section.value)
    }
}
