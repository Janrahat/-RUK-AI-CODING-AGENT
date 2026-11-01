package com.ruk.ai.data.repository

import com.ruk.ai.data.model.DocumentDto
import com.ruk.ai.data.model.PdfAskRequest
import com.ruk.ai.data.remote.RukApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class PdfRepository(private val api: RukApi) {

    suspend fun documents(): Result<List<DocumentDto>> = runCatchingResult { api.documents() }

    suspend fun upload(bytes: ByteArray, filename: String): Result<DocumentDto> =
        runCatchingResult {
            val part = MultipartBody.Part.createFormData(
                "file",
                filename,
                bytes.toRequestBody("application/pdf".toMediaType())
            )
            api.uploadPdf(part)
        }

    suspend fun summarize(id: String): Result<String> = runCatchingResult {
        api.summarizePdf(id).summary
    }

    suspend fun ask(id: String, question: String): Result<String> = runCatchingResult {
        api.askPdf(id, PdfAskRequest(question)).answer
    }

    suspend fun delete(id: String): Result<Unit> = runCatchingResult { api.deleteDocument(id) }
}
