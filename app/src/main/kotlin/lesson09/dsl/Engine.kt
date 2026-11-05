package lesson09.dsl

/**
 * Marks every receiver type of the DSL. Because all receivers carry the same
 * marker, an inner block cannot implicitly call a function of an outer one;
 * an explicit labelled receiver such as `this@validate` is still allowed.
 */
@DslMarker
@Target(AnnotationTarget.CLASS)
annotation class HttpCheckDsl

/**
 * A stored rule. It is created while a configuration block runs and executed
 * later, once per validation run, against the part of the response it checks.
 */
internal fun interface Check<in T> {
    fun run(subject: T, mismatches: MismatchCollector)
}

/** Fresh accumulator for one validation run. */
internal class MismatchCollector {
    private val items = mutableListOf<Mismatch>()

    fun report(path: Path, expected: String, actual: Actual) {
        items += Mismatch(path, expected, actual)
    }

    fun toResult(): ValidationResult = ValidationResult(items.toList())
}

/** Ordered rules of one block. */
internal class CheckList<T> {
    private val items = mutableListOf<Check<T>?>()

    fun add(check: Check<T>) {
        items += check
    }

    /**
     * Adds a rule produced by a nested block. The slot is reserved before the
     * nested block runs, so the nested rule keeps the position where its block
     * was written even if the nested block reaches an outer receiver by label.
     */
    fun addNested(create: () -> Check<T>) {
        val slot = items.size
        items += null
        items[slot] = create()
    }

    /** Immutable snapshot; later calls on the builder do not affect it. */
    fun snapshot(): List<Check<T>> = items.map { checkNotNull(it) { "Nested block is still being configured" } }
}

internal fun <T> List<Check<T>>.runAll(subject: T, mismatches: MismatchCollector) {
    for (check in this) check.run(subject, mismatches)
}
