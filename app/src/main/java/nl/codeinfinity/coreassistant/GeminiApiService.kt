package nl.codeinfinity.coreassistant

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part as RetrofitPart
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

    @POST("v1beta/files")
    suspend fun uploadFile(
        @Query("key") apiKey: String,
        @Header("X-Goog-Upload-Protocol") protocol: String = "multipart",
        @Body body: RequestBody
    ): FileUploadResponse

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
    val tools: List<Tool>? = null,
    val generationConfig: GenerationConfig? = null
)

data class GenerationConfig(
    val includeThoughts: Boolean? = null,
    val thinkingConfig: ThinkingConfig? = null
)

data class ThinkingConfig(
    val includeThoughts: Boolean? = null,
    val thinkingLevel: String? = null
)

data class Content(
    val role: String? = null,
    val parts: List<nl.codeinfinity.coreassistant.Part>
)

data class Part(
    @SerializedName("text") val text: String? = null,
    @SerializedName("thought") val thought: Boolean? = null,
    @SerializedName("inline_data") val inlineData: InlineData? = null,
    @SerializedName("file_data") val fileData: FileData? = null
)

data class InlineData(
    val mimeType: String,
    val data: String
)

data class FileData(
    val mimeType: String,
    val fileUri: String
)

data class FileUploadResponse(
    val file: GeminiFile
)

data class GeminiFile(
    val name: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: String,
    val createTime: String,
    val updateTime: String,
    val expirationTime: String,
    val sha256Hash: String,
    val uri: String
)

data class Tool(
    val googleSearch: GoogleSearch? = null
)

class GoogleSearch

// Response Models
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null,
    val promptFeedback: PromptFeedback? = null,
    val error: GeminiError? = null
) {
    val text: String?
        get() = candidates?.firstOrNull()?.content?.parts?.find { (it.thought == null || it.thought == false) && it.text != null }?.text
        
    val thought: String?
        get() = candidates?.firstOrNull()?.content?.parts?.find { it.thought == true && it.text != null }?.text
        
    val groundingMetadata: GroundingMetadata?
        get() = candidates?.firstOrNull()?.groundingMetadata
}

data class GeminiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

data class PromptFeedback(
    val blockReason: String? = null,
    val safetyRatings: List<SafetyRating>? = null
)

data class SafetyRating(
    val category: String,
    val probability: String,
    val blocked: Boolean? = null
)

data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
    val groundingMetadata: GroundingMetadata? = null,
    val safetyRatings: List<SafetyRating>? = null
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
