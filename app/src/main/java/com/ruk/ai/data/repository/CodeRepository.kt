package com.ruk.ai.data.repository

import com.ruk.ai.data.model.CodeAssistRequest
import com.ruk.ai.data.model.CodeAssistResponse
import com.ruk.ai.data.model.CodeRequest
import com.ruk.ai.data.model.CodeRunResult
import com.ruk.ai.data.model.LanguagesDto
import com.ruk.ai.data.remote.RukApi

class CodeRepository(private val api: RukApi) {

    suspend fun languages(): Result<LanguagesDto> = runCatchingResult { api.languages() }

    suspend fun run(language: String, code: String, stdin: String = ""): Result<CodeRunResult> =
        runCatchingResult { api.runCode(CodeRequest(language, code, stdin)) }

    suspend fun assist(
        language: String,
        code: String,
        instruction: String,
        action: String
    ): Result<CodeAssistResponse> = runCatchingResult {
        api.codeAssist(CodeAssistRequest(language, code, instruction, action))
    }
}
