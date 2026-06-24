package nl.codeinfinity.coreassistant

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class ApiModelTest {

    private val gson = Gson()

    @Test
    fun `GenerateContentRequest serialization`() {
        val request = GenerateContentRequest(
            contents = listOf(
                Content(role = "user", parts = listOf(Part(text = "Hello")))
            ),
            tools = listOf(Tool(googleSearch = GoogleSearch())),
            generationConfig = GenerationConfig(
                includeThoughts = true,
                responseModalities = listOf("TEXT")
            ),
            systemInstruction = Content(
                role = "system",
                parts = listOf(Part(text = "Be helpful"))
            )
        )
        val json = gson.toJson(request)
        assertTrue(json.contains("generation_config"))
        assertTrue(json.contains("system_instruction"))
        assertTrue(json.contains("google_search"))

        val deserialized = gson.fromJson(json, GenerateContentRequest::class.java)
        assertEquals(1, deserialized.contents.size)
        assertEquals("user", deserialized.contents[0].role)
        assertEquals("Hello", deserialized.contents[0].parts[0].text)
        assertNotNull(deserialized.tools)
        assertEquals(1, deserialized.tools?.size)
        assertNotNull(deserialized.tools?.first()?.googleSearch)
        assertNotNull(deserialized.generationConfig)
        assertEquals(true, deserialized.generationConfig?.includeThoughts)
        assertEquals(listOf("TEXT"), deserialized.generationConfig?.responseModalities)
        assertNotNull(deserialized.systemInstruction)
        assertEquals("system", deserialized.systemInstruction?.role)
    }

    @Test
    fun `Content with parts`() {
        val content = Content(
            role = "user",
            parts = listOf(
                Part(text = "text part"),
                Part(text = "thought part", thought = true),
                Part(inlineData = InlineData(mimeType = "image/png", data = "base64data")),
                Part(fileData = FileData(mimeType = "application/pdf", fileUri = "https://example.com/doc.pdf")),
                Part(executableCode = ExecutableCode(language = "python", code = "print('hello')")),
                Part(codeExecutionResult = CodeExecutionResult(outcome = "OK", output = "hello"))
            )
        )
        val json = gson.toJson(content)
        assertTrue(json.contains("executable_code"))
        assertTrue(json.contains("code_execution_result"))

        val deserialized = gson.fromJson(json, Content::class.java)
        assertEquals("user", deserialized.role)
        assertEquals(6, deserialized.parts.size)
        assertEquals("text part", deserialized.parts[0].text)
        assertEquals(true, deserialized.parts[1].thought)
        assertNotNull(deserialized.parts[2].inlineData)
        assertEquals("image/png", deserialized.parts[2].inlineData?.mimeType)
        assertNotNull(deserialized.parts[3].fileData)
        assertEquals("application/pdf", deserialized.parts[3].fileData?.mimeType)
        assertNotNull(deserialized.parts[4].executableCode)
        assertEquals("python", deserialized.parts[4].executableCode?.language)
        assertNotNull(deserialized.parts[5].codeExecutionResult)
        assertEquals("OK", deserialized.parts[5].codeExecutionResult?.outcome)

        val camelJson = """{"role":"user","parts":[{"executableCode":{"language":"python","code":"print(1)"}}]}"""
        val fromCamel = gson.fromJson(camelJson, Content::class.java)
        assertNotNull(fromCamel.parts[0].executableCode)
    }

    @Test
    fun `Part with inlineData`() {
        val part = Part(inlineData = InlineData(mimeType = "image/jpeg", data = "/9j/4AAQSkZJRg..."))
        val json = gson.toJson(part)
        assertTrue(json.contains("inlineData"))

        val deserialized = gson.fromJson(json, Part::class.java)
        assertNotNull(deserialized.inlineData)
        assertEquals("image/jpeg", deserialized.inlineData?.mimeType)
        assertEquals("/9j/4AAQSkZJRg...", deserialized.inlineData?.data)
    }

    @Test
    fun `Part with fileData`() {
        val part = Part(fileData = FileData(mimeType = "video/mp4", fileUri = "https://example.com/video.mp4"))
        val json = gson.toJson(part)
        assertTrue(json.contains("fileData"))

        val deserialized = gson.fromJson(json, Part::class.java)
        assertNotNull(deserialized.fileData)
        assertEquals("video/mp4", deserialized.fileData?.mimeType)
        assertEquals("https://example.com/video.mp4", deserialized.fileData?.fileUri)
    }

    @Test
    fun `Tool with googleSearch`() {
        val tool = Tool(googleSearch = GoogleSearch())
        val json = gson.toJson(tool)
        assertTrue(json.contains("google_search"))

        val deserialized = gson.fromJson(json, Tool::class.java)
        assertNotNull(deserialized.googleSearch)
    }

    @Test
    fun `GeminiError in response`() {
        val json = """{"error": {"code": 400, "message": "Invalid request", "status": "INVALID_ARGUMENT"}}"""
        val response = gson.fromJson(json, GenerateContentResponse::class.java)
        assertNull(response.candidates)
        assertNotNull(response.error)
        assertEquals(400, response.error?.code)
        assertEquals("Invalid request", response.error?.message)
        assertEquals("INVALID_ARGUMENT", response.error?.status)
    }

    @Test
    fun `Candidate with finishReason`() {
        val json = """{"candidates": [{"finishReason": "STOP"}, {"finishReason": "MAX_TOKENS"}]}"""
        val response = gson.fromJson(json, GenerateContentResponse::class.java)
        assertEquals(2, response.candidates?.size)
        assertEquals("STOP", response.candidates?.get(0)?.finishReason)
        assertEquals("MAX_TOKENS", response.candidates?.get(1)?.finishReason)
    }

    @Test
    fun `GroundingMetadata complete`() {
        val json = """{
            "candidates": [{
                "groundingMetadata": {
                    "searchEntryPoint": {"html": "<div>search</div>"},
                    "groundingChunks": [{"web": {"uri": "https://example.com", "title": "Example"}}],
                    "groundingSupports": [{"segment": {"startIndex": 0, "endIndex": 5, "text": "hello"}, "groundingChunkIndices": [0], "confidenceScores": [0.9]}]
                }
            }]
        }""".trimIndent()
        val response = gson.fromJson(json, GenerateContentResponse::class.java)
        val metadata = response.candidates?.first()?.groundingMetadata
        assertNotNull(metadata)
        assertNotNull(metadata?.searchEntryPoint)
        assertEquals("<div>search</div>", metadata?.searchEntryPoint?.html)
        assertEquals(1, metadata?.groundingChunks?.size)
        assertEquals("https://example.com", metadata?.groundingChunks?.first()?.web?.uri)
        assertEquals("Example", metadata?.groundingChunks?.first()?.web?.title)
        assertEquals(1, metadata?.groundingSupports?.size)
        assertEquals(0, metadata?.groundingSupports?.first()?.segment?.startIndex)
        assertEquals(5, metadata?.groundingSupports?.first()?.segment?.endIndex)
        assertEquals("hello", metadata?.groundingSupports?.first()?.segment?.text)
        assertEquals(listOf(0), metadata?.groundingSupports?.first()?.groundingChunkIndices)
        assertEquals(listOf(0.9), metadata?.groundingSupports?.first()?.confidenceScores)
    }

    @Test
    fun `GeminiModelsResponse`() {
        val json = """{"models": [{"name": "models/gemini-pro", "displayName": "Gemini Pro", "description": "Best model"}]}"""
        val response = gson.fromJson(json, GeminiModelsResponse::class.java)
        assertEquals(1, response.models.size)
        assertEquals("models/gemini-pro", response.models[0].name)
        assertEquals("Gemini Pro", response.models[0].displayName)
        assertEquals("Best model", response.models[0].description)

        val minimalJson = """{"models": [{"name": "models/gemini-flash"}]}"""
        val minimal = gson.fromJson(minimalJson, GeminiModelsResponse::class.java)
        assertEquals("models/gemini-flash", minimal.models[0].name)
        assertNull(minimal.models[0].displayName)
        assertNull(minimal.models[0].description)
    }
}
