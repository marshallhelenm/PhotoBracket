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
 *
 * Each decision is recorded so [undo] can bring its pair back exactly as it was.
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

    // A decision only ever changes the two photos in its pair, so that's all undo needs to remember.
    private data class PhotoBefore(val id: Long, val kept: Boolean, val queuedFolder: String?)
    private data class Decision(val pair: Pair<Long, Long>, val tags: Map<Long, String?>, val before: List<PhotoBefore>)

    private val history = ArrayDeque<Decision>()

    val canUndo: Boolean get() = history.isNotEmpty()

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
        record()
        discard(if (winner == first) second else first)
        markKept(winner)
        commitTag(winner)
        advance()
    }

    fun keepBoth() {
        val (first, second) = current ?: return
        record()
        markKept(first)
        markKept(second)
        commitTag(first)
        commitTag(second)
        advance()
    }

    fun keepNeither() {
        val (first, second) = current ?: return
        record()
        discard(first)
        discard(second)
        advance()
    }

    /** Brings back the previous pair, undoing everything its decision did. */
    fun undo() {
        val decision = history.removeLastOrNull() ?: return
        decision.before.forEach { photo ->
            trash.remove(photo.id)
            if (photo.kept) keptSet.add(photo.id) else keptSet.remove(photo.id)
            if (photo.queuedFolder != null) queue[photo.id] = photo.queuedFolder else queue.remove(photo.id)
            if (photo.id !in pool) pool.add(photo.id)
        }
        pendingTags.clear()
        pendingTags.putAll(decision.tags)
        current = decision.pair
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
        val scrubbed = history.map { decision ->
            decision.copy(
                tags = decision.tags.filterValues { it != folderId },
                before = decision.before.map { if (it.queuedFolder == folderId) it.copy(queuedFolder = null) else it },
            )
        }
        history.clear()
        history.addAll(scrubbed)
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
        // Deleted photos can't come back, so neither can decisions about them.
        history.removeAll { it.pair.first in ids || it.pair.second in ids }
        val pair = current
        if (pair == null || pair.first in ids || pair.second in ids) advance()
    }

    private fun record() {
        val pair = current ?: return
        history.addLast(
            Decision(
                pair = pair,
                tags = pendingTags.toMap(),
                before = pair.toList().map { PhotoBefore(it, kept = it in keptSet, queuedFolder = queue[it]) },
            ),
        )
        if (history.size > MAX_UNDO) history.removeFirst()
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

private const val MAX_UNDO = 50
