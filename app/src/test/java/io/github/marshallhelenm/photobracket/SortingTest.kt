package io.github.marshallhelenm.photobracket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SortingTest {

    private fun bracket(
        ids: List<Long> = listOf(1L, 2L, 3L, 4L),
        trashed: List<Long> = emptyList(),
        sortQueue: Map<Long, String> = emptyMap(),
    ) = Bracket(ids, trashed = trashed, sortQueue = sortQueue, random = Random(0))

    @Test
    fun tagOnKeptWinnerIsQueued() {
        val bracket = bracket()
        val (winner, loser) = bracket.current!!
        bracket.tag(winner, "cats")
        bracket.tag(loser, "dogs")
        bracket.keep(winner)
        assertEquals(mapOf(winner to "cats"), bracket.sortQueue)
    }

    @Test
    fun keepBothQueuesBothTags() {
        val bracket = bracket()
        val (a, b) = bracket.current!!
        bracket.tag(a, "cats")
        bracket.tag(b, "dogs")
        bracket.keepBoth()
        assertEquals(mapOf(a to "cats", b to "dogs"), bracket.sortQueue)
    }

    @Test
    fun keepNeitherQueuesNothing() {
        val bracket = bracket()
        val (a, b) = bracket.current!!
        bracket.tag(a, "cats")
        bracket.tag(b, "dogs")
        bracket.keepNeither()
        assertTrue(bracket.sortQueue.isEmpty())
    }

    @Test
    fun tagsDoNotCarryOverToTheNextPair() {
        val bracket = bracket()
        val (a, _) = bracket.current!!
        bracket.tag(a, "cats")
        bracket.keepBoth()
        bracket.current!!.toList().forEach { id ->
            // Only a queued folder (from the keep above) may show; never a leftover pending tag.
            assertEquals(bracket.sortQueue[id], bracket.folderFor(id))
        }
    }

    @Test
    fun trashingAQueuedPhotoUnqueuesIt() {
        val bracket = bracket(ids = listOf(1L, 2L), sortQueue = mapOf(1L to "cats", 2L to "dogs"))
        bracket.keep(1L)
        assertEquals(mapOf(1L to "cats"), bracket.sortQueue)
    }

    @Test
    fun clearingTheTagOfAQueuedPhotoUnqueuesItWhenKept() {
        val bracket = bracket(ids = listOf(1L, 2L), sortQueue = mapOf(1L to "cats"))
        assertEquals("cats", bracket.folderFor(1L))
        bracket.tag(1L, null)
        assertNull(bracket.folderFor(1L))
        bracket.keepBoth()
        assertTrue(bracket.sortQueue.isEmpty())
    }

    @Test
    fun queuedPhotosMissingFromLibraryOrTrashedAreDropped() {
        val bracket = bracket(ids = listOf(1L, 2L, 3L), trashed = listOf(3L), sortQueue = mapOf(2L to "a", 3L to "b", 99L to "c"))
        assertEquals(mapOf(2L to "a"), bracket.sortQueue)
    }

    @Test
    fun onSortedAndForgetFolderEmptyTheQueue() {
        val bracket = bracket(sortQueue = mapOf(1L to "cats", 2L to "dogs", 3L to "cats"))
        bracket.onSorted(listOf(2L))
        assertEquals(mapOf(1L to "cats", 3L to "cats"), bracket.sortQueue)
        bracket.forgetFolder("cats")
        assertTrue(bracket.sortQueue.isEmpty())
    }

    @Test
    fun folderNameRules() {
        val existing = listOf(Folder("1", "Cats", "🐱", starred = true))
        assertNull(folderNameProblem("Dogs", existing))
        assertNull(folderNameProblem("  Dogs  ", existing))
        assertEquals(FolderNameProblem.Blank, folderNameProblem("   ", existing))
        assertEquals(FolderNameProblem.Slash, folderNameProblem("a/b", existing))
        assertEquals(FolderNameProblem.LeadingDot, folderNameProblem(".hidden", existing))
        assertEquals(FolderNameProblem.TooLong, folderNameProblem("x".repeat(41), existing))
        assertEquals(FolderNameProblem.Duplicate, folderNameProblem("cats", existing))
    }
}
