package lesson09

import lesson09.dsl.HeaderChecks
import lesson09.dsl.validate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HeaderRulesTest {
    private fun check(headers: Map<String, List<String>>, name: String = "Accept", block: HeaderChecks.() -> Unit) =
        validate(response(headers = headers)) { header(name, block) }

    @Test
    fun missingHeaderEmptyListAndEmptyStringAreDifferent() {
        val absent = check(emptyMap()) { hasValue("text/html") }
        val noValues = check(mapOf("Accept" to emptyList())) { hasValue("text/html") }
        val emptyValue = check(mapOf("Accept" to listOf(""))) { hasValue("text/html") }

        assertEquals(listOf(mismatch("headers.Accept", "exactly \"text/html\"", missing)), absent.mismatches)
        assertEquals(listOf(mismatch("headers.Accept", "exactly \"text/html\"", value(emptyList<String>()))), noValues.mismatches)
        assertEquals(listOf(mismatch("headers.Accept", "exactly \"text/html\"", value(listOf("")))), emptyValue.mismatches)
        assertEquals(
            listOf(
                "headers.Accept: expected exactly \"text/html\", actual <missing>",
                "headers.Accept: expected exactly \"text/html\", actual []",
                "headers.Accept: expected exactly \"text/html\", actual [\"\"]",
            ),
            listOf(absent, noValues, emptyValue).map { it.render() },
        )
        assertTrue(check(mapOf("Accept" to listOf(""))) { hasValue("") }.isSuccess)
        assertTrue(check(mapOf("Accept" to listOf("x"))) { startsWith("") }.isSuccess)
        assertEquals(1, check(mapOf("Accept" to emptyList())) { startsWith("") }.mismatches.size)
    }

    @Test
    fun headerNameIgnoresCaseAndPathKeepsTheWrittenName() {
        val headers = mapOf("cOnTeNt-TyPe" to listOf("text/plain"))

        assertTrue(check(headers, "content-type") { hasValue("text/plain") }.isSuccess)
        assertTrue(check(headers, "CONTENT-TYPE") { hasValue("text/plain") }.isSuccess)
        assertEquals(
            listOf(mismatch("headers.Content-Type", "exactly \"application/json\"", value(listOf("text/plain")))),
            check(headers, "Content-Type") { hasValue("application/json") }.mismatches,
        )
    }

    @Test
    fun keysDifferingByCaseAreMergedInMapOrder() {
        val headers = linkedMapOf(
            "Accept" to listOf("a"),
            "X-Other" to listOf("ignored"),
            "ACCEPT" to listOf("b", "c"),
            "accept" to emptyList(),
        )

        assertTrue(check(headers) { hasValue("a"); hasValue("c") }.isSuccess)
        assertEquals(
            listOf(mismatch("headers.Accept", "exactly \"d\"", value(listOf("a", "b", "c")))),
            check(headers) { hasValue("d") }.mismatches,
        )
        assertEquals(
            listOf(mismatch("headers.Accept", "exactly \"d\"", value(emptyList<String>()))),
            check(linkedMapOf("Accept" to emptyList(), "accept" to emptyList())) { hasValue("d") }.mismatches,
        )
    }

    @Test
    fun differentOperationsMayPassOnDifferentValues() {
        val split = mapOf("Accept" to listOf("application/json", "text/html"))
        val joined = mapOf("Accept" to listOf("application/json,text/html"))
        val rules: HeaderChecks.() -> Unit = {
            hasValue("application/json")
            hasValue("text/html")
        }

        assertTrue(check(split, block = rules).isSuccess)
        assertEquals(
            listOf(
                mismatch("headers.Accept", "exactly \"application/json\"", value(listOf("application/json,text/html"))),
                mismatch("headers.Accept", "exactly \"text/html\"", value(listOf("application/json,text/html"))),
            ),
            check(joined, block = rules).mismatches,
        )
    }

    @Test
    fun valuesAreCaseSensitiveAndNotTrimmed() {
        fun contentType(vararg values: String) = mapOf("Content-Type" to values.toList())

        assertEquals(1, check(contentType("Application/JSON"), "Content-Type") { hasValue("application/json") }.mismatches.size)
        assertEquals(1, check(contentType("Application/JSON"), "Content-Type") { startsWith("application/json") }.mismatches.size)
        assertEquals(1, check(contentType(" application/json"), "Content-Type") { startsWith("application/json") }.mismatches.size)
        assertEquals(1, check(contentType("application/json "), "Content-Type") { hasValue("application/json") }.mismatches.size)
        assertTrue(check(contentType("application/json "), "Content-Type") { startsWith("application/json") }.isSuccess)
        assertTrue(check(contentType("application/jsonp"), "Content-Type") { startsWith("application/json") }.isSuccess)
        assertEquals(
            "headers.Content-Type: expected exactly \"application/json\", actual [\" application/json \"]",
            check(contentType(" application/json "), "Content-Type") { hasValue("application/json") }.render(),
        )
    }

    @Test
    fun extraHeadersDoNotMatter() {
        val headers = mapOf(
            "Content-Type" to listOf("application/json"),
            "X-Request-Id" to listOf("1"),
            "Accept" to listOf("*/*"),
        )

        assertTrue(check(headers, "Content-Type") { hasValue("application/json") }.isSuccess)
        assertNotEquals(emptyList(), check(headers, "Accept") { hasValue("text/html") }.mismatches)
    }
}
