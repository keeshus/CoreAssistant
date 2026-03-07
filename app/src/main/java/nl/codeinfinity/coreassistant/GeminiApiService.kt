package nl.codeinfinity.coreassistant

import retrofit2.http.GET
import retrofit2.http.Query

interface GeminiApiService {
    @GET("v1beta/models")
    suspend fun getModels(
        @Query("key") apiKey: String
    ): GeminiModelsResponse
}

data class GeminiModelsResponse(
    val models: List<GeminiModel>
)

data class GeminiModel(
    val name: String,
    val displayName: String,
    val description: String
)
