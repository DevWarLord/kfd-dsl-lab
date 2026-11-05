package lesson09

import lesson09.dsl.responseSpec
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResponseSpecTest {
    private val json = mapOf("Content-Type" to listOf("application/json"))

    @Test
    fun configurationBlockRunsOnlyWhenTheSpecIsCreated() {
        var configured = 0
        val spec = responseSpec {
            configured += 1
            status { equalTo(200) }
        }
        assertEquals(1, configured)

        spec.validate(response(200))
        spec.validate(response(500))
        spec.validate(response(200))
        assertEquals(1, configured)
    }

    @Test
    fun predicateRunsOnlyDuringValidation() {
        var calls = 0
        val spec = responseSpec {
            header("X-Trace") {
                satisfies("non-blank value") { value -> calls += 1; value.isNotBlank() }
            }
        }
        assertEquals(0, calls)

        assertTrue(spec.validate(response(headers = mapOf("X-Trace" to listOf("id")))).isSuccess)
        assertEquals(1, calls)

        spec.validate(response())
        assertEquals(1, calls)
    }

    @Test
    fun goodBadGoodGivesIndependentResults() {
        val spec = responseSpec {
            status { equalTo(200) }
            header("Content-Type") { startsWith("application/json") }
        }

        val first = spec.validate(response(200, json))
        val second = spec.validate(response(500, emptyMap()))
        val third = spec.validate(response(200, json))

        assertTrue(first.isSuccess)
        assertEquals(
            listOf(statusMismatch(200, 500), mismatch("headers.Content-Type", "prefix \"application/json\"", missing)),
            second.mismatches,
        )
        assertTrue(third.isSuccess)
    }

    @Test
    fun laterRunDoesNotChangeEarlierMismatches() {
        val spec = responseSpec { status { equalTo(200) } }

        val first = spec.validate(response(404))
        val firstMismatches = first.mismatches
        spec.validate(response(500))
        spec.validate(response(200))

        assertEquals(listOf(statusMismatch(200, 404)), firstMismatches)
        assertEquals(listOf(statusMismatch(200, 404)), first.mismatches)
    }

    @Test
    fun sharedRulesCanBeIncludedInTwoSpecsWithoutLeakingErrors() {
        val commonRules = responseSpec { status { equalTo(200) } }
        val userRules = responseSpec {
            include(commonRules)
            body { json { field("id") { present() } } }
        }
        val adminRules = responseSpec {
            include(commonRules)
            header("X-Role") { hasValue("admin") }
        }

        val userResult = userRules.validate(response(500, body = "{}"))
        val adminResult = adminRules.validate(response(200, mapOf("X-Role" to listOf("admin"))))
        val commonResult = commonRules.validate(response(200))

        assertEquals(listOf(statusMismatch(200, 500), mismatch("body.json.id", "present", missing)), userResult.mismatches)
        assertTrue(adminResult.isSuccess)
        assertTrue(commonResult.isSuccess)
    }

    @Test
    fun includePositionIsKeptAndRepeatedIncludeRepeatsRules() {
        val commonRules = responseSpec { status { equalTo(200) } }
        val spec = responseSpec {
            header("Accept") { hasValue("text/html") }
            include(commonRules)
            body { notEmpty() }
            include(commonRules)
        }

        assertEquals(
            listOf(
                mismatch("headers.Accept", "exactly \"text/html\"", missing),
                statusMismatch(200, 503),
                mismatch("body", "non-empty", missing),
                statusMismatch(200, 503),
            ),
            spec.validate(response(503)).mismatches,
        )
        assertTrue(spec.validate(response(200, mapOf("Accept" to listOf("text/html")), "x")).isSuccess)
    }

    @Test
    fun specKeepsItsRulesWhenItsBuilderIsUsedLater() {
        var leaked: lesson09.dsl.ResponseChecks? = null
        val spec = responseSpec {
            leaked = this
            status { equalTo(200) }
        }
        leaked!!.status { equalTo(201) }

        assertEquals(listOf(statusMismatch(200, 500)), spec.validate(response(500)).mismatches)
    }
}
