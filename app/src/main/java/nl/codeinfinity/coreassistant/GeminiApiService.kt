package nl.codeinfinity.coreassistant

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GeminiApiService {
    @GET("v1beta/models")
    suspend fun getModels(
        @Query("key") apiKey: String
    ): GeminiModelsResponse

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/"

        fun create(): GeminiApiService {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GeminiApiService::class.java)
        }
    }
}

// Request Models
data class GenerateContentRequest(
    val contents: List<Content>,
    val tools: List<Tool>? = null
)

data class Content(
    val role: String? = null,
    val parts: List<Part>
)

data class Part(
    val text: String? = null,
    val thought: Boolean? = null
)

data class Tool(
    val googleSearch: GoogleSearch? = null
)

class GoogleSearch

// Response Models
data class GenerateContentResponse(
    val candidates: List<Candidate>
) {
    val text: String?
        get() = candidates.firstOrNull()?.content?.parts?.find { it.thought != true }?.text
        
    val thought: String?
        get() = candidates.firstOrNull()?.content?.parts?.find { it.thought == true }?.text
        
    val groundingMetadata: GroundingMetadata?
        get() = candidates.firstOrNull()?.groundingMetadata
}

data class Candidate(
    val content: Content,
    val finishReason: String? = null,
    val groundingMetadata: GroundingMetadata? = null
)

data class GroundingMetadata(
    val searchEntryPoint: SearchEntryPoint? = null,
    val groundingChunks: List<GroundingChunk>? = null,
    val groundingSupports: List<GroundingSupport>? = null
)

data class SearchEntryPoint(
    val html: String? = null
)

data class GroundingChunk(
    val web: WebChunk? = null
)

data class WebChunk(
    val uri: String? = null,
    val title: String? = null
)

data class GroundingSupport(
    val segment: Segment? = null,
    val groundingChunkIndices: List<Int>? = null,
    val confidenceScores: List<Double>? = null
)

data class Segment(
    val startIndex: Int? = null,
    val endIndex: Int? = null,
    val text: String? = null
)

data class GeminiModelsResponse(
    val models: List<GeminiModel>
)

data class GeminiModel(
    val name: String,
    val displayName: String,
    val description: String
)
