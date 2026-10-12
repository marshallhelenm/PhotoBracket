package io.github.marshallhelenm.photobracket

import kotlin.random.Random

/**
 * Pairs up photos from [ids] and tracks the user's decisions about them.
 *
 * Photos the user passes on go to [trashed] until they're deleted for real (see [forget]) or
 * restored. Photos the user keeps are remembered in [kept]; unless [includeKept] is true, they're
 * taken out of the pool and won't be shown again.
 *
 * Photos in the current pair can be tagged with a folder ID. A tag only counts once the photo is
 * kept: it then joins [sortQueue] until the photo is actually moved (see [onSorted]).
 *
 * Photos in [excluded] (from folders the user left out) never come up in matchups, but any trash,
 * kept, or sort decisions already made about them still stand.
 */
class Bracket(
    ids: Collection<Long>,
    trashed: Collection<Long> = emptyList(),
    kept: Collection<Long> = emptyList(),
    sortQueue: Map<Long, String> = emptyMap(),
    excluded: Collection<Long> = emptyList(),
    private val includeKept: Boolean = true,
    private val random: Random = Random.Default,
) {
    private val library = ids.toSet()
    private val trash = trashed.filterTo(mutableSetOf()) { it in library }
    private val keptSet = kept.filterTo(mutableSetOf()) { it in library && it !in trash }
    private val excludedSet = excluded.toSet()
    private val pool = ids
        .filterNot { it in trash || it in excludedSet || (!includeKept && it in keptSet) }
        .toMutableList()
    private val queue = sortQueue.filterKeys { it in library && it !in trash }.toMutableMap()

    // Tags on the current pair, applied when they're kept. A null value clears a queued folder.
    private val pendingTags = mutableMapOf<Long, String?>()

    val trashed: Set<Long> get() = trash
    val kept: Set<Long> get() = keptSet
    val sortQueue: Map<Long, String> get() = queue

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
        commitTag(winner)
        advance()
    }

    fun keepBoth() {
        val (first, second) = current ?: return
        markKept(first)
        markKept(second)
        commitTag(first)
        commitTag(second)
        advance()
    }

    fun keepNeither() {
        val (first, second) = current ?: return
        discard(first)
        discard(second)
        advance()
    }

    /** The folder [id] will be sorted into if kept: its tag in this pair, or what's already queued. */
    fun folderFor(id: Long): String? = if (id in pendingTags) pendingTags[id] else queue[id]

    /** Tags a photo in the current pair with a folder, or clears it with null. */
    fun tag(id: Long, folderId: String?) {
        val (first, second) = current ?: return
        require(id == first || id == second) { "$id is not in the current pair" }
        pendingTags[id] = folderId
    }

    /** Call once photos have been moved into their folders. */
    fun onSorted(ids: Collection<Long>) {
        queue.keys.removeAll(ids.toSet())
    }

    fun unqueue(id: Long) {
        queue.remove(id)
    }

    /** Drops every tag and queued sort pointing at a folder that's been deleted. */
    fun forgetFolder(folderId: String) {
        queue.values.removeAll { it == folderId }
        pendingTags.entries.removeAll { it.value == folderId }
    }

    fun restore(id: Long) {
        if (!trash.remove(id)) return
        if (id !in excludedSet) pool.add(id)
        if (current == null) advance()
    }

    fun restoreAll() {
        trash.toList().forEach(::restore)
    }

    /** Drops photos that no longer exist, e.g. because they were just deleted. */
    fun forget(ids: Collection<Long>) {
        trash.removeAll(ids.toSet())
        keptSet.removeAll(ids.toSet())
        queue.keys.removeAll(ids.toSet())
        pool.removeAll(ids.toSet())
        val pair = current
        if (pair == null || pair.first in ids || pair.second in ids) advance()
    }

    private fun discard(id: Long) {
        pool.remove(id)
        trash.add(id)
        // A trashed photo never gets sorted, even if it was queued from an earlier matchup.
        queue.remove(id)
    }

    private fun commitTag(id: Long) {
        if (id !in pendingTags) return
        when (val folderId = pendingTags[id]) {
            null -> queue.remove(id)
            else -> queue[id] = folderId
        }
    }

    private fun markKept(id: Long) {
        keptSet.add(id)
        if (!includeKept) pool.remove(id)
    }

    private fun advance() {
        pendingTags.clear()
        if (pool.size < 2) {
            current = null
            return
        }
        val i = random.nextInt(pool.size)
        val j = (i + 1 + random.nextInt(pool.size - 1)) % pool.size
        current = pool[i] to pool[j]
    }
}
