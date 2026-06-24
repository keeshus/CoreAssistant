package nl.codeinfinity.coreassistant

import org.junit.Assert.*
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `groundingMetadata null round-trip`() {
        // Gson.toJson(null) returns "null" string, not null
        assertEquals("null", converters.fromGroundingMetadata(null))
        assertNull(converters.toGroundingMetadata(null))
    }

    @Test
    fun `groundingMetadata round-trip preserves all fields`() {
        val metadata = GroundingMetadata(
            searchEntryPoint = SearchEntryPoint(html = "<div>search results</div>"),
            groundingChunks = listOf(
                GroundingChunk(web = WebChunk(uri = "https://example.com", title = "Example")),
                GroundingChunk(web = WebChunk(uri = "https://test.org", title = null))
            ),
            groundingSupports = listOf(
                GroundingSupport(
                    segment = Segment(startIndex = 0, endIndex = 5, text = "hello"),
                    groundingChunkIndices = listOf(0, 1),
                    confidenceScores = listOf(0.95, 0.87)
                ),
                GroundingSupport(
                    segment = null,
                    groundingChunkIndices = null,
                    confidenceScores = null
                )
            )
        )

        val json = converters.fromGroundingMetadata(metadata)
        val result = converters.toGroundingMetadata(json)

        assertNotNull(result)
        assertEquals(metadata.searchEntryPoint?.html, result?.searchEntryPoint?.html)
        assertEquals(metadata.groundingChunks?.size, result?.groundingChunks?.size)
        assertEquals(metadata.groundingChunks?.first()?.web?.uri, result?.groundingChunks?.first()?.web?.uri)
        assertEquals(metadata.groundingChunks?.first()?.web?.title, result?.groundingChunks?.first()?.web?.title)
        assertEquals(metadata.groundingChunks?.get(1)?.web?.uri, result?.groundingChunks?.get(1)?.web?.uri)
        assertEquals(metadata.groundingChunks?.get(1)?.web?.title, result?.groundingChunks?.get(1)?.web?.title)
        assertEquals(metadata.groundingSupports?.size, result?.groundingSupports?.size)
        assertEquals(metadata.groundingSupports?.first()?.segment?.startIndex, result?.groundingSupports?.first()?.segment?.startIndex)
        assertEquals(metadata.groundingSupports?.first()?.segment?.endIndex, result?.groundingSupports?.first()?.segment?.endIndex)
        assertEquals(metadata.groundingSupports?.first()?.segment?.text, result?.groundingSupports?.first()?.segment?.text)
        assertEquals(metadata.groundingSupports?.first()?.groundingChunkIndices, result?.groundingSupports?.first()?.groundingChunkIndices)
        assertEquals(metadata.groundingSupports?.first()?.confidenceScores, result?.groundingSupports?.first()?.confidenceScores)
        assertNull(result?.groundingSupports?.get(1)?.segment)
        assertNull(result?.groundingSupports?.get(1)?.groundingChunkIndices)
        assertNull(result?.groundingSupports?.get(1)?.confidenceScores)
    }

    @Test
    fun `groundingMetadata with null fields round-trip`() {
        val metadata = GroundingMetadata(
            searchEntryPoint = null,
            groundingChunks = null,
            groundingSupports = null
        )

        val json = converters.fromGroundingMetadata(metadata)
        val result = converters.toGroundingMetadata(json)

        assertNotNull(result)
        assertNull(result?.searchEntryPoint)
        assertNull(result?.groundingChunks)
        assertNull(result?.groundingSupports)
    }

    @Test
    fun `attachmentList null round-trip`() {
        assertEquals("null", converters.fromAttachmentList(null))
        assertNull(converters.toAttachmentList(null))
    }

    @Test
    fun `attachmentList empty round-trip`() {
        val json = converters.fromAttachmentList(emptyList())
        val result = converters.toAttachmentList(json)
        assertNotNull(result)
        assertTrue(result?.isEmpty() ?: false)
    }

    @Test
    fun `attachmentList round-trip preserves all fields`() {
        val attachments = listOf(
            Attachment(
                uri = "content://media/external/images/1",
                mimeType = "image/jpeg",
                fileName = "photo.jpg",
                fileSize = 1048576L,
                remoteUri = "https://generativelanguage.googleapis.com/files/abc123",
                thoughtSignature = null
            ),
            Attachment(
                uri = "content://media/external/images/2",
                mimeType = "image/png",
                fileName = "screenshot.png",
                fileSize = 2048L,
                remoteUri = null,
                thoughtSignature = "sig_abc"
            ),
            Attachment(
                uri = "content://media/external/video/3",
                mimeType = "video/mp4",
                fileName = "clip.mp4",
                fileSize = 0L,
                remoteUri = null,
                thoughtSignature = null
            )
        )

        val json = converters.fromAttachmentList(attachments)
        val result = converters.toAttachmentList(json)

        assertNotNull(result)
        assertEquals(3, result?.size)

        result?.forEachIndexed { index, attachment ->
            val original = attachments[index]
            assertEquals("uri mismatch at index $index", original.uri, attachment.uri)
            assertEquals("mimeType mismatch at index $index", original.mimeType, attachment.mimeType)
            assertEquals("fileName mismatch at index $index", original.fileName, attachment.fileName)
            assertEquals("fileSize mismatch at index $index", original.fileSize, attachment.fileSize)
            assertEquals("remoteUri mismatch at index $index", original.remoteUri, attachment.remoteUri)
            assertEquals("thoughtSignature mismatch at index $index", original.thoughtSignature, attachment.thoughtSignature)
        }
    }
}
