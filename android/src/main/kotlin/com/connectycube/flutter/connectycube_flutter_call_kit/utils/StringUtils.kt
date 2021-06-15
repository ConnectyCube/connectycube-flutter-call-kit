package com.connectycube.flutter.connectycube_flutter_call_kit.utils

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import org.json.JSONArray
import org.json.JSONObject
import java.lang.Exception
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

fun getStringResource(context: Context, name: String?): String? {
    return context.getString(
        context.resources.getIdentifier(
            name, "string", context.packageName
        )
    )
}

fun getColorizedText(string: String, colorHex: String): Spannable {
    val spannable: Spannable = SpannableString(string)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
        spannable.setSpan(
            ForegroundColorSpan(Color.parseColor(colorHex)),
            0,
            spannable.length,
            Spanned.SPAN_INCLUSIVE_EXCLUSIVE
        )
    }
    return spannable
}

fun getMD5forString(source: String): String? {
    var result: String? = null
    try {
        // Create MD5 Hash
        val digest = MessageDigest.getInstance("MD5")
        digest.update(source.toByteArray())
        val messageDigest = digest.digest()

        // Create Hex String
        val hexString = StringBuilder()
        for (b in messageDigest) {
            var h = Integer.toHexString(0xFF and b.toInt())
            while (h.length < 2) {
                h = "0$h"
            }
            hexString.append(h)
        }
        result = hexString.toString()
    } catch (e: NoSuchAlgorithmException) {
        e.printStackTrace()
    }
    return result
}

fun mapToJsonString(map: Map<String, *>): String? {
    return try {
        JSONObject(map).toString()
    } catch (e: Exception) {
        null
    }
}

fun getMapFromJsonString(json: String): Map<String, String>? {
    if (TextUtils.isEmpty(json)) return null

    val result: Map<String, String>?

    try {
        val jsonObj = JSONObject(json)
        result = jsonObj.toMap().mapValues {
            it.value.toString()
        }
    } catch (e: Exception) {
        return null
    }

    return result
}

fun JSONObject.toMap(): Map<String, *> = keys().asSequence().associateWith { it ->
    when (val value = this[it]) {
        is JSONArray -> {
            val map = (0 until value.length()).associate { Pair(it.toString(), value[it]) }
            JSONObject(map).toMap().values.toList()
        }
        is JSONObject -> value.toMap()
        JSONObject.NULL -> null
        else -> value
    }
}