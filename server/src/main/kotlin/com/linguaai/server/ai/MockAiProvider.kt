package com.linguaai.server.ai

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Deterministic provider for demos, tests and offline development.
 * Failure injection via the `AI_MOCK_SCENARIO` env (or request scenarioHint):
 * timeout | rate_limit | invalid_json | empty | echo_system | echo_user.
 */
class MockAiProvider(
    private val defaultScenario: String? = null,
) : AiProvider {
    override suspend fun chat(request: AiChatRequest): AiChatResponse {
        val scenario = defaultScenario ?: request.scenarioHint
        val scenarioResponse = resolveScenario(request, scenario)
        if (scenarioResponse != null) return scenarioResponse

        if (request.jsonMode) {
            val quizJson =
                """
                {"questions":[
                  {"prompt":"風邪を（　）ように気をつけて。","options":["ひか","ひかない","ひいた","ひこう"],"correctAnswer":"ひかない","explanation":"ように with a negative verb expresses avoiding an outcome."},
                  {"prompt":"買えないわけではない means what?","options":["Cannot buy","Not that I cannot buy it","Will definitely buy","Refuse to buy"],"correctAnswer":"Not that I cannot buy it","explanation":"わけではない is a partial negation."},
                  {"prompt":"Company-decided outcomes use which pattern?","options":["ことにする","ことになる","ようにする","ことにしている"],"correctAnswer":"ことになる","explanation":"ことになる marks decisions made by circumstances."}
                ]}
                """.trimIndent()
            return AiChatResponse(content = quizJson)
        }

        return AiChatResponse(content = tutorReply(request))
    }

    private fun resolveScenario(
        request: AiChatRequest,
        scenario: String?,
    ): AiChatResponse? =
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
                    AiChatResponse(content = "this is not json at all")
                } else {
                    null
                }
            }
            "empty" -> AiChatResponse(content = "")
            // Test-support scenario: echoes the assembled system prompt back as
            // the reply, so an integration test can assert on what the tutor was
            // actually told rather than on a separately constructed string.
            "echo_system" ->
                AiChatResponse(
                    content =
                        request.messages
                            .firstOrNull { it.role == "system" }
                            ?.content
                            .orEmpty(),
                )
            // Test-support scenario for transport/templating contracts. It
            // deliberately echoes the exact last user message so integration
            // tests can detect accidental DTO rendering or prefix changes.
            "echo_user" ->
                AiChatResponse(
                    content =
                        request.messages
                            .lastOrNull { it.role == "user" }
                            ?.content
                            .orEmpty(),
                )
            "practice-score" -> practiceScoreResponse(valid = true)
            "invalid_practice_score" -> practiceScoreResponse(valid = false)
            "echo_quiz_prompt" -> {
                val prompt =
                    request.messages
                        .lastOrNull { it.role == "user" }
                        ?.content
                        .orEmpty()
                AiChatResponse(
                    content =
                        """
                        {
                          "questions":[{
                            "prompt":${Json.encodeToString(prompt)},
                            "options":["A","B","C","D"],
                            "correctAnswer":"A",
                            "explanation":"Prompt probe"
                          }]
                        }
                        """.trimIndent(),
                )
            }
            else -> null
        }

    private fun practiceScoreResponse(valid: Boolean): AiChatResponse {
        val content =
            if (valid) {
                """
                {
                  "score":84,
                  "grammarScore":82,
                  "vocabularyScore":86,
                  "naturalness":83,
                  "mistakes":["Use a softer request ending in formal situations."],
                  "recommendations":["Practice one more restaurant role-play."]
                }
                """.trimIndent()
            } else {
                """
                {
                  "score":140,
                  "grammarScore":-1,
                  "vocabularyScore":86,
                  "naturalness":83,
                  "mistakes":[],
                  "recommendations":[]
                }
                """.trimIndent()
            }
        return AiChatResponse(content = content)
    }

    /**
     * Deterministic tutoring reply, keyed on the learner's last message.
     *
     * Split out of [chat] because that function was carrying the failure-injection
     * dispatch, the JSON quiz branch and this reply selection together, which put
     * it well past the complexity threshold. The dispatch is inherently branchy;
     * this part is not, and it did not belong in the same function.
     */
    private fun tutorReply(request: AiChatRequest): String {
        val lastUser =
            request.messages
                .lastOrNull { it.role == "user" }
                ?.content
                .orEmpty()
        val grammarHint =
            request.messages
                .firstOrNull()
                ?.content
                ?.contains(PromptBuilder.HEADING_GRAMMAR) == true
        return buildString {
            append("Good question! ")
            if (grammarHint) append("About the grammar point you are studying: ")
            append(
                when {
                    lastUser.contains("ように") ->
                        "～ように attaches to dictionary or negative forms " +
                            "to express purpose or hope, e.g. 忘れないようにメモします " +
                            "(I take notes so I won't forget). Unlike ～ために it also works with " +
                            "potential verbs and natural outcomes."
                    lastUser.contains("行きませんでしたから") ||
                        lastUser.endsWith("。") &&
                        lastUser.contains("から") ->
                        "Your sentence reads unnaturally: 昨日学校に行きませんでしたから病気でした。" +
                            "A natural version is 病気だったので、学校に行きませんでした。" +
                            "Use ので/から after a plain reason clause, not after the past-tense result."
                    else ->
                        "Let's break it down step by step, keeping your current level in mind. " +
                            "Try building one example sentence with the pattern and I will correct it."
                },
            )
        }
    }
}
