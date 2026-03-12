package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.model.Bullet
import com.gmaingret.notes.domain.model.FlatBullet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [FlattenTreeUseCase].
 *
 * Pure Kotlin — no Android dependencies. Tests cover:
 * - Empty input
 * - Flat list (no nesting)
 * - Sibling ordering by position
 * - Nested bullets (depth assignment)
 * - Deep nesting (grandchild at depth 2)
 * - Collapsed parent hides descendants
 * - Zoom mode (rootId filters to subtree at depth 0)
 * - maxDisplayDepth caps visual depth
 */
class FlattenTreeUseCaseTest {

    private lateinit var useCase: FlattenTreeUseCase

    private fun bullet(
        id: String,
        parentId: String? = null,
        position: Double = 1.0,
        isCollapsed: Boolean = false,
        content: String = id
    ) = Bullet(
        id = id,
        documentId = "doc1",
        parentId = parentId,
        content = content,
        position = position,
        isComplete = false,
        isCollapsed = isCollapsed,
        note = null
    )

    @Before
    fun setUp() {
        useCase = FlattenTreeUseCase()
    }

    @Test
    fun `empty input returns empty list`() {
        val result = useCase(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `flat list with no nesting returns root bullets at depth 0`() {
        val bullets = listOf(
            bullet("A", parentId = null, position = 1.0),
            bullet("B", parentId = null, position = 2.0),
            bullet("C", parentId = null, position = 3.0)
        )
        val result = useCase(bullets)
        assertEquals(3, result.size)
        assertEquals("A", result[0].bullet.id)
        assertEquals("B", result[1].bullet.id)
        assertEquals("C", result[2].bullet.id)
        result.forEach { assertEquals(0, it.depth) }
    }

    @Test
    fun `siblings ordered by position ascending`() {
        val bullets = listOf(
            bullet("C", parentId = null, position = 3.0),
            bullet("A", parentId = null, position = 1.0),
            bullet("B", parentId = null, position = 2.0)
        )
        val result = useCase(bullets)
        assertEquals(listOf("A", "B", "C"), result.map { it.bullet.id })
    }

    @Test
    fun `nested bullet with parent and two children`() {
        val bullets = listOf(
            bullet("parent", parentId = null, position = 1.0),
            bullet("child1", parentId = "parent", position = 1.0),
            bullet("child2", parentId = "parent", position = 2.0)
        )
        val result = useCase(bullets)
        assertEquals(3, result.size)
        assertEquals("parent", result[0].bullet.id)
        assertEquals(0, result[0].depth)
        assertEquals("child1", result[1].bullet.id)
        assertEquals(1, result[1].depth)
        assertEquals("child2", result[2].bullet.id)
        assertEquals(1, result[2].depth)
    }

    @Test
    fun `deep nesting grandchild at depth 2`() {
        val bullets = listOf(
            bullet("A", parentId = null, position = 1.0),
            bullet("B", parentId = "A", position = 1.0),
            bullet("C", parentId = "B", position = 1.0)
        )
        val result = useCase(bullets)
        assertEquals(3, result.size)
        assertEquals("A", result[0].bullet.id)
        assertEquals(0, result[0].depth)
        assertEquals("B", result[1].bullet.id)
        assertEquals(1, result[1].depth)
        assertEquals("C", result[2].bullet.id)
        assertEquals(2, result[2].depth)
    }

    @Test
    fun `collapsed parent hides all descendants`() {
        val bullets = listOf(
            bullet("parent", parentId = null, position = 1.0, isCollapsed = true),
            bullet("child", parentId = "parent", position = 1.0),
            bullet("grandchild", parentId = "child", position = 1.0)
        )
        val result = useCase(bullets)
        assertEquals(1, result.size)
        assertEquals("parent", result[0].bullet.id)
    }

    @Test
    fun `non-collapsed parent shows all descendants`() {
        val bullets = listOf(
            bullet("parent", parentId = null, position = 1.0, isCollapsed = false),
            bullet("child", parentId = "parent", position = 1.0),
            bullet("grandchild", parentId = "child", position = 1.0)
        )
        val result = useCase(bullets)
        assertEquals(3, result.size)
    }

    @Test
    fun `zoom mode rootId returns only children of that bullet at depth 0`() {
        val bullets = listOf(
            bullet("root", parentId = null, position = 1.0),
            bullet("zoom", parentId = null, position = 2.0),
            bullet("child1", parentId = "zoom", position = 1.0),
            bullet("child2", parentId = "zoom", position = 2.0),
            bullet("grandchild", parentId = "child1", position = 1.0)
        )
        val result = useCase(bullets, rootId = "zoom")
        // Should only show children of "zoom", starting at depth 0
        assertEquals(3, result.size)
        assertEquals("child1", result[0].bullet.id)
        assertEquals(0, result[0].depth)
        assertEquals("grandchild", result[1].bullet.id)
        assertEquals(1, result[1].depth)
        assertEquals("child2", result[2].bullet.id)
        assertEquals(0, result[2].depth)
    }

    @Test
    fun `maxDisplayDepth caps visual depth but bullets still appear`() {
        val bullets = listOf(
            bullet("A", parentId = null, position = 1.0),
            bullet("B", parentId = "A", position = 1.0),
            bullet("C", parentId = "B", position = 1.0),
            bullet("D", parentId = "C", position = 1.0)
        )
        // Cap depth at 2 — bullet D is at actual depth 3 but should appear at depth 2
        val result = useCase(bullets, maxDisplayDepth = 2)
        assertEquals(4, result.size)
        assertEquals("A", result[0].bullet.id)
        assertEquals(0, result[0].depth)
        assertEquals("B", result[1].bullet.id)
        assertEquals(1, result[1].depth)
        assertEquals("C", result[2].bullet.id)
        assertEquals(2, result[2].depth)
        assertEquals("D", result[3].bullet.id)
        assertEquals(2, result[3].depth) // capped at maxDisplayDepth
    }

    @Test
    fun `hasChildren is true only for bullets with children`() {
        val bullets = listOf(
            bullet("parent", parentId = null, position = 1.0),
            bullet("child", parentId = "parent", position = 1.0),
            bullet("leaf", parentId = null, position = 2.0)
        )
        val result = useCase(bullets)
        val parentFlat = result.first { it.bullet.id == "parent" }
        val childFlat = result.first { it.bullet.id == "child" }
        val leafFlat = result.first { it.bullet.id == "leaf" }
        assertTrue(parentFlat.hasChildren)
        assertTrue(!childFlat.hasChildren)
        assertTrue(!leafFlat.hasChildren)
    }

    @Test
    fun `multiple root siblings with nested children in correct order`() {
        // Tree:
        //   A (pos=1)
        //     A1 (pos=1)
        //     A2 (pos=2)
        //   B (pos=2)
        //     B1 (pos=1)
        val bullets = listOf(
            bullet("A", parentId = null, position = 1.0),
            bullet("A2", parentId = "A", position = 2.0),
            bullet("A1", parentId = "A", position = 1.0),
            bullet("B", parentId = null, position = 2.0),
            bullet("B1", parentId = "B", position = 1.0)
        )
        val result = useCase(bullets)
        assertEquals(5, result.size)
        assertEquals(listOf("A", "A1", "A2", "B", "B1"), result.map { it.bullet.id })
        assertEquals(listOf(0, 1, 1, 0, 1), result.map { it.depth })
    }
}
