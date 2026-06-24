package nl.codeinfinity.coreassistant

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@RunWith(JUnit4::class)
class GeminiApiServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: GeminiApiService

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        api = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getModels parses response`() = runTest {
        val json = """
        {
            "models": [
                {
                    "name": "models/gemini-2.0-flash",
                    "displayName": "Gemini 2.0 Flash",
                    "description": "Fast and versatile"
                },
                {
                    "name": "models/gemini-2.0-pro",
                    "displayName": "Gemini 2.0 Pro",
                    "description": "Best for complex tasks"
                }
            ]
        }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json)
                .addHeader("Content-Type", "application/json")
        )

        val response = api.getModels("test-key")

        assertEquals(2, response.models.size)
        assertEquals("models/gemini-2.0-flash", response.models[0].name)
        assertEquals("Gemini 2.0 Flash", response.models[0].displayName)
        assertEquals("Fast and versatile", response.models[0].description)
        assertEquals("models/gemini-2.0-pro", response.models[1].name)
        assertEquals("Gemini 2.0 Pro", response.models[1].displayName)
        assertEquals("Best for complex tasks", response.models[1].description)
    }

    @Test
    fun `getModels handles empty list`() = runTest {
        val json = """{"models": []}"""

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json)
                .addHeader("Content-Type", "application/json")
        )

        val response = api.getModels("test-key")

        assertTrue(response.models.isEmpty())
    }

    @Test
    fun `generateContent returns text`() = runTest {
        val json = """
        {
            "candidates": [
                {
                    "content": {
                        "parts": [
                            {"text": "Hello, world!"}
                        ]
                    }
                }
            ]
        }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json)
                .addHeader("Content-Type", "application/json")
        )

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = "hi"))))
        )
        val response = api.generateContent("models/gemini-2.0-flash", "test-key", request)

        assertEquals("Hello, world!", response.text)
    }

    @Test
    fun `generateContent returns thought`() = runTest {
        val json = """
        {
            "candidates": [
                {
                    "content": {
                        "parts": [
                            {"text": "Let me reason step by step...", "thought": true},
                            {"text": "The answer is 42."}
                        ]
                    }
                }
            ]
        }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json)
                .addHeader("Content-Type", "application/json")
        )

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = "test"))))
        )
        val response = api.generateContent("models/gemini-2.0-flash", "test-key", request)

        assertEquals("Let me reason step by step...", response.thought)
        assertEquals("The answer is 42.", response.text)
    }

    @Test
    fun `generateContent returns inline images`() = runTest {
        val json = """
        {
            "candidates": [
                {
                    "content": {
                        "parts": [
                            {
                                "inlineData": {
                                    "mimeType": "image/png",
                                    "data": "iVBORw0KGgoAAAANSUhEUg=="
                                }
                            }
                        ]
                    }
                }
            ]
        }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json)
                .addHeader("Content-Type", "application/json")
        )

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = "generate image"))))
        )
        val response = api.generateContent("models/gemini-2.0-flash", "test-key", request)

        assertNotNull(response.inlineImages)
        assertEquals(1, response.inlineImages?.size)
        assertEquals("image/png", response.inlineImages?.first()?.mimeType)
        assertEquals("iVBORw0KGgoAAAANSUhEUg==", response.inlineImages?.first()?.data)
    }

    @Test
    fun `generateContent returns grounding metadata`() = runTest {
        val json = """
        {
            "candidates": [
                {
                    "groundingMetadata": {
                        "groundingChunks": [
                            {
                                "web": {
                                    "uri": "https://example.com",
                                    "title": "Example Page"
                                }
                            },
                            {
                                "web": {
                                    "uri": "https://kotlinlang.org",
                                    "title": "Kotlin Language"
                                }
                            }
                        ]
                    }
                }
            ]
        }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json)
                .addHeader("Content-Type", "application/json")
        )

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = "search query"))))
        )
        val response = api.generateContent("models/gemini-2.0-flash", "test-key", request)

        val metadata = response.groundingMetadata
        assertNotNull(metadata)
        assertEquals(2, metadata?.groundingChunks?.size)
        assertEquals("https://example.com", metadata?.groundingChunks?.get(0)?.web?.uri)
        assertEquals("Example Page", metadata?.groundingChunks?.get(0)?.web?.title)
        assertEquals("https://kotlinlang.org", metadata?.groundingChunks?.get(1)?.web?.uri)
        assertEquals("Kotlin Language", metadata?.groundingChunks?.get(1)?.web?.title)
    }
}
