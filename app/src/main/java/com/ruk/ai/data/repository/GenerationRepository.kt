package com.ruk.ai.data.repository

import com.ruk.ai.data.model.GenerationDto
import com.ruk.ai.data.model.ImageRequest
import com.ruk.ai.data.model.VideoRequest
import com.ruk.ai.data.remote.RukApi

class GenerationRepository(private val api: RukApi) {

    suspend fun history(kind: String? = null): Result<List<GenerationDto>> =
        runCatchingResult { api.generations(kind) }

    suspend fun image(
        prompt: String,
        aspectRatio: String,
        quality: String,
        model: String
    ): Result<GenerationDto> = runCatchingResult {
        api.generateImage(ImageRequest(prompt, aspectRatio, quality, model))
    }

    suspend fun video(
        prompt: String,
        aspectRatio: String,
        durationSeconds: Int,
        imageUrl: String? = null
    ): Result<GenerationDto> = runCatchingResult {
        api.generateVideo(VideoRequest(prompt, aspectRatio, durationSeconds, imageUrl))
    }

    /** Polls a job; pass refresh=true to ask the backend to re-check the provider. */
    suspend fun poll(id: String): Result<GenerationDto> =
        runCatchingResult { api.generation(id, refresh = true) }

    suspend fun delete(id: String): Result<Unit> = runCatchingResult {
        api.deleteGeneration(id)
    }
}
