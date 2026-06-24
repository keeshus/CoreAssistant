package nl.codeinfinity.coreassistant

import org.junit.Assert.*
import org.junit.Test

class GenerateContentResponseTest {

    @Test
    fun `text extracts from non-thought parts`() {
        val response = GenerateContentResponse(
            candidates = listOf(
                Candidate(
                    content = Content(
                        parts = listOf(
                            Part(text = "Hello"),
                            Part(text = "World")
                        )
                    )
                )
            )
        )
        assertEquals("Hello\nWorld", response.text)
    }

    @Test
    fun `text excludes thought parts`() {
        val response = GenerateContentResponse(
            candidates = listOf(
                Candidate(
                    content = Content(
                        parts = listOf(
                            Part(text = "visible", thought = false),
                            Part(text = "hidden", thought = true)
                        )
                    )
                )
            )
        )
        assertEquals("visible", response.text)
    }

    @Test
    fun `text returns null when all parts are thoughts`() {
        val response = GenerateContentResponse(
            candidates = listOf(
                Candidate(
                    content = Content(
                        parts = listOf(
                            Part(text = "thought", thought = true)
                        )
                    )
                )
            )
        )
        assertNull(response.text)
    }

    @Test
    fun `text returns null when no candidates`() {
        val response = GenerateContentResponse(candidates = null)
        assertNull(response.text)
    }

    @Test
    fun `text returns null when candidate has no content`() {
        val response = GenerateContentResponse(
            candidates = listOf(Candidate(content = null))
        )
        assertNull(response.text)
    }

    @Test
    fun `text returns null when parts is empty`() {
        val response = GenerateContentResponse(
            candidates = listOf(
                Candidate(content = Content(parts = emptyList()))
            )
        )
        assertNull(response.text)
    }

    @Test
    fun `thought extracts from thought parts`() {
        val response = GenerateContentResponse(
            candidates = listOf(
                Candidate(
                    content = Content(
                        parts = listOf(
                            Part(text = "my thought", thought = true)
                        )
                    )
                )
            )
        )
        assertEquals("my thought", response.thought)
    }

    @Test
    fun `thought returns first thought part`() {
        val response = GenerateContentResponse(
            candidates = listOf(
                Candidate(
                    content = Content(
                        parts = listOf(
                            Part(text = "first", thought = true),
                            Part(text = "second", thought = true)
                        )
                    )
                )
            )
        )
        assertEquals("first", response.thought)
    }

    @Test
    fun `thought returns null when no thought parts`() {
        val response = GenerateContentResponse(
            candidates = listOf(
                Candidate(
                    content = Content(
                        parts = listOf(Part(text = "normal"))
                    )
                )
            )
        )
        assertNull(response.thought)
    }

    @Test
    fun `inlineImages returns inlineData parts`() {
        val response = GenerateContentResponse(
            candidates = listOf(
                Candidate(
                    content = Content(
                        parts = listOf(
                            Part(inlineData = InlineData(mimeType = "image/png", data = "abc123"))
                        )
                    )
                )
            )
        )
        assertEquals(1, response.inlineImages?.size)
        assertEquals("image/png", response.inlineImages?.first()?.mimeType)
        assertEquals("abc123", response.inlineImages?.first()?.data)
    }

    @Test
    fun `inlineImages returns null when no images`() {
        val response = GenerateContentResponse(
            candidates = listOf(
                Candidate(
                    content = Content(
                        parts = listOf(Part(text = "text only"))
                    )
                )
            )
        )
        assertNull(response.inlineImages)
    }

    @Test
    fun `inlineImages returns null when no candidates`() {
        val response = GenerateContentResponse(candidates = null)
        assertNull(response.inlineImages)
    }

    @Test
    fun `groundingMetadata extracts from candidate`() {
        val metadata = GroundingMetadata(
            groundingChunks = listOf(
                GroundingChunk(web = WebChunk(uri = "https://example.com", title = "Example"))
            )
        )
        val response = GenerateContentResponse(
            candidates = listOf(
                Candidate(groundingMetadata = metadata)
            )
        )
        assertNotNull(response.groundingMetadata)
        assertEquals(1, response.groundingMetadata?.groundingChunks?.size)
        assertEquals("https://example.com", response.groundingMetadata?.groundingChunks?.first()?.web?.uri)
    }

    @Test
    fun `text handles null parts gracefully`() {
        val response = GenerateContentResponse(
            candidates = listOf(
                Candidate(
                    content = Content(parts = listOf(Part(text = null)))
                )
            )
        )
        assertNull(response.text)
    }

    @Test
    fun `text skips parts with null text`() {
        val response = GenerateContentResponse(
            candidates = listOf(
                Candidate(
                    content = Content(
                        parts = listOf(
                            Part(text = null),
                            Part(text = "actual")
                        )
                    )
                )
            )
        )
        assertEquals("actual", response.text)
    }
}
