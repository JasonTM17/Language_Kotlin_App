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
            return AiChatResponse(content = quizJson(quizLanguage(request.scenarioHint)))
        }

        return AiChatResponse(content = tutorReply(request))
    }

    /**
     * Quiz fixtures were Japanese-only while the catalogue now serves 17
     * languages. `generateQuiz` passes "quiz:<language>" so the deterministic
     * demo quiz is phrased about the learner's language; the JSON shape is
     * identical for every language so structured-output tests are unaffected.
     */
    private fun quizLanguage(scenarioHint: String?): String =
        scenarioHint
            ?.takeIf { it.startsWith("quiz:") }
            ?.removePrefix("quiz:")
            ?.takeIf { it.isNotBlank() }
            ?: "Japanese"

    private fun quizJson(language: String): String =
        """
        {"questions":[
          {"prompt":"Which article correctly completes the sentence in $language?","options":["the correct particle","a random verb","an unrelated noun","a greeting"],"correctAnswer":"the correct particle","explanation":"Deterministic demo quiz: the option matching the tested pattern is correct."},
          {"prompt":"Which sentence is grammatical in $language?","options":["The pattern-conforming sentence","A sentence missing its verb","A sentence with doubled subjects","A sentence with mismatched politeness"],"correctAnswer":"The pattern-conforming sentence","explanation":"Demo quiz: only the pattern-conforming option is grammatical."},
          {"prompt":"What does the level-appropriate greeting mean in $language?","options":["A polite greeting","A farewell","A number","A colour"],"correctAnswer":"A polite greeting","explanation":"Demo quiz: the greeting option is the correct meaning."}
        ]}
        """.trimIndent()

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
