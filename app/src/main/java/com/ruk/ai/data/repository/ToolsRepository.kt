package com.ruk.ai.data.repository

import com.ruk.ai.data.model.ToolDto
import com.ruk.ai.data.model.ToolRequest
import com.ruk.ai.data.remote.RukApi

class ToolsRepository(private val api: RukApi) {

    suspend fun tools(): Result<List<ToolDto>> = runCatchingResult { api.tools() }

    suspend fun run(
        tool: String,
        input: String,
        options: Map<String, String> = emptyMap()
    ): Result<String> = runCatchingResult {
        api.runTool(ToolRequest(tool, input, options)).output
    }
}
