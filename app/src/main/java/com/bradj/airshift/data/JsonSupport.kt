package com.bradj.airshift.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/**
 * 排班 JSON 与 MUC 状态 JSON 共用的空值约定：写入时 null 落成 `JSONObject.NULL`，
 * 读取时缺键、`null` 与空白字符串一律视为 null；损坏的数组元素逐项跳过。
 */
internal fun JSONObject.putNullable(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}

internal fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

internal fun JSONObject.nullableInt(key: String): Int? = if (isNull(key)) null else getInt(key)

internal fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)

internal fun JSONObject.nullableDateTime(key: String): LocalDateTime? =
    nullableString(key)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

internal inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            runCatching { transform(getJSONObject(index)) }.getOrNull()?.let(::add)
        }
    }
}
