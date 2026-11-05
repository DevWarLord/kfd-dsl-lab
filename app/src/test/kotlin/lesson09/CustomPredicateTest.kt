package lesson09

import lesson09.dsl.ValidationResult
import lesson09.dsl.validate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

private fun isNonBlank(value: String): Boolean = value.isNotBlank()

class CustomPredicateTest {
    private val responses = listOf(
        response(headers = mapOf("X-Trace" to listOf("abc-123"))),
        response(headers = mapOf("X-Trace" to listOf("   "))),
        response(headers = mapOf("X-Trace" to listOf(" ", "", "id"))),
        response(headers = mapOf("x-trace" to listOf(""))),
        response(headers = mapOf("X-Trace" to emptyList())),
        response(headers = emptyMap()),
    )

    private val expected = listOf(
        emptyList(),
        listOf(mismatch("headers.X-Trace", "non-blank value", value(listOf("   ")))),
        emptyList(),
        listOf(mismatch("headers.X-Trace", "non-blank value", value(listOf("")))),
        listOf(mismatch("headers.X-Trace", "non-blank value", value(emptyList<String>()))),
        listOf(mismatch("headers.X-Trace", "non-blank value", missing)),
    )

    private fun withLambdaLiteral(response: HttpResponse): ValidationResult = validate(response) {
        header("X-Trace") {
            satisfies("non-blank value") { value -> value.isNotBlank() }
        }
    }

    private val nonBlank: (String) -> Boolean = { value -> value.isNotBlank() }

    private fun withVariable(response: HttpResponse): ValidationResult = validate(response) {
        header("X-Trace") {
            satisfies("non-blank value", nonBlank)
        }
    }

    private fun withFunctionReference(response: HttpResponse): ValidationResult = validate(response) {
        header("X-Trace") {
            satisfies("non-blank value", ::isNonBlank)
        }
    }

    @Test
    fun threeWaysToPassThePredicateGiveTheSameResults() {
        val literal = responses.map(::withLambdaLiteral)
        val variable = responses.map(::withVariable)
        val reference = responses.map(::withFunctionReference)

        assertEquals(expected, literal.map { it.mismatches })
        assertEquals(literal, variable)
        assertEquals(literal, reference)
    }

    @Test
    fun predicateIsNotCalledWithoutValues() {
        var calls = 0
        val counting: (String) -> Boolean = { calls += 1; true }
        fun run(response: HttpResponse) = validate(response) { header("X-Trace") { satisfies("anything", counting) } }

        assertEquals(listOf(mismatch("headers.X-Trace", "anything", missing)), run(responses[5]).mismatches)
        assertEquals(listOf(mismatch("headers.X-Trace", "anything", value(emptyList<String>()))), run(responses[4]).mismatches)
        assertEquals(0, calls)

        assertEquals(emptyList(), run(responses[2]).mismatches)
        assertEquals(1, calls, "the first matching value is enough")
    }
}
