package lesson09

import lesson09.dsl.Mismatch
import lesson09.dsl.ValidationResult
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import lesson09.checks.check01 as dslCheck01
import lesson09.checks.check02 as dslCheck02
import lesson09.checks.check03 as dslCheck03
import lesson09.checks.check04 as dslCheck04
import lesson09.checks.check05 as dslCheck05
import lesson09.checks.check06 as dslCheck06
import lesson09.legacy.check01 as legacyCheck01
import lesson09.legacy.check02 as legacyCheck02
import lesson09.legacy.check03 as legacyCheck03
import lesson09.legacy.check04 as legacyCheck04
import lesson09.legacy.check05 as legacyCheck05
import lesson09.legacy.check06 as legacyCheck06

/**
 * For every legacy check: one response that satisfies all conditions, then
 * responses that break exactly one condition. On each response the legacy
 * Boolean must match `isSuccess`, and the full mismatch list must match.
 */
class LegacyComparisonTest {
    private class Case(val name: String, val response: HttpResponse, vararg expected: Mismatch) {
        val expected: List<Mismatch> = expected.toList()
    }

    private fun compare(
        legacy: (HttpResponse) -> Boolean,
        dsl: (HttpResponse) -> ValidationResult,
        passing: HttpResponse,
        vararg violations: Case,
    ) {
        val cases = listOf(Case("passing", passing)) + violations
        assertTrue(legacy(passing), "passing response must satisfy the legacy check")
        for (case in cases) {
            val result = dsl(case.response)
            assertEquals(legacy(case.response), result.isSuccess, "success differs for '${case.name}'")
            assertEquals(case.expected, result.mismatches, "mismatches differ for '${case.name}'")
        }
    }

    @Test
    fun check01() {
        val ok = response(204, body = null)
        compare(
            ::legacyCheck01, ::dslCheck01, ok,
            Case("empty body is also fine", ok.copy(body = "")),
            Case("wrong status", ok.copy(status = 200), statusMismatch(204, 200)),
            Case("blank body", ok.copy(body = " "), mismatch("body", "absent or empty", value(" "))),
            Case("non-empty body", ok.copy(body = "{}"), mismatch("body", "absent or empty", value("{}"))),
        )
    }

    @Test
    fun check02() {
        val ok = response(
            200,
            mapOf("content-type" to listOf("application/json; charset=utf-8"), "X-Extra" to listOf("1")),
            "not a json",
        )
        compare(
            ::legacyCheck02, ::dslCheck02, ok,
            Case("wrong status", ok.copy(status = 201), statusMismatch(200, 201)),
            Case(
                "missing Content-Type", ok.copy(headers = mapOf("X-Extra" to listOf("1"))),
                mismatch("headers.Content-Type", "prefix \"application/json\"", missing),
            ),
            Case(
                "other Content-Type", ok.copy(headers = mapOf("Content-Type" to listOf("text/plain"))),
                mismatch("headers.Content-Type", "prefix \"application/json\"", value(listOf("text/plain"))),
            ),
            Case("null body", ok.copy(body = null), mismatch("body", "non-empty", missing)),
            Case("empty body", ok.copy(body = ""), mismatch("body", "non-empty", value(""))),
        )
    }

    @Test
    fun check03() {
        val ok = response(200, mapOf("Accept" to listOf("text/html", "application/json")))
        compare(
            ::legacyCheck03, ::dslCheck03, ok,
            Case("wrong status", ok.copy(status = 404), statusMismatch(200, 404)),
            Case(
                "no application/json", ok.copy(headers = mapOf("Accept" to listOf("text/html"))),
                mismatch("headers.Accept", "exactly \"application/json\"", value(listOf("text/html"))),
            ),
            Case(
                "no text/html", ok.copy(headers = mapOf("Accept" to listOf("application/json"))),
                mismatch("headers.Accept", "exactly \"text/html\"", value(listOf("application/json"))),
            ),
        )
    }

    @Test
    fun check04() {
        val ok = response(200, body = """{"id":null,"name":"Ivanov","age":30}""")
        compare(
            ::legacyCheck04, ::dslCheck04, ok,
            Case("wrong status", ok.copy(status = 500), statusMismatch(200, 500)),
            Case("null body", ok.copy(body = null), mismatch("body.json", "JSON object", missing)),
            Case(
                "invalid JSON", ok.copy(body = """{"id":1,"name":"Ivanov",}"""),
                mismatch("body.json", "JSON object", invalidJson("Expected a quoted field name")),
            ),
            Case(
                "missing id", ok.copy(body = """{"name":"Ivanov"}"""),
                mismatch("body.json.id", "present", missing),
            ),
            Case(
                "other name", ok.copy(body = """{"id":1,"name":"ivanov"}"""),
                mismatch("body.json.name", "\"Ivanov\"", value("ivanov")),
            ),
            Case(
                "name of another type", ok.copy(body = """{"id":1,"name":true}"""),
                mismatch("body.json.name", "\"Ivanov\"", value(true)),
            ),
        )
    }

    @Test
    fun check05() {
        val ok = response(200, body = """{"id":42,"active":true,"name":"x"}""")
        compare(
            ::legacyCheck05, ::dslCheck05, ok,
            Case("wrong status", ok.copy(status = 204), statusMismatch(200, 204)),
            Case(
                "not an object", ok.copy(body = "[]"),
                mismatch("body.json", "JSON object", invalidJson("Invalid token at offset 0")),
            ),
            Case(
                "id as string", ok.copy(body = """{"id":"42","active":true}"""),
                mismatch("body.json.id", "42", value("42")),
            ),
            Case(
                "other id", ok.copy(body = """{"id":43,"active":true}"""),
                mismatch("body.json.id", "42", value(43L)),
            ),
            Case(
                "inactive", ok.copy(body = """{"id":42,"active":false}"""),
                mismatch("body.json.active", "true", value(false)),
            ),
            Case(
                "missing active", ok.copy(body = """{"id":42}"""),
                mismatch("body.json.active", "true", missing),
            ),
        )
    }

    @Test
    fun check06() {
        val ok = response(
            422,
            mapOf("Content-Type" to listOf("application/json")),
            """{"error":"invalid","retry":false}""",
        )
        compare(
            ::legacyCheck06, ::dslCheck06, ok,
            Case("wrong status", ok.copy(status = 200), statusMismatch(422, 200)),
            Case(
                "missing Content-Type", ok.copy(headers = emptyMap()),
                mismatch("headers.Content-Type", "prefix \"application/json\"", missing),
            ),
            Case(
                "invalid JSON", ok.copy(body = "invalid"),
                mismatch("body.json", "JSON object", invalidJson("Invalid token at offset 0")),
            ),
            Case(
                "other error", ok.copy(body = """{"error":"invalid ","retry":false}"""),
                mismatch("body.json.error", "\"invalid\"", value("invalid ")),
            ),
            Case(
                "retry true", ok.copy(body = """{"error":"invalid","retry":true}"""),
                mismatch("body.json.retry", "false", value(true)),
            ),
            Case(
                "retry null", ok.copy(body = """{"error":"invalid","retry":null}"""),
                mismatch("body.json.retry", "false", value(null)),
            ),
        )
    }
}
