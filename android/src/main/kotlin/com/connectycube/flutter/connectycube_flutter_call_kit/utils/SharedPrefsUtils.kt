package com.connectycube.flutter.connectycube_flutter_call_kit.utils

import android.content.Context
import android.content.SharedPreferences


private const val PREFERENCES_FILE_NAME = "connectycube_flutter_call_kit"
private const val DEFAULT_INT_VALUE = -1
private const val DEFAULT_LONG_VALUE: Long = -1L

private var preferences: SharedPreferences? = null
private var editor: SharedPreferences.Editor? = null


private fun initPreferences(context: Context) {
    preferences = context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)
    editor = preferences!!.edit()
}

fun putBoolean(context: Context, key: String, value: Boolean) {
    initPreferences(context)
    editor!!.putBoolean(key, value)
    editor!!.commit()
}

fun getBoolean(context: Context, key: String): Boolean {
    initPreferences(context)
    return preferences!!.getBoolean(key, false)
}

fun putString(context: Context, key: String, value: String?) {
    initPreferences(context)
    editor!!.putString(key, value)
    editor!!.commit()
}

fun getString(context: Context, key: String): String? {
    initPreferences(context)
    return preferences!!.getString(key, "")
}


fun putInt(context: Context, key: String, value: Int) {
    initPreferences(context)
    editor!!.putInt(key, value)
    editor!!.commit()
}

fun getInt(context: Context, key: String): Int {
    initPreferences(context)
    return preferences!!.getInt(key, DEFAULT_INT_VALUE)
}

fun putLong(context: Context, key: String, value: Long) {
    initPreferences(context)
    editor!!.putLong(key, value)
    editor!!.commit()
}

fun getLong(context: Context, key: String): Long {
    initPreferences(context)
    return preferences!!.getLong(key, DEFAULT_LONG_VALUE)
}

fun putDouble(context: Context, key: String, value: Double) {
    initPreferences(context)
    editor!!.putString(key, value.toString())
    editor!!.commit()
}

fun getDouble(context: Context, key: String): Double {
    initPreferences(context)
    val value = preferences!!.getString(key, "")
    return if (value == "") {
        0.0
    } else value!!.toDouble()
}

fun remove(context: Context, key: String) {
    initPreferences(context)
    editor!!.remove(key)
    editor!!.commit()
}