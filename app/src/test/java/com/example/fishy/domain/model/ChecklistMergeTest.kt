package com.example.fishy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistMergeTest {

    @Test
    fun plannedFirstThenIncompletePrep() {
        val planned = listOf(ChecklistTask(title = "Seal doors", isCompleted = true))
        val prep = listOf(ChecklistTask(title = "Call driver"))
        val merged = mergeShipmentChecklist(planned, prep)
        assertEquals(listOf("Seal doors", "Call driver"), merged.map { it.title })
        assertTrue(merged[0].isCompleted)
        assertFalse(merged[1].isCompleted)
    }

    @Test
    fun skipsPrepWhenTitleMatchesPlannedIgnoringCaseAndTrim() {
        val planned = listOf(ChecklistTask(title = "  Docs ready  "))
        val prep = listOf(
            ChecklistTask(title = "docs ready"),
            ChecklistTask(title = "Fuel check")
        )
        val merged = mergeShipmentChecklist(planned, prep)
        assertEquals(listOf("  Docs ready  ", "Fuel check"), merged.map { it.title })
    }

    @Test
    fun dedupesDuplicatePrepTitles() {
        val prep = listOf(
            ChecklistTask(title = "Gloves"),
            ChecklistTask(title = "gloves"),
            ChecklistTask(title = " Gloves ")
        )
        val merged = mergeShipmentChecklist(emptyList(), prep)
        assertEquals(listOf("Gloves"), merged.map { it.title })
    }

    @Test
    fun skipsCompletedPrepIsCallerResponsibility() {
        // merge only receives incomplete prep; empty prep → planned unchanged
        val planned = listOf(ChecklistTask(title = "A"))
        assertEquals(planned, mergeShipmentChecklist(planned, emptyList()))
    }
}
