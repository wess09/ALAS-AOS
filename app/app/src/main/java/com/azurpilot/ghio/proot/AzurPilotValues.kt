package com.azurpilot.ghio.proot

import org.json.JSONArray
import org.json.JSONObject

/**
 * 网关 JSON 值树在 Kotlin 侧的表示
 *
 * 配置参数的值域是任意 JSON（`Scalar | List | Map`），`Any?` 是唯一能如实装下它的类型：
 * 定义成 `String` 或 `Double` 都会在存 `storage` 那种嵌套字典时丢东西。各处一律用它，
 * 不再各页自己 `JSONObject` 来回倒。
 */
typealias ApValue = Any?

/** JSON 里的对象/数组在 Kotlin 侧分别是 `Map`/`List`，标量原样 */
fun JSONObject.toValueMap(): Map<String, ApValue> {
    val result = LinkedHashMap<String, ApValue>(length())
    keys().forEach { key -> result[key] = opt(key).asApValue() }
    return result
}

fun JSONArray.toValueList(): List<ApValue> = List(length()) { index -> opt(index).asApValue() }

/**
 * 取字符串字段；显式 null、缺失、空串一律给 null
 *
 * **不能用 `optString`**：网关对可空字段发的是 JSON `null`，而 `optString` 走的是
 * `JSONObject.NULL.toString()`，会老老实实返回字面量 `"null"`——那两个字会原样显示到界面上。
 */
fun JSONObject.optText(key: String): String? = when (val value = opt(key)) {
    null, JSONObject.NULL -> null
    is String -> value.ifEmpty { null }
    else -> value.toString().ifEmpty { null }
}

/** 递归把 org.json 的类型摊成 Kotlin 类型；`JSONObject.NULL` 收成 `null` */
fun Any?.asApValue(): ApValue = when (this) {
    null, JSONObject.NULL -> null
    is JSONObject -> toValueMap()
    is JSONArray -> toValueList()
    is Number, is Boolean, is String -> this
    else -> toString()
}

/**
 * 反向：把 Kotlin 值树压成 org.json 能原样序列化的类型
 *
 * `JSONObject.put(key, null)` 是**删键**而不是写 null，所以空值必须显式换成
 * [JSONObject.NULL]，否则 `config.patch` 里「把参数清空」会变成「这个参数没提交」。
 */
fun ApValue.toJsonCompatible(): ApValue = when (this) {
    null -> JSONObject.NULL
    is Map<*, *> -> JSONObject().apply {
        forEach { (key, value) -> put(key.toString(), value.toJsonCompatible()) }
    }

    is Iterable<*> -> JSONArray().apply { forEach { put(it.toJsonCompatible()) } }
    is Array<*> -> JSONArray().apply { forEach { put(it.toJsonCompatible()) } }
    else -> this
}

/** 值的稳定字符串形式：做相等比较、做下拉框 item key 都用它 */
fun ApValue.stableKey(): String = when (this) {
    null -> "null"
    is String -> "\"$this\""
    is List<*> -> joinToString(prefix = "[", postfix = "]") { it.stableKey() }
    is Map<*, *> -> entries.joinToString(prefix = "{", postfix = "}") { "${it.key}:${it.value.stableKey()}" }
    else -> toString()
}

/**
 * 嵌套值（`storage` / `state` / `lock`）的展示文本
 *
 * 这几个类型在界面上是只读的，显示成 pretty JSON 比 `{a=1, b=2}` 那种 `toString()` 好读，
 * 也便于用户抄出来。标量原样返回。
 */
fun ApValue.prettyText(): String = when (this) {
    null -> ""
    is String -> this
    is Number, is Boolean -> toString()
    else -> runCatching { (JSONObject.wrap(this) as? JSONObject)?.toString(2) }.getOrNull() ?: toString()
}

/** 按网关的类型规则比对两个值：数字不分 Int/Double，但 `1` 与 `true` 不相等 */
fun ApValue.sameValueAs(other: ApValue): Boolean {
    if (this is Number && other is Number) return toDouble() == other.toDouble()
    if (this is Map<*, *> || other is Map<*, *>) return stableKey() == other.stableKey()
    if (this is List<*> || other is List<*>) return stableKey() == other.stableKey()
    return this == other
}

/** 网关返回的参数树：任务 → 分组 → 参数 → 值 */
typealias ApConfigValues = Map<String, Map<String, Map<String, ApValue>>>

fun ApConfigValues.task(task: String): Map<String, Map<String, ApValue>> = this[task].orEmpty()

@Suppress("UNCHECKED_CAST")
fun JSONObject.toConfigValues(): ApConfigValues {
    val result = LinkedHashMap<String, Map<String, Map<String, ApValue>>>()
    keys().forEach { task ->
        val groups = optJSONObject(task) ?: return@forEach
        val groupMap = LinkedHashMap<String, Map<String, ApValue>>()
        groups.keys().forEach { group ->
            val args = groups.optJSONObject(group) ?: return@forEach
            groupMap[group] = args.toValueMap()
        }
        result[task] = groupMap
    }
    return result
}
