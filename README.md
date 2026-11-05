# HTTP Checks DSL — лабораторная «Свой DSL»

[![http-checks](https://github.com/DevWarLord/kfd-dsl-lab/actions/workflows/ci.yml/badge.svg)](https://github.com/DevWarLord/kfd-dsl-lab/actions/workflows/ci.yml)

Kotlin-DSL для проверки HTTP-ответов. Вместо `true`/`false` он возвращает **список всех расхождений**: для каждого указано, где оно найдено, что ожидалось и что пришло на самом деле. Через DSL переписаны шесть исходных функций `check01`–`check06`.

Полное условие задания лежит в [docs/lesson09.md](docs/lesson09.md).

## Запуск

Нужен JDK 25 в `JAVA_HOME`. Gradle ставить не нужно, проект использует Wrapper.

| Действие | Bash | PowerShell |
|---|---|---|
| Сборка и тесты | `bash ./gradlew build` | `.\gradlew.bat build` |
| Только тесты | `bash ./gradlew :app:test` | `.\gradlew.bat :app:test` |

Те же команды запускает GitHub Actions (`.github/workflows/ci.yml`) на Ubuntu и Windows.

## Пример

```kotlin
import lesson09.dsl.validate

val result = validate(response) {
    status {
        equalTo(200)
    }
    header("Content-Type") {
        startsWith("application/json")
    }
    body {
        json {
            field("id") {
                present()
            }
        }
    }
}

if (!result.isSuccess) println(result.render())
```

Для ответа `HttpResponse(500, mapOf("Content-Type" to listOf("text/plain")), "{}")` получаем три расхождения, в порядке записи проверок:

```text
status: expected 200, actual 500
headers.Content-Type: expected prefix "application/json", actual ["text/plain"]
body.json.id: expected present, actual <missing>
```

`render()` нужен только для чтения человеком. В самом результате хранятся структурированные данные:

```kotlin
ValidationResult(
    mismatches = listOf(
        Mismatch(Path("status"), "200", Actual.Value(500)),
        Mismatch(Path("headers", "Content-Type"), "prefix \"application/json\"", Actual.Value(listOf("text/plain"))),
        Mismatch(Path("body", "json", "id"), "present", Actual.Unavailable("missing")),
    )
)
```

Пользовательская проверка значения заголовка:

```kotlin
val result = validate(response) {
    header("X-Trace") {
        satisfies("non-blank value") { value -> value.isNotBlank() }
    }
}
```

Спецификацию можно описать один раз и применять к разным ответам, а также включать в другие спецификации:

```kotlin
val commonRules = responseSpec {
    status { equalTo(200) }
}

val userRules = responseSpec {
    include(commonRules)
    body {
        json {
            field("id") { present() }
            field("name") { equalTo("Ivanov") }
        }
    }
}

val first = userRules.validate(firstResponse)
val second = userRules.validate(secondResponse)
```

## Структура

```text
app/src/main/kotlin/lesson09/
├── HttpResponse.kt, legacy/, support/json/   стартовый проект, без изменений
├── dsl/
│   ├── ValidationResult.kt   ValidationResult, Mismatch, Path, Actual, текстовое представление
│   ├── Engine.kt             @HttpCheckDsl, внутренние Check, MismatchCollector, CheckList
│   ├── ResponseDsl.kt        validate, responseSpec, ResponseSpec, ResponseChecks
│   └── PartChecks.kt         StatusChecks, HeaderChecks, BodyChecks, JsonObjectChecks, JsonFieldChecks
└── checks/Checks.kt          check01–check06 через DSL
app/src/test/kotlin/lesson09/  тесты (стартовые LegacySmokeTest и JsonParserTest не менялись)
```

## Операции по уровням

У каждого блока свой тип получателя, и все они помечены одной аннотацией `@HttpCheckDsl` (`@DslMarker`).

| Блок | Получатель | Операции |
|---|---|---|
| Ответ | `ResponseChecks` | `status { }`, `header(name) { }`, `body { }`, `include(spec)` |
| Статус | `StatusChecks` | `equalTo(Int)`, `inRange(IntRange)`* |
| Заголовок | `HeaderChecks` | `hasValue(String)` (точное значение), `startsWith(String)`, `satisfies(description, (String) -> Boolean)` |
| Тело | `BodyChecks` | `absentOrEmpty()`, `notEmpty()`, `json { }` |
| JSON-объект | `JsonObjectChecks` | `field(name) { }` |
| Поле JSON | `JsonFieldChecks` | `present()`, `equalTo(String)`, `equalTo(Long)` / `equalTo(Int)`, `equalTo(Boolean)`, `isNull()`* |

\* дополнительные операции.

Так как маркер у всех получателей общий, компилятор не даёт неявно вызвать операцию внешнего блока из внутреннего:

```kotlin
validate(response) {
    body {
        status { equalTo(200) }  // ошибка компиляции: 'fun status(...)' cannot be called
                                 // in this context with an implicit receiver
        this@validate.status { equalTo(200) }  // можно: явный получатель через метку
    }
}
```

## Результат проверки

* `ValidationResult(mismatches: List<Mismatch>)` — data-класс. `isSuccess` равен `true`, когда список пуст. `render()` выдаёт текст: одно расхождение на строку.
* `Mismatch(path: Path, expected: String, actual: Actual)` хранит путь, ожидание и факт отдельно друг от друга.
* `Actual` — sealed-интерфейс с двумя вариантами:
  * `Actual.Value(value)` — значение, найденное в ответе: `Int` для статуса, `String` для тела, `List<String>` для значений заголовка, `String`/`Long`/`Boolean`/`null` для поля JSON;
  * `Actual.Unavailable(reason)` — значение недоступно. `Actual.Missing` — это `Unavailable("missing")`, для разбора JSON используется `Unavailable("invalid JSON: <причина от парсера>")`.
* Если данные не совпадают с ожиданием, исключение не бросается: добавляется расхождение.

### Запись пути

Путь хранится как список сегментов (`Path(segments: List<String>)`). В тексте сегменты соединяются точкой: `status`, `headers.Content-Type`, `body`, `body.json`, `body.json.id`. Если сегмент содержит что-то кроме латинских букв, цифр, `_` и `-`, он записывается в скобках как строка в кавычках: поле `a.b` превращается в `body.json["a.b"]`, а пустое имя — в `body.json[""]`. Поэтому `body.json["a.b"]` и `body.json.a.b` не путаются. Имя заголовка попадает в путь в том виде, в каком оно записано в DSL (`header("Content-Type")` даёт `headers.Content-Type` при любом регистре ключа в ответе).

### Запись значений

Значения записываются в стиле JSON, чтобы видеть тип и границы строки:

| Что | Пример |
|---|---|
| статус | `500` |
| тело | `" "`, `""`; отсутствующее тело — `<missing>` |
| заголовок | нет заголовка — `<missing>`, пустой список — `[]`, пустая строка в списке — `[""]`, несколько значений — `["a", "b"]` |
| поле JSON | `"42"` (строка), `42` (число), `true`, `null`; отсутствующее поле — `<missing>` |
| ожидание для заголовка | `exactly "text/html"`, `prefix "application/json"`, у `satisfies` — переданное описание |

От примера в условии (`expected prefix application/json, actual [text/plain]`) запись отличается кавычками. Без них `[]` и `[""]`, а также `"a, b"` и `["a", "b"]` выглядели бы одинаково, и пропали бы внешние пробелы.

### Порядок значений заголовка

Имена заголовков сравниваются без учёта регистра. Значения всех ключей, совпадающих с именем, объединяются **в порядке итерации `HttpResponse.headers`**, а внутри одного ключа идут в исходном порядке. Для `linkedMapOf("Accept" to ["a"], "ACCEPT" to ["b", "c"])` фактическое значение будет `["a", "b", "c"]`. Если подходящих ключей нет, это `<missing>`; если ключи есть, но значений нет, это `[]`. Сами значения сравниваются с учётом регистра, без обрезки пробелов и без разбиения по запятой.

## Как это устроено

### Две фазы: описание и выполнение

1. **Описание.** `responseSpec(block)` создаёт `ResponseChecks` и сразу один раз выполняет `block`. Каждая операция (`equalTo`, `startsWith`, `present` и т. д.) ничего не проверяет, а добавляет в список текущего блока *правило* — внутренний объект `Check<T>`. Вложенный блок (`status { }`, `json { }`, `field { }`) выполняется тут же, собирает свои правила и становится одним правилом родителя, которое при запуске достаёт свою часть ответа и передаёт её дочерним правилам. В конце список копируется в неизменяемый `List` внутри `ResponseSpec`.
2. **Выполнение.** `spec.validate(response)` создаёт новый `MismatchCollector`, по порядку прогоняет правила на этом ответе и возвращает `ValidationResult` с копией собранного списка.

`validate(response) { ... }` — это просто `responseSpec(block).validate(response)`.

**Распределение операций.** Каждое правило типизировано той частью ответа, которую проверяет: `Check<Int>` для статуса, `Check<List<String>?>` для заголовка (`null` — заголовка нет), `Check<String?>` для тела, `Check<Map<String, Any?>>` для JSON-объекта, `Check<FieldValue>` для поля. `FieldValue` — это `Missing` или `Present(value)`, поэтому `{"id": null}` и `{}` различаются: поле проверяется через `containsKey`. Получатель уровня ответа выбирает часть ответа: статус, значения заголовка через `headerValues(name)`, тело. Блок `json` разбирает тело через `parseJsonObject` при каждом запуске. Если тело `null` или не разбирается, он добавляет одно расхождение `body.json` и пропускает свои проверки полей, а остальные правила выполняются дальше. `notEmpty()` тело не разбирает.

**Порядок.** Правила выполняются в порядке записи, вложенный блок — в том месте, где он записан. Повторные вызовы дают отдельные правила, и одинаковые расхождения не схлопываются. `CheckList.addNested` резервирует место под вложенный блок *до* его выполнения, поэтому даже `this@validate.status { }` внутри `body { }` не сдвигает `body` назад.

**Пользовательский предикат.** `satisfies(expectation: String, predicate: (String) -> Boolean)` принимает обычное значение функционального типа. Подходит литерал лямбды, переменная типа `(String) -> Boolean` или ссылка на функцию (`::isNonBlank`). Предикат сохраняется внутри правила заголовка и вызывается только при `validate`, по значениям заголовка до первого `true` (`none(predicate)`). Если заголовка или значений нет, предикат не вызывается и добавляется одно расхождение.

### Независимость запусков

* Накопитель `MismatchCollector` создаётся заново для каждого вызова `validate`, а в результат попадает его копия (`toList()`).
* В правилах нет изменяемого состояния: они хранят только путь, ожидаемые значения и предикаты. Ответ передаётся параметром и после запуска нигде не остаётся.
* Входной `HttpResponse` только читается. Значения заголовков собираются в новый список (`flatMap`), так что результат не меняется, даже если потом изменить исходные коллекции.
* `ResponseSpec` получает неизменяемую копию правил. Если сохранить ссылку на `ResponseChecks` и вызвать его позже, на готовую спецификацию это не повлияет.
* `include(spec)` вставляет правила другой спецификации в текущее место. Так как правила не хранят состояния, одну общую спецификацию можно включить в несколько других, и ошибки между ними не переносятся. Повторный `include` добавляет правила ещё раз.

### Что хранится в спецификации, а что создаётся при запуске

| | Где живёт | Когда создаётся |
|---|---|---|
| `List<Check<HttpResponse>>` — дерево правил с путями, ожиданиями и предикатами | `ResponseSpec` | один раз, в `responseSpec { }` |
| `MismatchCollector` | локальная переменная `validate` | на каждый запуск |
| разобранный JSON (`Map<String, Any?>`) | локальная переменная правила `json` | на каждый запуск этого правила |
| `ValidationResult` | возвращается вызывающему | на каждый запуск |

### Какие лямбды выполняются сразу, а какие сохраняются

| Лямбда | Функциональный тип | Когда выполняется |
|---|---|---|
| блок `validate` / `responseSpec` | `ResponseChecks.() -> Unit` | сразу, один раз при создании спецификации |
| блок `status` | `StatusChecks.() -> Unit` | сразу, во время описания |
| блок `header` | `HeaderChecks.() -> Unit` | сразу, во время описания |
| блок `body` | `BodyChecks.() -> Unit` | сразу, во время описания |
| блок `json` | `JsonObjectChecks.() -> Unit` | сразу, во время описания |
| блок `field` | `JsonFieldChecks.() -> Unit` | сразу, во время описания |
| предикат `satisfies` | `(String) -> Boolean` | **сохраняется**, вызывается при `validate` |
| внутреннее правило `Check<T>` (`fun interface`, по сути `(T, MismatchCollector) -> Unit`) | `Check<T>` | **сохраняется**, вызывается при `validate` |

`inline`, `noinline` и `crossinline` здесь не нужны. Блоки настройки вызываются по одному разу на создание спецификации, так что встраивание ничего не даёт. Предикаты и правила нужно сохранять, а для этого у `inline`-функции их пришлось бы помечать `noinline`.

## check01–check06 через DSL

Новые версии лежат в `lesson09.checks`. Каждая описана один раз как `ResponseSpec` (`check01Spec` … `check06Spec`), а функции `checkNN(response): ValidationResult` её запускают.

| Функция | DSL |
|---|---|
| `check01` | `status { equalTo(204) }`, `body { absentOrEmpty() }` |
| `check02` | `status { equalTo(200) }`, `header("Content-Type") { startsWith("application/json") }`, `body { notEmpty() }` |
| `check03` | `status { equalTo(200) }`, `header("Accept") { hasValue("application/json"); hasValue("text/html") }` |
| `check04` | `status { equalTo(200) }`, `body { json { field("id") { present() }; field("name") { equalTo("Ivanov") } } }` |
| `check05` | `status { equalTo(200) }`, `body { json { field("id") { equalTo(42) }; field("active") { equalTo(true) } } }` |
| `check06` | `status { equalTo(422) }`, `header("Content-Type") { startsWith("application/json") }`, `body { json { field("error") { equalTo("invalid") }; field("retry") { equalTo(false) } } }` |

## Тесты

| Файл | Что проверяет |
|---|---|
| `LegacyComparisonTest` | для каждой из шести функций: подходящий ответ и ответы, где по отдельности нарушено каждое условие. Сравнивается `legacy(response) == result.isSuccess` и полный список расхождений (пути, ожидания, факты) |
| `DslExampleTest` | пример из условия, текст `render()`, запись путей, обращение к внешнему получателю через `this@validate` |
| `BodyAndJsonRulesTest` | тело `null` / `""` / `" "` / непустое; корректный и некорректный JSON, отсутствующее поле, JSON `null`, несовпадение типа, лишние поля; ошибка разбора вместе с ошибками статуса и заголовка |
| `HeaderRulesTest` | отсутствующий заголовок, `[]`, `[""]`; регистр имени; несколько ключей с разным регистром; разные проверки на разных значениях; регистр и внешние пробелы в значениях; лишние заголовки |
| `CustomPredicateTest` | один предикат тремя способами: литерал, переменная, ссылка на функцию; предикат не вызывается без значений |
| `RunIndependenceTest` | порядок нескольких независимых нарушений, повторные одинаковые проверки, блок настройки в переменной на ответах «подходящий → неподходящий → подходящий», сохранность входного ответа |
| `OptionalOperationsTest` | `inRange(200..299)` на 199/200/299/300, `isNull()` |
| `ResponseSpecTest` | блок настройки выполняется один раз; предикат не вызывается при создании; независимость результатов; общий набор правил в двух спецификациях; порядок `include` и повторное включение |
