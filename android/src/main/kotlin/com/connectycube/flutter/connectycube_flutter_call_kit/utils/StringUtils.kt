package com.connectycube.flutter.connectycube_flutter_call_kit.utils

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
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
