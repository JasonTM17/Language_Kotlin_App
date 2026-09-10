package com.linguaai.server.ai

/**
 * Deterministic provider for demos, tests and offline development.
 * Failure injection via the `AI_MOCK_SCENARIO` env (or request scenarioHint):
 * timeout | rate_limit | invalid_json | empty.
 */
class MockAiProvider(private val defaultScenario: String? = null) : AiProvider {

    override suspend fun chat(request: AiChatRequest): AiChatResponse {
        val scenario = request.scenarioHint ?: defaultScenario
        when (scenario) {
            "timeout" -> throw AiProviderException(
                AiProviderException.Kind.TIMEOUT,
                "Mock provider timeout",
            )
            "rate_limit" -> throw AiProviderException(
                AiProviderException.Kind.RATE_LIMITED,
                "Mock provider rate limited",
                retryAfterSeconds = 30,
            )
            "invalid_json" -> {
                if (request.jsonMode) {
                    return AiChatResponse(content = "this is not json at all")
                }
            }
            "empty" -> return AiChatResponse(content = "")
        }

        if (request.jsonMode) {
            val quizJson = """
                {"questions":[
                  {"prompt":"風邪を（　）ように気をつけて。","options":["ひか","ひかない","ひいた","ひこう"],"correctAnswer":"ひかない","explanation":"ように with a negative verb expresses avoiding an outcome."},
                  {"prompt":"買えないわけではない means what?","options":["Cannot buy","Not that I cannot buy it","Will definitely buy","Refuse to buy"],"correctAnswer":"Not that I cannot buy it","explanation":"わけではない is a partial negation."},
                  {"prompt":"Company-decided outcomes use which pattern?","options":["ことにする","ことになる","ようにする","ことにしている"],"correctAnswer":"ことになる","explanation":"ことになる marks decisions made by circumstances."}
                ]}
            """.trimIndent()
            return AiChatResponse(content = quizJson)
        }

        val lastUser = request.messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val grammarHint = request.messages.firstOrNull()?.content?.contains("Grammar context") == true
        val reply = buildString {
            append("Good question! ")
            if (grammarHint) append("About the grammar point you are studying: ")
            append(
                when {
                    lastUser.contains("ように") -> "～ように attaches to dictionary or negative forms to express purpose or hope, e.g. 忘れないようにメモします (I take notes so I won't forget). Unlike ～ために it also works with potential verbs and natural outcomes."
                    lastUser.contains("行きませんでしたから") || lastUser.endsWith("。") && lastUser.contains("から") -> "Your sentence reads unnaturally: 昨日学校に行きませんでしたから病気でした。A natural version is 病気だったので、学校に行きませんでした。Use ので/から after a plain reason clause, not after the past-tense result."
                    else -> "Let's break it down step by step, keeping your current level in mind. Try building one example sentence with the pattern and I will correct it."
                },
            )
        }
        return AiChatResponse(content = reply)
    }
}
