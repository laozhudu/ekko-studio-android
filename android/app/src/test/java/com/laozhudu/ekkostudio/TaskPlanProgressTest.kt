package com.laozhudu.ekkostudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The live task card's maths, kept out of the composable so it can be reasoned
 * about without a device.
 *
 * `execution_state` values seen on 0.7.38: `running` while the agent works,
 * `ended` once the run finishes.
 */
class TaskPlanProgressTest {

    private fun step(id: String, status: String) = TaskPlanStep(id = id, step = "s$id", status = status)

    @Test
    fun progressCountsOnlyCompletedSteps() {
        val steps = listOf(step("1", "completed"), step("2", "completed"), step("3", "in_progress"), step("4", "pending"))
        assertEquals(2 to 4, planProgress(steps))
    }

    @Test
    fun anEmptyPlanReportsZeroOfZero() {
        assertEquals(0 to 0, planProgress(emptyList()))
    }

    @Test
    fun theRunningStepWinsOverTheNextPendingOne() {
        val steps = listOf(step("1", "completed"), step("2", "pending"), step("3", "in_progress"))
        assertEquals("3", planCurrentStep(steps)?.id)
    }

    @Test
    fun withoutARunningStepTheNextPendingOneIsShown() {
        val steps = listOf(step("1", "completed"), step("2", "pending"))
        assertEquals("2", planCurrentStep(steps)?.id)
    }

    @Test
    fun aFinishedPlanHasNoCurrentStep() {
        val steps = listOf(step("1", "completed"), step("2", "completed"))
        assertNull(planCurrentStep(steps))
    }
}
