package com.gmaingret.notes.data.api

import com.gmaingret.notes.data.model.PatchBulletRequest
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests that verify PatchBulletRequest serialization behavior with Gson.
 *
 * Root cause of the "text not saving" bug: GsonBuilder().serializeNulls() causes
 * PatchBulletRequest to send null for unset fields (e.g., isComplete: null),
 * which the server's Zod schema rejects as invalid (expects boolean, not null).
 *
 * These tests verify that the serialized JSON contains the intended field and
 * does NOT send values that would cause server-side validation failures.
 */
class PatchBulletSerializationTest {

    // Use the same Gson config as the app (NetworkModule line 59)
    private val gson = GsonBuilder().serializeNulls().create()

    @Test
    fun `updateContent serializes content field`() {
        val request = PatchBulletRequest.updateContent("Hello World")
        val json = gson.toJson(request)
        assertTrue("JSON should contain content field", json.contains("\"content\":\"Hello World\""))
    }

    @Test
    fun `updateContent with serializeNulls includes null fields`() {
        // This test documents the behavior that caused the bug.
        // With serializeNulls(), null fields are included in the JSON.
        // The server must accept null for optional boolean/string fields.
        val request = PatchBulletRequest.updateContent("test")
        val json = gson.toJson(request)
        assertTrue("serializeNulls sends isComplete:null", json.contains("\"isComplete\":null"))
        assertTrue("serializeNulls sends isCollapsed:null", json.contains("\"isCollapsed\":null"))
        assertTrue("serializeNulls sends note:null", json.contains("\"note\":null"))
    }

    @Test
    fun `updateIsComplete serializes isComplete field`() {
        val request = PatchBulletRequest.updateIsComplete(true)
        val json = gson.toJson(request)
        assertTrue("JSON should contain isComplete:true", json.contains("\"isComplete\":true"))
    }

    @Test
    fun `updateIsCollapsed serializes isCollapsed field`() {
        val request = PatchBulletRequest.updateIsCollapsed(true)
        val json = gson.toJson(request)
        assertTrue("JSON should contain isCollapsed:true", json.contains("\"isCollapsed\":true"))
    }

    @Test
    fun `updateNote serializes note field`() {
        val request = PatchBulletRequest.updateNote("a note")
        val json = gson.toJson(request)
        assertTrue("JSON should contain note field", json.contains("\"note\":\"a note\""))
    }

    @Test
    fun `factory methods set only one field non-null`() {
        val contentReq = PatchBulletRequest.updateContent("test")
        assertEquals("test", contentReq.content)
        assertEquals(null, contentReq.isComplete)
        assertEquals(null, contentReq.isCollapsed)
        assertEquals(null, contentReq.note)

        val completeReq = PatchBulletRequest.updateIsComplete(true)
        assertEquals(null, completeReq.content)
        assertEquals(true, completeReq.isComplete)
        assertEquals(null, completeReq.isCollapsed)
        assertEquals(null, completeReq.note)

        val collapseReq = PatchBulletRequest.updateIsCollapsed(false)
        assertEquals(null, collapseReq.content)
        assertEquals(null, collapseReq.isComplete)
        assertEquals(false, collapseReq.isCollapsed)
        assertEquals(null, collapseReq.note)

        val noteReq = PatchBulletRequest.updateNote("note")
        assertEquals(null, noteReq.content)
        assertEquals(null, noteReq.isComplete)
        assertEquals(null, noteReq.isCollapsed)
        assertEquals("note", noteReq.note)
    }
}
