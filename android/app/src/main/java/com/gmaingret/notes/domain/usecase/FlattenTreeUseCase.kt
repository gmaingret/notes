package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.model.Bullet
import com.gmaingret.notes.domain.model.FlatBullet
import javax.inject.Inject

/**
 * Pure Kotlin recursive DFS tree flattener.
 *
 * Converts a flat list of parent-linked [Bullet] objects into a depth-ordered
 * [FlatBullet] list suitable for rendering in a flat LazyColumn.
 *
 * No Android dependencies — can be instantiated directly in unit tests via FlattenTreeUseCase()
 * without any DI framework involvement (javax.inject.Inject is a pure Java annotation).
 *
 * Features:
 * - DFS traversal with children sorted by position ascending
 * - Respects [Bullet.isCollapsed]: children of collapsed bullets are hidden
 * - Zoom mode via [rootId]: returns only the subtree under that bullet, starting at depth 0
 * - [maxDisplayDepth]: visual depth cap (bullets deeper than cap appear AT cap depth)
 * - [hasChildren] computed from the child map (includes collapsed children)
 */
class FlattenTreeUseCase @Inject constructor() {

    /**
     * @param bullets All bullets for a document (unordered is fine)
     * @param rootId  If non-null, only the subtree rooted at this bullet is returned,
     *                with depths starting at 0 for its direct children
     * @param maxDisplayDepth Visual depth cap (default 7). Bullets deeper than this
     *                        still appear but their [FlatBullet.depth] is capped here
     */
    operator fun invoke(
        bullets: List<Bullet>,
        rootId: String? = null,
        maxDisplayDepth: Int = 7
    ): List<FlatBullet> {
        if (bullets.isEmpty()) return emptyList()

        // Build child map: parentId -> sorted children
        val childMap: Map<String?, List<Bullet>> = bullets
            .groupBy { it.parentId }
            .mapValues { (_, children) -> children.sortedBy { it.position } }

        // Build a set of all bullet IDs that have at least one child
        val bulletsWithChildren: Set<String> = childMap.keys.filterNotNull().toSet()

        val result = mutableListOf<FlatBullet>()

        // DFS traversal starting from rootId (or document root if null)
        fun dfs(parentId: String?, depth: Int) {
            val children = childMap[parentId] ?: return
            for (bullet in children) {
                val displayDepth = minOf(depth, maxDisplayDepth)
                val hasChildren = bullet.id in bulletsWithChildren
                result.add(FlatBullet(bullet = bullet, depth = displayDepth, hasChildren = hasChildren))
                // Only recurse if not collapsed
                if (!bullet.isCollapsed) {
                    dfs(bullet.id, depth + 1)
                }
            }
        }

        dfs(rootId, 0)

        return result
    }
}
