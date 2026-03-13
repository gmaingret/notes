package com.gmaingret.notes.data.api

import com.google.gson.GsonBuilder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.util.concurrent.TimeUnit

/**
 * Integration test that verifies the server correctly handles PATCH requests
 * from the Android client, including the serializeNulls format (null fields
 * alongside the intended update field).
 *
 * These tests hit the real server at http://192.168.1.50:8000.
 * They are skipped if the server is unreachable (CI environments).
 *
 * This test class was created to verify the fix for the "text not saving" bug,
 * where Gson's serializeNulls() caused the server's Zod schema to reject
 * PATCH requests with null boolean fields.
 */
class BulletApiIntegrationTest {

    private val baseUrl = "http://192.168.1.50:8000"
    private val jsonType = "application/json".toMediaType()
    private val gson = GsonBuilder().serializeNulls().create()

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var accessToken: String? = null
    private var documentId: String? = null

    @Before
    fun setUp() {
        // Skip if server is unreachable
        val reachable = try {
            val req = Request.Builder().url("$baseUrl/api/auth/login").build()
            // Just check connectivity, we expect 405 or similar for GET on POST endpoint
            client.newCall(req).execute().use { true }
        } catch (e: Exception) {
            false
        }
        assumeTrue("Server at $baseUrl is not reachable — skipping integration test", reachable)

        // Register/login
        val email = "integration-test-${System.currentTimeMillis()}@notes.app"
        val registerBody = """{"email":"$email","password":"TestPass123!"}""".toRequestBody(jsonType)
        val registerReq = Request.Builder()
            .url("$baseUrl/api/auth/register")
            .post(registerBody)
            .build()
        val registerResp = client.newCall(registerReq).execute()
        val registerBodyStr = registerResp.body?.string() ?: ""
        assumeTrue(
            "Registration failed (status=${registerResp.code}): $registerBodyStr",
            registerResp.isSuccessful
        )
        val registerJson = gson.fromJson(registerBodyStr, Map::class.java)
        accessToken = registerJson["accessToken"] as String
        assertNotNull("Should have access token", accessToken)

        // Create test document
        val docBody = """{"title":"Integration Test Doc"}""".toRequestBody(jsonType)
        val docReq = Request.Builder()
            .url("$baseUrl/api/documents")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(docBody)
            .build()
        val docResp = client.newCall(docReq).execute()
        val docJson = gson.fromJson(docResp.body?.string(), Map::class.java)
        documentId = docJson["id"] as String
        assertNotNull("Should have document ID", documentId)
    }

    private fun createBullet(content: String = ""): String {
        val body = """{"documentId":"$documentId","content":"$content"}""".toRequestBody(jsonType)
        val req = Request.Builder()
            .url("$baseUrl/api/bullets")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(body)
            .build()
        val resp = client.newCall(req).execute()
        assertEquals("Create bullet should return 201", 201, resp.code)
        val json = gson.fromJson(resp.body?.string(), Map::class.java)
        return json["id"] as String
    }

    private fun getBullets(): List<Map<String, Any>> {
        val req = Request.Builder()
            .url("$baseUrl/api/bullets/documents/$documentId/bullets")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        val resp = client.newCall(req).execute()
        assertEquals("GET bullets should return 200", 200, resp.code)
        @Suppress("UNCHECKED_CAST")
        return gson.fromJson(resp.body?.string(), List::class.java) as List<Map<String, Any>>
    }

    @Test
    fun `PATCH content with serializeNulls format succeeds and persists`() {
        val bulletId = createBullet()

        // PATCH using the exact format Gson serializeNulls produces
        val patchBody = """{"content":"Hello World","isComplete":null,"isCollapsed":null,"note":null}"""
            .toRequestBody(jsonType)
        val patchReq = Request.Builder()
            .url("$baseUrl/api/bullets/$bulletId")
            .addHeader("Authorization", "Bearer $accessToken")
            .patch(patchBody)
            .build()
        val patchResp = client.newCall(patchReq).execute()
        assertEquals("PATCH should return 200", 200, patchResp.code)

        val patchJson = gson.fromJson(patchResp.body?.string(), Map::class.java)
        assertEquals("PATCH response should have updated content", "Hello World", patchJson["content"])

        // Verify content persists on GET
        val bullets = getBullets()
        val bullet = bullets.first { it["id"] == bulletId }
        assertEquals("Content should persist after GET", "Hello World", bullet["content"])
    }

    @Test
    fun `PATCH isComplete with serializeNulls format succeeds`() {
        val bulletId = createBullet("test bullet")

        val patchBody = """{"content":null,"isComplete":true,"isCollapsed":null,"note":null}"""
            .toRequestBody(jsonType)
        val patchReq = Request.Builder()
            .url("$baseUrl/api/bullets/$bulletId")
            .addHeader("Authorization", "Bearer $accessToken")
            .patch(patchBody)
            .build()
        val patchResp = client.newCall(patchReq).execute()
        assertEquals("PATCH isComplete should return 200", 200, patchResp.code)

        val patchJson = gson.fromJson(patchResp.body?.string(), Map::class.java)
        assertEquals("isComplete should be true", true, patchJson["isComplete"])
        // Content should NOT have been wiped by the null content field
        assertEquals("Content should be preserved", "test bullet", patchJson["content"])
    }

    @Test
    fun `PATCH isCollapsed with serializeNulls format succeeds`() {
        val bulletId = createBullet("collapsible")

        val patchBody = """{"content":null,"isComplete":null,"isCollapsed":true,"note":null}"""
            .toRequestBody(jsonType)
        val patchReq = Request.Builder()
            .url("$baseUrl/api/bullets/$bulletId")
            .addHeader("Authorization", "Bearer $accessToken")
            .patch(patchBody)
            .build()
        val patchResp = client.newCall(patchReq).execute()
        assertEquals("PATCH isCollapsed should return 200", 200, patchResp.code)

        val patchJson = gson.fromJson(patchResp.body?.string(), Map::class.java)
        assertEquals("isCollapsed should be true", true, patchJson["isCollapsed"])
    }

    @Test
    fun `full round-trip create then PATCH content then GET preserves content`() {
        // This is the exact flow that was broken:
        // 1. Create bullet with empty content
        // 2. PATCH content (simulating user typing)
        // 3. GET bullets (simulating app reopen)
        // 4. Verify content is present

        val bulletId = createBullet("")

        // Step 2: PATCH with the exact format the Android app sends
        val patchBody = """{"content":"User typed this text","isComplete":null,"isCollapsed":null,"note":null}"""
            .toRequestBody(jsonType)
        val patchReq = Request.Builder()
            .url("$baseUrl/api/bullets/$bulletId")
            .addHeader("Authorization", "Bearer $accessToken")
            .patch(patchBody)
            .build()
        val patchResp = client.newCall(patchReq).execute()
        assertEquals("PATCH should succeed", 200, patchResp.code)

        // Step 3: GET bullets (like app reopening)
        val bullets = getBullets()
        val bullet = bullets.first { it["id"] == bulletId }

        // Step 4: Content should be there
        assertEquals("Content must survive the round-trip", "User typed this text", bullet["content"])
    }

    @Test
    fun `multiple content PATCHes keep the last value`() {
        val bulletId = createBullet("")

        // Simulate rapid typing with multiple PATCHes
        listOf("H", "He", "Hel", "Hell", "Hello").forEach { text ->
            val body = """{"content":"$text","isComplete":null,"isCollapsed":null,"note":null}"""
                .toRequestBody(jsonType)
            val req = Request.Builder()
                .url("$baseUrl/api/bullets/$bulletId")
                .addHeader("Authorization", "Bearer $accessToken")
                .patch(body)
                .build()
            val resp = client.newCall(req).execute()
            assertEquals("PATCH should succeed for '$text'", 200, resp.code)
            resp.body?.close()
        }

        // Final state should be "Hello"
        val bullets = getBullets()
        val bullet = bullets.first { it["id"] == bulletId }
        assertEquals("Hello", bullet["content"])
    }
}
