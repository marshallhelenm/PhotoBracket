package io.github.marshallhelenm.photobracket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UndoTest {

    private fun bracket(ids: List<Long> = listOf(1L, 2L, 3L, 4L), sortQueue: Map<Long, String> = emptyMap()) =
        Bracket(ids, sortQueue = sortQueue, random = Random(0))

    @Test
    fun nothingToUndoAtFirst() {
        val bracket = bracket()
        assertFalse(bracket.canUndo)
        val pair = bracket.current
        bracket.undo()
        assertEquals(pair, bracket.current)
    }

    @Test
    fun undoKeepBringsThePairBackAndRestoresTheLoser() {
        val bracket = bracket()
        val pair = bracket.current!!
        bracket.keep(pair.first)
        bracket.undo()
        assertEquals(pair, bracket.current)
        assertTrue(bracket.trashed.isEmpty())
        assertTrue(bracket.kept.isEmpty())
        assertFalse(bracket.canUndo)
    }

    @Test
    fun undoKeepNeitherEmptiesTheTrashAndPoolCanPairThemAgain() {
        val bracket = bracket(ids = listOf(1L, 2L))
        bracket.keepNeither()
        assertNull(bracket.current)
        bracket.undo()
        assertEquals(setOf(1L, 2L), bracket.current!!.toList().toSet())
        assertTrue(bracket.trashed.isEmpty())
        // Both are back in the pool, so deciding again works.
        bracket.keepBoth()
        assertEquals(setOf(1L, 2L), bracket.current!!.toList().toSet())
    }

    @Test
    fun undoRestoresTagsAndQueue() {
        val bracket = bracket(sortQueue = mapOf(1L to "dogs"))
        val (a, b) = bracket.current!!
        bracket.tag(a, "cats")
        bracket.tag(b, null)
        val queueBefore = bracket.sortQueue
        bracket.keepBoth()
        bracket.undo()
        assertEquals(queueBefore, bracket.sortQueue)
        assertEquals("cats", bracket.folderFor(a))
        assertNull(bracket.folderFor(b))
    }

    @Test
    fun undoGoesBackSeveralSteps() {
        val bracket = bracket()
        val first = bracket.current!!
        bracket.keepBoth()
        val second = bracket.current!!
        bracket.keep(second.first)
        bracket.undo()
        assertEquals(second, bracket.current)
        bracket.undo()
        assertEquals(first, bracket.current)
        assertTrue(bracket.trashed.isEmpty())
        assertTrue(bracket.kept.isEmpty())
    }

    @Test
    fun keptBeforeStaysKeptAfterUndo() {
        val bracket = Bracket(listOf(1L, 2L, 3L), kept = listOf(1L, 2L, 3L), random = Random(0))
        val (a, _) = bracket.current!!
        bracket.keep(a)
        bracket.undo()
        assertEquals(setOf(1L, 2L, 3L), bracket.kept)
    }

    @Test
    fun deletedPhotosCannotBeUndone() {
        val bracket = bracket()
        val (a, b) = bracket.current!!
        bracket.keep(a)
        bracket.forget(listOf(b))
        assertFalse(bracket.canUndo)
    }

    @Test
    fun undoAfterManualRestoreDoesNotDuplicateThePhoto() {
        val bracket = bracket(ids = listOf(1L, 2L))
        val (a, b) = bracket.current!!
        bracket.keep(a)
        bracket.restore(b)
        bracket.undo()
        // With exactly two photos in the pool, every pair must be those two.
        repeat(10) {
            assertEquals(setOf(1L, 2L), bracket.current!!.toList().toSet())
            bracket.keepBoth()
        }
    }

    @Test
    fun deletingAFolderScrubsItFromUndoHistory() {
        val bracket = bracket()
        val (a, _) = bracket.current!!
        bracket.tag(a, "cats")
        bracket.keepBoth()
        bracket.forgetFolder("cats")
        bracket.undo()
        assertNull(bracket.folderFor(a))
    }
}
