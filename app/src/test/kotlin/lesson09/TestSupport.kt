package lesson09

import lesson09.dsl.Actual
import lesson09.dsl.Mismatch
import lesson09.dsl.Path

/** Short constructors that keep expected mismatch lists readable. */

fun response(
    status: Int = 200,
    headers: Map<String, List<String>> = emptyMap(),
    body: String? = null,
): HttpResponse = HttpResponse(status, headers, body)

fun mismatch(path: String, expected: String, actual: Actual): Mismatch =
    Mismatch(Path(path.split('.')), expected, actual)

fun value(value: Any?): Actual = Actual.Value(value)

val missing: Actual = Actual.Missing

fun invalidJson(reason: String): Actual = Actual.Unavailable("invalid JSON: $reason")

fun statusMismatch(expected: Int, actual: Int): Mismatch = mismatch("status", "$expected", value(actual))
