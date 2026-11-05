package lesson09

import lesson09.dsl.validate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class OptionalOperationsTest {
    @Test
    fun statusRangeIncludesBothBounds() {
        fun mismatches(status: Int) = validate(response(status)) { status { inRange(200..299) } }.mismatches

        assertEquals(listOf(mismatch("status", "in 200..299", value(199))), mismatches(199))
        assertEquals(emptyList(), mismatches(200))
        assertEquals(emptyList(), mismatches(299))
        assertEquals(listOf(mismatch("status", "in 200..299", value(300))), mismatches(300))
    }

    @Test
    fun jsonNullCheckNeedsPresentNull() {
        fun mismatches(body: String) =
            validate(response(body = body)) { body { json { field("id") { isNull() } } } }.mismatches

        assertEquals(emptyList(), mismatches("""{"id":null}"""))
        assertEquals(listOf(mismatch("body.json.id", "null", missing)), mismatches("{}"))
        assertEquals(listOf(mismatch("body.json.id", "null", value(0L))), mismatches("""{"id":0}"""))
        assertEquals(listOf(mismatch("body.json.id", "null", value("null"))), mismatches("""{"id":"null"}"""))
    }
}
