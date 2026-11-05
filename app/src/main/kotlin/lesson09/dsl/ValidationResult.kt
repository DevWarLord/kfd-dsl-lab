package lesson09.dsl

/**
 * Outcome of one validation run.
 *
 * The run succeeds when [mismatches] is empty. Mismatches are kept in the order
 * in which the checks were executed; identical mismatches are not merged.
 */
data class ValidationResult(val mismatches: List<Mismatch>) {
    val isSuccess: Boolean get() = mismatches.isEmpty()

    /** Human-readable report: one mismatch per line, empty string on success. */
    fun render(): String = mismatches.joinToString(separator = "\n") { it.render() }
}

/**
 * One failed check.
 *
 * @property path the checked part of the response, for example `headers.Content-Type`.
 * @property expected description of what the check expected.
 * @property actual the actual value, or the reason why it is not available.
 */
data class Mismatch(val path: Path, val expected: String, val actual: Actual) {
    fun render(): String = "$path: expected $expected, actual ${actual.render()}"

    override fun toString(): String = render()
}

/**
 * Location of a checked part of the response, stored as a list of segments.
 *
 * Text form: segments joined with `.`; a segment that is not made only of
 * letters, digits, `_` and `-` is written in brackets as a quoted string,
 * so `body.json["a.b"]` and `body.json.a.b` never collide.
 */
data class Path(val segments: List<String>) {
    constructor(vararg segments: String) : this(segments.toList())

    fun child(segment: String): Path = Path(segments + segment)

    override fun toString(): String = buildString {
        segments.forEachIndexed { index, segment ->
            when {
                simpleSegment.matches(segment) -> {
                    if (index > 0) append('.')
                    append(segment)
                }
                else -> append('[').append(quote(segment)).append(']')
            }
        }
    }

    private companion object {
        val simpleSegment = Regex("[A-Za-z0-9_-]+")
    }
}

/** Actual side of a mismatch. */
sealed interface Actual {
    fun render(): String

    /**
     * A value that was found in the response: an `Int` status, a `String?` body,
     * a `List<String>` of header values or a JSON field value
     * (`String`, `Long`, `Boolean` or `null`).
     */
    data class Value(val value: Any?) : Actual {
        override fun render(): String = renderValue(value)
    }

    /** The value could not be obtained; [reason] explains why. */
    data class Unavailable(val reason: String) : Actual {
        override fun render(): String = "<$reason>"
    }

    companion object {
        /** The header, body or JSON field is absent. */
        val Missing: Actual = Unavailable("missing")
    }
}

/** JSON-like text for a value: strings are quoted, lists are bracketed. */
internal fun renderValue(value: Any?): String = when (value) {
    null -> "null"
    is String -> quote(value)
    is List<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ", ") { renderValue(it) }
    else -> value.toString()
}

internal fun quote(text: String): String = buildString {
    append('"')
    for (char in text) {
        when {
            char == '"' -> append("\\\"")
            char == '\\' -> append("\\\\")
            char == '\n' -> append("\\n")
            char == '\r' -> append("\\r")
            char == '\t' -> append("\\t")
            char < ' ' -> append("\\u").append(char.code.toString(16).padStart(4, '0'))
            else -> append(char)
        }
    }
    append('"')
}
