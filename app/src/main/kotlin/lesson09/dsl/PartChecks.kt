package lesson09.dsl

import lesson09.support.json.JsonParseResult
import lesson09.support.json.parseJsonObject

/** Status level. */
@HttpCheckDsl
class StatusChecks internal constructor(private val path: Path) {
    private val checks = CheckList<Int>()

    fun equalTo(expected: Int) = expect("$expected") { it == expected }

    /** Optional extra: both bounds are included. */
    fun inRange(range: IntRange) = expect("in $range") { it in range }

    private fun expect(expected: String, accepts: (Int) -> Boolean) {
        checks.add { status, mismatches ->
            if (!accepts(status)) mismatches.report(path, expected, Actual.Value(status))
        }
    }

    internal fun build(): List<Check<Int>> = checks.snapshot()
}

/**
 * Header level. Every operation passes when at least one header value
 * satisfies it; different operations may pass on different values.
 * Values are compared as is: case-sensitive, not trimmed, not split by commas.
 */
@HttpCheckDsl
class HeaderChecks internal constructor(private val path: Path) {
    private val checks = CheckList<List<String>?>()

    /** Some value is exactly [value]. */
    fun hasValue(value: String) = satisfies("exactly ${quote(value)}") { it == value }

    /** Some value starts with [prefix] (plain [String.startsWith]). */
    fun startsWith(prefix: String) = satisfies("prefix ${quote(prefix)}") { it.startsWith(prefix) }

    /**
     * Some value satisfies [predicate]; [expectation] describes it in the report.
     * The predicate is stored and called only during validation, and never
     * when the header is missing or has no values.
     */
    fun satisfies(expectation: String, predicate: (String) -> Boolean) {
        checks.add { values, mismatches ->
            when {
                values == null -> mismatches.report(path, expectation, Actual.Missing)
                values.none(predicate) -> mismatches.report(path, expectation, Actual.Value(values))
            }
        }
    }

    internal fun build(): List<Check<List<String>?>> = checks.snapshot()
}

/** Body level. */
@HttpCheckDsl
class BodyChecks internal constructor(private val path: Path) {
    private val checks = CheckList<String?>()

    /** Passes for `null` and `""`; a string of spaces is not empty. */
    fun absentOrEmpty() {
        checks.add { body, mismatches ->
            if (!body.isNullOrEmpty()) mismatches.report(path, "absent or empty", Actual.Value(body))
        }
    }

    /** Passes for any string longer than zero, including spaces. Does not parse the body. */
    fun notEmpty() {
        checks.add { body, mismatches ->
            when {
                body == null -> mismatches.report(path, "non-empty", Actual.Missing)
                body.isEmpty() -> mismatches.report(path, "non-empty", Actual.Value(body))
            }
        }
    }

    /**
     * Parses the body as a JSON object on every run of this block. If the body
     * is missing or invalid, one mismatch is reported at `body.json` and the
     * field checks of this block are skipped.
     */
    fun json(block: JsonObjectChecks.() -> Unit) {
        val jsonPath = path.child("json")
        checks.addNested {
            val nested = JsonObjectChecks(jsonPath).apply(block).build()
            Check { body, mismatches ->
                if (body == null) {
                    mismatches.report(jsonPath, JSON_OBJECT, Actual.Missing)
                    return@Check
                }
                when (val parsed = parseJsonObject(body)) {
                    is JsonParseResult.Success -> nested.runAll(parsed.fields, mismatches)
                    is JsonParseResult.Failure ->
                        mismatches.report(jsonPath, JSON_OBJECT, Actual.Unavailable("invalid JSON: ${parsed.reason}"))
                }
            }
        }
    }

    internal fun build(): List<Check<String?>> = checks.snapshot()

    private companion object {
        const val JSON_OBJECT = "JSON object"
    }
}

/** JSON object level: selects fields. Selecting a field reports nothing by itself. */
@HttpCheckDsl
class JsonObjectChecks internal constructor(private val path: Path) {
    private val checks = CheckList<Map<String, Any?>>()

    fun field(name: String, block: JsonFieldChecks.() -> Unit) {
        checks.addNested {
            val nested = JsonFieldChecks(path.child(name)).apply(block).build()
            Check { fields, mismatches ->
                val value = if (fields.containsKey(name)) FieldValue.Present(fields[name]) else FieldValue.Missing
                nested.runAll(value, mismatches)
            }
        }
    }

    internal fun build(): List<Check<Map<String, Any?>>> = checks.snapshot()
}

/** A JSON field as seen by field checks: a present `null` differs from a missing key. */
internal sealed interface FieldValue {
    data object Missing : FieldValue
    data class Present(val value: Any?) : FieldValue
}

/** JSON field level. Equality compares both type and value. */
@HttpCheckDsl
class JsonFieldChecks internal constructor(private val path: Path) {
    private val checks = CheckList<FieldValue>()

    /** The key exists, with any value including `null`. */
    fun present() {
        checks.add { field, mismatches ->
            if (field is FieldValue.Missing) mismatches.report(path, "present", Actual.Missing)
        }
    }

    fun equalTo(expected: String) = expectValue(expected)

    fun equalTo(expected: Long) = expectValue(expected)

    /** Convenience overload: JSON integers are parsed as [Long]. */
    fun equalTo(expected: Int) = expectValue(expected.toLong())

    fun equalTo(expected: Boolean) = expectValue(expected)

    /** Optional extra: the key exists and holds JSON `null`. */
    fun isNull() = expectValue(null)

    private fun expectValue(expected: Any?) {
        val description = renderValue(expected)
        checks.add { field, mismatches ->
            when (field) {
                FieldValue.Missing -> mismatches.report(path, description, Actual.Missing)
                is FieldValue.Present ->
                    if (field.value != expected) mismatches.report(path, description, Actual.Value(field.value))
            }
        }
    }

    internal fun build(): List<Check<FieldValue>> = checks.snapshot()
}
