package lesson09

import lesson09.dsl.validate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BodyAndJsonRulesTest {
    private fun absentOrEmpty(body: String?) = validate(response(body = body)) { body { absentOrEmpty() } }.mismatches
    private fun notEmpty(body: String?) = validate(response(body = body)) { body { notEmpty() } }.mismatches

    @Test
    fun absentOrEmptyBody() {
        assertEquals(emptyList(), absentOrEmpty(null))
        assertEquals(emptyList(), absentOrEmpty(""))
        assertEquals(listOf(mismatch("body", "absent or empty", value(" "))), absentOrEmpty(" "))
        assertEquals(listOf(mismatch("body", "absent or empty", value("ok"))), absentOrEmpty("ok"))
    }

    @Test
    fun nonEmptyBodyIncludesSpacesAndIsNotParsed() {
        assertEquals(listOf(mismatch("body", "non-empty", missing)), notEmpty(null))
        assertEquals(listOf(mismatch("body", "non-empty", value(""))), notEmpty(""))
        assertEquals(emptyList(), notEmpty(" "))
        assertEquals(emptyList(), notEmpty("definitely { not json"))
    }

    @Test
    fun presenceAndEqualityDistinguishMissingNullAndTypes() {
        fun idPresent(body: String) = validate(response(body = body)) { body { json { field("id") { present() } } } }
        fun idIs42(body: String) = validate(response(body = body)) { body { json { field("id") { equalTo(42) } } } }

        assertEquals(listOf(mismatch("body.json.id", "present", missing)), idPresent("{}").mismatches)
        assertTrue(idPresent("""{"id":null}""").isSuccess)
        assertTrue(idIs42("""{"id":42}""").isSuccess)
        assertEquals(listOf(mismatch("body.json.id", "42", value("42"))), idIs42("""{"id":"42"}""").mismatches)
        assertEquals(listOf(mismatch("body.json.id", "42", value(null))), idIs42("""{"id":null}""").mismatches)
        assertEquals(listOf(mismatch("body.json.id", "42", missing)), idIs42("{}").mismatches)
    }

    @Test
    fun eachOperationOnMissingFieldReportsSeparately() {
        val result = validate(response(body = "{}")) {
            body {
                json {
                    field("name") {
                        present()
                        equalTo("Ivanov")
                        equalTo(true)
                    }
                }
            }
        }

        assertEquals(
            listOf(
                mismatch("body.json.name", "present", missing),
                mismatch("body.json.name", "\"Ivanov\"", missing),
                mismatch("body.json.name", "true", missing),
            ),
            result.mismatches,
        )
    }

    @Test
    fun selectingFieldAloneReportsNothingAndExtraFieldsAreIgnored() {
        val result = validate(response(body = """{"id":7,"extra":"x","more":false}""")) {
            body {
                json {
                    field("missing") { }
                    field("id") { equalTo(7L) }
                }
            }
        }

        assertTrue(result.isSuccess)
    }

    @Test
    fun invalidJsonSkipsFieldChecksButNotOtherChecks() {
        val result = validate(response(status = 500, headers = mapOf("Content-Type" to listOf("text/plain")), body = "{")) {
            status { equalTo(200) }
            body {
                json {
                    field("id") { present() }
                    field("name") { equalTo("Ivanov") }
                }
                notEmpty()
                absentOrEmpty()
            }
            header("Content-Type") { startsWith("application/json") }
        }

        assertEquals(
            listOf(
                statusMismatch(200, 500),
                mismatch("body.json", "JSON object", invalidJson("Expected a field name")),
                mismatch("body", "absent or empty", value("{")),
                mismatch("headers.Content-Type", "prefix \"application/json\"", value(listOf("text/plain"))),
            ),
            result.mismatches,
        )
    }

    @Test
    fun nullBodyGivesOneJsonMismatchPerJsonBlock() {
        val result = validate(response(body = null)) {
            body {
                json { field("id") { present() } }
                json { field("id") { present() } }
            }
        }

        assertEquals(
            listOf(mismatch("body.json", "JSON object", missing), mismatch("body.json", "JSON object", missing)),
            result.mismatches,
        )
        assertEquals(
            """
            body.json: expected JSON object, actual <missing>
            body.json: expected JSON object, actual <missing>
            """.trimIndent(),
            result.render(),
        )
    }

    @Test
    fun jsonRunsEvenWhenContentTypeIsNotJson() {
        val result = validate(response(headers = mapOf("Content-Type" to listOf("text/plain")), body = """{"ok":true}""")) {
            body { json { field("ok") { equalTo(true) } } }
        }

        assertTrue(result.isSuccess)
    }

    @Test
    fun jsonValuesAreRenderedWithTheirTypes() {
        val result = validate(response(body = """{"s":"a\"b","n":-5,"b":false,"z":null}""")) {
            body {
                json {
                    field("s") { equalTo(1) }
                    field("n") { equalTo("-5") }
                    field("b") { equalTo("false") }
                    field("z") { equalTo(false) }
                }
            }
        }

        assertEquals(
            """
            body.json.s: expected 1, actual "a\"b"
            body.json.n: expected "-5", actual -5
            body.json.b: expected "false", actual false
            body.json.z: expected false, actual null
            """.trimIndent(),
            result.render(),
        )
    }
}
