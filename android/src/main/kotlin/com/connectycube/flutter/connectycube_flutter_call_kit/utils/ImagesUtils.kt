package com.connectycube.flutter.connectycube_flutter_call_kit.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextUtils

fun getPhotoPlaceholderResId(context: Context): Int {
    val customAvatarResName = getString(context.applicationContext, "icon")
    val defaultImgResId =
        context.resources.getIdentifier(
            "photo_placeholder",
            "drawable",
            context.packageName
        )

    return if (TextUtils.isEmpty(customAvatarResName)) {
        defaultImgResId
    } else {
        val avatarResourceId =
            context.resources.getIdentifier(
                customAvatarResName,
                "drawable",
                context.packageName
            )
        if (avatarResourceId != 0) {
            avatarResourceId
        } else {
            defaultImgResId
        }
    }
}

fun getCircleBitmap(bitmap: Bitmap): Bitmap {
    val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val paint = Paint()
    val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())

    paint.isAntiAlias = true
    paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

    canvas.drawRoundRect(rect, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
    return output
}

fun getDefaultPhoto(context: Context): Bitmap {
    return getCircleBitmap(
        BitmapFactory.decodeResource(
            context.resources,
            getPhotoPlaceholderResId(context)
        )
    )
}