package io.github.marshallhelenm.photobracket

import kotlin.random.Random

/**
 * Pairs up photos from [ids] and tracks the user's decisions about them.
 *
 * Photos the user passes on go to [trashed] until they're deleted for real (see [forget]) or
 * restored. Photos the user keeps are remembered in [kept]; unless [includeKept] is true, they're
 * taken out of the pool and won't be shown again.
 */
class Bracket(
    ids: Collection<Long>,
    trashed: Collection<Long> = emptyList(),
    kept: Collection<Long> = emptyList(),
    private val includeKept: Boolean = true,
    private val random: Random = Random.Default,
) {
    private val library = ids.toSet()
    private val trash = trashed.filterTo(mutableSetOf()) { it in library }
    private val keptSet = kept.filterTo(mutableSetOf()) { it in library && it !in trash }
    private val pool = ids.filterNot { it in trash || (!includeKept && it in keptSet) }.toMutableList()

    val trashed: Set<Long> get() = trash
    val kept: Set<Long> get() = keptSet

    var current: Pair<Long, Long>? = null
        private set

    init {
        advance()
    }

    fun keep(winner: Long) {
        val (first, second) = current ?: return
        require(winner == first || winner == second) { "$winner is not in the current pair" }
        discard(if (winner == first) second else first)
        markKept(winner)
        advance()
    }

    fun keepBoth() {
        val (first, second) = current ?: return
        markKept(first)
        markKept(second)
        advance()
    }

    fun keepNeither() {
        val (first, second) = current ?: return
        discard(first)
        discard(second)
        advance()
    }

    fun restore(id: Long) {
        if (!trash.remove(id)) return
        pool.add(id)
        if (current == null) advance()
    }

    fun restoreAll() {
        trash.toList().forEach(::restore)
    }

    /** Drops photos that no longer exist, e.g. because they were just deleted. */
    fun forget(ids: Collection<Long>) {
        trash.removeAll(ids.toSet())
        keptSet.removeAll(ids.toSet())
        pool.removeAll(ids.toSet())
        val pair = current
        if (pair == null || pair.first in ids || pair.second in ids) advance()
    }

    private fun discard(id: Long) {
        pool.remove(id)
        trash.add(id)
    }

    private fun markKept(id: Long) {
        keptSet.add(id)
        if (!includeKept) pool.remove(id)
    }

    private fun advance() {
        if (pool.size < 2) {
            current = null
            return
        }
        val i = random.nextInt(pool.size)
        val j = (i + 1 + random.nextInt(pool.size - 1)) % pool.size
        current = pool[i] to pool[j]
    }
}
