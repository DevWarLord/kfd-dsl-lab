package lesson09

import lesson09.dsl.ResponseChecks
import lesson09.dsl.validate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Order of mismatches, repeated checks, repeated runs and immutability of the input. */
class RunIndependenceTest {
    private val expectedType = "application/json"
    private val rules: ResponseChecks.() -> Unit = {
        status { equalTo(200) }
        header("Content-Type") { startsWith(expectedType) }
        body { json { field("id") { equalTo(1) } } }
    }

    private val good = response(200, mapOf("Content-Type" to listOf("application/json")), """{"id":1}""")
    private val bad = response(404, mapOf("Content-Type" to listOf("text/html")), """{"id":2}""")
    private val badMismatches = listOf(
        statusMismatch(200, 404),
        mismatch("headers.Content-Type", "prefix \"application/json\"", value(listOf("text/html"))),
        mismatch("body.json.id", "1", value(2L)),
    )

    @Test
    fun independentViolationsKeepTheWrittenOrder() {
        val result = validate(bad) {
            body { json { field("id") { equalTo(1) } } }
            header("Content-Type") { startsWith("application/json") }
            status { equalTo(200) }
        }

        assertEquals(badMismatches.reversed(), result.mismatches)
    }

    @Test
    fun nestedBlocksRunAfterEarlierOperationsOfTheirParent() {
        val result = validate(response(500, body = """{"a":1}""")) {
            status { equalTo(201) }
            body {
                absentOrEmpty()
                json {
                    field("a") { equalTo(2); present(); equalTo("1") }
                    field("b") { present() }
                }
                notEmpty()
            }
            status { equalTo(202) }
        }

        assertEquals(
            listOf(
                statusMismatch(201, 500),
                mismatch("body", "absent or empty", value("""{"a":1}""")),
                mismatch("body.json.a", "2", value(1L)),
                mismatch("body.json.a", "\"1\"", value(1L)),
                mismatch("body.json.b", "present", missing),
                statusMismatch(202, 500),
            ),
            result.mismatches,
        )
    }

    @Test
    fun repeatedChecksAreNotMerged() {
        val result = validate(response(500, emptyMap(), null)) {
            status {
                equalTo(200)
                equalTo(200)
            }
            header("Accept") {
                hasValue("text/html")
                hasValue("text/html")
            }
            body {
                json { }
                json { }
            }
        }

        assertEquals(
            listOf(
                statusMismatch(200, 500),
                statusMismatch(200, 500),
                mismatch("headers.Accept", "exactly \"text/html\"", missing),
                mismatch("headers.Accept", "exactly \"text/html\"", missing),
                mismatch("body.json", "JSON object", missing),
                mismatch("body.json", "JSON object", missing),
            ),
            result.mismatches,
        )
    }

    @Test
    fun repeatedRunsWithTheSameRulesAreIndependent() {
        val first = validate(good, rules)
        val second = validate(bad, rules)
        val third = validate(good, rules)

        assertTrue(first.isSuccess)
        assertEquals(badMismatches, second.mismatches)
        assertTrue(third.isSuccess)

        validate(bad, rules)
        assertEquals(emptyList(), first.mismatches)
        assertEquals(badMismatches, second.mismatches)
        assertEquals(emptyList(), third.mismatches)
    }

    @Test
    fun inputResponseIsNotChangedAndResultDoesNotDependOnLaterChanges() {
        val values = mutableListOf("text/plain", "")
        val headers = linkedMapOf<String, List<String>>("Content-Type" to values, "content-type" to mutableListOf())
        val input = HttpResponse(500, headers, "{}")
        val before = HttpResponse(input.status, headers.mapValues { it.value.toList() }, input.body)

        val result = validate(input, rules)

        assertEquals(before, input)
        assertEquals(listOf("text/plain", ""), values)

        values += "application/json"
        headers["X-New"] = listOf("1")
        assertEquals(
            mismatch("headers.Content-Type", "prefix \"application/json\"", value(listOf("text/plain", ""))),
            result.mismatches[1],
        )
    }
}
