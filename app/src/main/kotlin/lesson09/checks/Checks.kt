package lesson09.checks

import lesson09.HttpResponse
import lesson09.dsl.ResponseSpec
import lesson09.dsl.ValidationResult
import lesson09.dsl.responseSpec

/*
 * DSL versions of lesson09.legacy.check01–check06.
 * Each check is described once as a ResponseSpec and returns the full list of mismatches.
 */

/** Status 204; body is null or "". */
val check01Spec: ResponseSpec = responseSpec {
    status { equalTo(204) }
    body { absentOrEmpty() }
}

/** Status 200; some Content-Type value starts with application/json; body is not null and not empty (not parsed). */
val check02Spec: ResponseSpec = responseSpec {
    status { equalTo(200) }
    header("Content-Type") { startsWith("application/json") }
    body { notEmpty() }
}

/** Status 200; Accept has the exact values application/json and text/html. */
val check03Spec: ResponseSpec = responseSpec {
    status { equalTo(200) }
    header("Accept") {
        hasValue("application/json")
        hasValue("text/html")
    }
}

/** Status 200; body is a JSON object; id is present (null allowed); name is the string Ivanov. */
val check04Spec: ResponseSpec = responseSpec {
    status { equalTo(200) }
    body {
        json {
            field("id") { present() }
            field("name") { equalTo("Ivanov") }
        }
    }
}

/** Status 200; body is a JSON object; id is the integer 42; active is true. */
val check05Spec: ResponseSpec = responseSpec {
    status { equalTo(200) }
    body {
        json {
            field("id") { equalTo(42) }
            field("active") { equalTo(true) }
        }
    }
}

/** Status 422; some Content-Type value starts with application/json; error is "invalid"; retry is false. */
val check06Spec: ResponseSpec = responseSpec {
    status { equalTo(422) }
    header("Content-Type") { startsWith("application/json") }
    body {
        json {
            field("error") { equalTo("invalid") }
            field("retry") { equalTo(false) }
        }
    }
}

fun check01(response: HttpResponse): ValidationResult = check01Spec.validate(response)
fun check02(response: HttpResponse): ValidationResult = check02Spec.validate(response)
fun check03(response: HttpResponse): ValidationResult = check03Spec.validate(response)
fun check04(response: HttpResponse): ValidationResult = check04Spec.validate(response)
fun check05(response: HttpResponse): ValidationResult = check05Spec.validate(response)
fun check06(response: HttpResponse): ValidationResult = check06Spec.validate(response)
