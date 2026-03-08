package nl.codeinfinity.coreassistant

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

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
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
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
    val text: String
)

data class Tool(
    val googleSearchRetrieval: GoogleSearchRetrieval? = null
)

class GoogleSearchRetrieval

// Response Models
data class GenerateContentResponse(
    val candidates: List<Candidate>
) {
    val text: String?
        get() = candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
}

data class Candidate(
    val content: Content,
    val finishReason: String? = null
)

data class GeminiModelsResponse(
    val models: List<GeminiModel>
)

data class GeminiModel(
    val name: String,
    val displayName: String,
    val description: String
)
