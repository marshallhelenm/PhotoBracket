package io.github.marshallhelenm.photobracket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BracketTest {

    @Test
    fun pairIsTwoDistinctPhotos() {
        val bracket = Bracket(listOf(1L, 2L, 3L), random = Random(0))
        repeat(50) {
            val (a, b) = bracket.current!!
            assertNotEquals(a, b)
            bracket.keepBoth()
        }
    }

    @Test
    fun keepSetsAsideTheLoser() {
        val bracket = Bracket(listOf(1L, 2L, 3L), random = Random(0))
        val (winner, loser) = bracket.current!!
        bracket.keep(winner)
        assertEquals(setOf(loser), bracket.trashed)
        assertEquals(setOf(winner), bracket.kept)
        assertEquals(setOf(1L, 2L, 3L) - loser, bracket.current!!.toList().toSet())
    }

    @Test
    fun keepNeitherSetsAsideBoth() {
        val bracket = Bracket(listOf(1L, 2L, 3L), random = Random(0))
        val (a, b) = bracket.current!!
        bracket.keepNeither()
        assertEquals(setOf(a, b), bracket.trashed)
        assertNull(bracket.current)
    }

    @Test
    fun restoreBringsPhotosBack() {
        val bracket = Bracket(listOf(1L, 2L), random = Random(0))
        bracket.keepNeither()
        bracket.restoreAll()
        assertTrue(bracket.trashed.isEmpty())
        assertNotNull(bracket.current)
    }

    @Test
    fun savedPendingIdsNoLongerInLibraryAreDropped() {
        val bracket = Bracket(listOf(1L, 2L, 3L), trashed = listOf(3L, 99L))
        assertEquals(setOf(3L), bracket.trashed)
        assertEquals(setOf(1L, 2L), bracket.current!!.toList().toSet())
    }

    @Test
    fun keptPhotosLeaveThePoolWhenNotIncluded() {
        val bracket = Bracket(listOf(1L, 2L, 3L, 4L), includeKept = false, random = Random(0))
        val (a, b) = bracket.current!!
        bracket.keepBoth()
        assertEquals(setOf(a, b), bracket.kept)
        assertEquals(setOf(1L, 2L, 3L, 4L) - a - b, bracket.current!!.toList().toSet())
    }

    @Test
    fun keptPhotosHiddenWhenRebuiltWithoutIncludeKept() {
        val bracket = Bracket(listOf(1L, 2L, 3L), kept = listOf(3L), includeKept = false)
        assertEquals(setOf(1L, 2L), bracket.current!!.toList().toSet())
    }

    @Test
    fun restoreSinglePhoto() {
        val bracket = Bracket(listOf(1L, 2L, 3L), trashed = listOf(2L, 3L))
        bracket.restore(2L)
        assertEquals(setOf(3L), bracket.trashed)
        assertEquals(setOf(1L, 2L), bracket.current!!.toList().toSet())
    }

    @Test
    fun forgetDropsDeletedPhotosEverywhere() {
        val bracket = Bracket(listOf(1L, 2L, 3L), trashed = listOf(3L), kept = listOf(1L))
        bracket.forget(listOf(1L, 3L))
        assertTrue(bracket.trashed.isEmpty())
        assertTrue(bracket.kept.isEmpty())
        assertNull(bracket.current)
    }

    @Test
    fun excludedPhotosNeverComeUp() {
        val bracket = Bracket(listOf(1L, 2L, 3L, 4L), excluded = listOf(3L, 4L), random = Random(0))
        repeat(20) {
            assertEquals(setOf(1L, 2L), bracket.current!!.toList().toSet())
            bracket.keepBoth()
        }
    }

    @Test
    fun excludedPhotosKeepTheirTrashAndQueue() {
        val bracket = Bracket(
            listOf(1L, 2L, 3L, 4L),
            trashed = listOf(3L),
            sortQueue = mapOf(4L to "cats"),
            excluded = listOf(3L, 4L),
        )
        assertEquals(setOf(3L), bracket.trashed)
        assertEquals(mapOf(4L to "cats"), bracket.sortQueue)
    }

    @Test
    fun restoringAnExcludedPhotoDoesNotPutItInThePool() {
        val bracket = Bracket(listOf(1L, 2L, 3L), trashed = listOf(3L), excluded = listOf(3L), random = Random(0))
        bracket.restore(3L)
        assertTrue(bracket.trashed.isEmpty())
        repeat(20) {
            assertEquals(setOf(1L, 2L), bracket.current!!.toList().toSet())
            bracket.keepBoth()
        }
    }
}
