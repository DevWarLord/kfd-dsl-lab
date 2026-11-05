package lesson09.dsl

import lesson09.HttpResponse

/**
 * Validates [response] against the checks configured in [block].
 *
 * Equivalent to `responseSpec(block).validate(response)`: the block runs once,
 * the collected rules run once, and a new [ValidationResult] is returned.
 */
fun validate(response: HttpResponse, block: ResponseChecks.() -> Unit): ValidationResult =
    responseSpec(block).validate(response)

/**
 * Runs [block] once and stores the resulting rules. No response exists yet,
 * so no value checks and no user predicates are executed here.
 */
fun responseSpec(block: ResponseChecks.() -> Unit): ResponseSpec =
    ResponseSpec(ResponseChecks().apply(block).build())

/** Reusable, immutable set of response rules. */
class ResponseSpec internal constructor(internal val checks: List<Check<HttpResponse>>) {
    /** Runs the stored rules on [response] with a new mismatch accumulator. */
    fun validate(response: HttpResponse): ValidationResult {
        val mismatches = MismatchCollector()
        checks.runAll(response, mismatches)
        return mismatches.toResult()
    }
}

/** Response level: chooses which part of the response to check. */
@HttpCheckDsl
class ResponseChecks internal constructor() {
    private val checks = CheckList<HttpResponse>()

    fun status(block: StatusChecks.() -> Unit) {
        checks.addNested {
            val nested = StatusChecks(Path("status")).apply(block).build()
            Check { response, mismatches -> nested.runAll(response.status, mismatches) }
        }
    }

    /** Checks the values of header [name]; the name is matched case-insensitively. */
    fun header(name: String, block: HeaderChecks.() -> Unit) {
        checks.addNested {
            val nested = HeaderChecks(Path("headers", name)).apply(block).build()
            Check { response, mismatches -> nested.runAll(response.headerValues(name), mismatches) }
        }
    }

    fun body(block: BodyChecks.() -> Unit) {
        checks.addNested {
            val nested = BodyChecks(Path("body")).apply(block).build()
            Check { response, mismatches -> nested.runAll(response.body, mismatches) }
        }
    }

    /**
     * Inserts the rules of [spec] at this position. Nothing is checked here;
     * including the same spec twice runs its rules twice.
     */
    fun include(spec: ResponseSpec) {
        spec.checks.forEach(checks::add)
    }

    internal fun build(): List<Check<HttpResponse>> = checks.snapshot()
}

/**
 * Values of header [name] from every key that equals it ignoring case,
 * in the iteration order of [HttpResponse.headers]; `null` if there is no such key.
 */
internal fun HttpResponse.headerValues(name: String): List<String>? {
    val matching = headers.entries.filter { it.key.equals(name, ignoreCase = true) }
    if (matching.isEmpty()) return null
    return matching.flatMap { it.value }
}
