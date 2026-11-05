package lesson09

import lesson09.dsl.Actual
import lesson09.dsl.Mismatch
import lesson09.dsl.Path
import lesson09.dsl.ResponseChecks
import lesson09.dsl.validate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The example from the assignment, plus the path notation and the text report. */
class DslExampleTest {
    private val rules: ResponseChecks.() -> Unit = {
        status {
            equalTo(200)
        }
        header("Content-Type") {
            startsWith("application/json")
        }
        body {
            json {
                field("id") {
                    present()
                }
            }
        }
    }

    @Test
    fun matchingResponseHasNoMismatches() {
        val result = validate(response(200, mapOf("Content-Type" to listOf("application/json")), """{"id":42}"""), rules)

        assertTrue(result.isSuccess)
        assertEquals(emptyList(), result.mismatches)
        assertEquals("", result.render())
    }

    @Test
    fun everyFailedConditionIsReportedInOrder() {
        val result = validate(response(500, mapOf("Content-Type" to listOf("text/plain")), "{}"), rules)

        assertEquals(
            listOf(
                Mismatch(Path("status"), "200", Actual.Value(500)),
                Mismatch(Path("headers", "Content-Type"), "prefix \"application/json\"", Actual.Value(listOf("text/plain"))),
                Mismatch(Path("body", "json", "id"), "present", Actual.Unavailable("missing")),
            ),
            result.mismatches,
        )
        assertEquals(
            """
            status: expected 200, actual 500
            headers.Content-Type: expected prefix "application/json", actual ["text/plain"]
            body.json.id: expected present, actual <missing>
            """.trimIndent(),
            result.render(),
        )
    }

    @Test
    fun pathNotation() {
        assertEquals("status", Path("status").toString())
        assertEquals("headers.Content-Type", Path("headers", "Content-Type").toString())
        assertEquals("body", Path("body").toString())
        assertEquals("body.json", Path("body", "json").toString())
        assertEquals("body.json.id", Path("body", "json", "id").toString())
        assertEquals("body.json[\"a.b\"]", Path("body", "json", "a.b").toString())
        assertEquals("body.json[\"\"]", Path("body", "json", "").toString())
        assertEquals("body.json[\"say \\\"hi\\\"\"]", Path("body", "json", "say \"hi\"").toString())
    }

    @Test
    fun fieldNameWithDotKeepsItsOwnSegment() {
        val result = validate(response(body = """{"a":1}""")) {
            body { json { field("a.b") { present() } } }
        }

        assertEquals(listOf(Mismatch(Path("body", "json", "a.b"), "present", Actual.Missing)), result.mismatches)
        assertEquals("body.json[\"a.b\"]: expected present, actual <missing>", result.render())
    }

    @Test
    fun outerReceiverIsReachableByLabel() {
        val result = validate(response(500, body = "")) {
            body {
                notEmpty()
                this@validate.status { equalTo(200) }
            }
        }

        assertEquals(
            listOf(mismatch("body", "non-empty", value("")), statusMismatch(200, 500)),
            result.mismatches,
        )
    }
}
