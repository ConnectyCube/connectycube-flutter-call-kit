package com.connectycube.flutter.connectycube_flutter_call_kit

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.connectycube.flutter.connectycube_flutter_call_kit.utils.getDefaultPhoto
import com.connectycube.flutter.connectycube_flutter_call_kit.utils.getPhotoPlaceholderResId
import com.connectycube.flutter.connectycube_flutter_call_kit.utils.getString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val CALL_CHANNEL_ID = "calls_channel_id"
const val CALL_CHANNEL_NAME = "Calls"


fun cancelCallNotification(context: Context, callId: String) {
    val notificationManager = NotificationManagerCompat.from(context)
    notificationManager.cancel(callId.hashCode())
}

fun showCallNotification(
    context: Context, callId: String, callType: Int, callInitiatorId: Int,
    callInitiatorName: String, callOpponents: ArrayList<Int>, callPhoto: String?, userInfo: String
) {
    Log.d("NotificationsManager", "[showCallNotification]")
    val notificationManager = NotificationManagerCompat.from(context)

    Log.d(
        "NotificationsManager",
        "[showCallNotification] canUseFullScreenIntent: ${notificationManager.canUseFullScreenIntent()}"
    )

    val intent = getLaunchIntent(context)

    val pendingIntent = PendingIntent.getActivity(
        context,
        callId.hashCode(),
        intent,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT

    )

    var ringtone: Uri

    val customRingtone = getString(context, "ringtone")
    if (!TextUtils.isEmpty(customRingtone)) {
        ringtone = Uri.parse("android.resource://" + context.packageName + "/raw/" + customRingtone)
    } else {
        ringtone = Settings.System.DEFAULT_RINGTONE_URI
    }

    Log.d("NotificationsManager", "ringtone: $ringtone")

    val isVideoCall = callType == 1

    val callTypeTitle =
        String.format(CALL_TYPE_PLACEHOLDER, if (isVideoCall) "Video" else "Audio")

    val callData = Bundle()
    callData.putString(EXTRA_CALL_ID, callId)
    callData.putInt(EXTRA_CALL_TYPE, callType)
    callData.putInt(EXTRA_CALL_INITIATOR_ID, callInitiatorId)
    callData.putString(EXTRA_CALL_INITIATOR_NAME, callInitiatorName)
    callData.putIntegerArrayList(EXTRA_CALL_OPPONENTS, callOpponents)
    callData.putString(EXTRA_CALL_PHOTO, callPhoto)
    callData.putString(EXTRA_CALL_USER_INFO, userInfo)

    val defaultPhoto = getDefaultPhoto(context)

    val builder: NotificationCompat.Builder =
        createCallNotification(
            context,
            callInitiatorName,
            callTypeTitle,
            pendingIntent,
            ringtone,
            isVideoCall,
            callData
        )

    // Add full screen intent (to show on lock screen)
    addCallFullScreenIntent(
        context,
        builder,
        callId,
        callType,
        callInitiatorId,
        callInitiatorName,
        callOpponents,
        callPhoto,
        userInfo
    )

    // Add action when delete call notification
    addCancelCallNotificationIntent(
        context,
        builder,
        callId.hashCode(),
        callData
    )

    // Set small icon for notification
    setNotificationSmallIcon(context, builder, isVideoCall)

    // Set notification color accent
    setNotificationColor(context, builder)

    createCallNotificationChannel(notificationManager, ringtone)

    if (TextUtils.isEmpty(callPhoto)) {
        setNotificationLargeIcon(builder, defaultPhoto)
        postNotification(callId.hashCode(), notificationManager, builder)
    } else {
        loadPhotoAndPostNotification(
            context,
            notificationManager,
            builder,
            callId.hashCode(),
            callPhoto!!,
            defaultPhoto
        )
    }
}

fun postNotification(
    notificationId: Int,
    notificationManager: NotificationManagerCompat,
    builder: NotificationCompat.Builder
) {
    val notification = builder.build()
    notification.flags = notification.flags or NotificationCompat.FLAG_INSISTENT
    notificationManager.notify(notificationId, notification)
}

fun loadPhotoAndPostNotification(
    context: Context,
    notificationManager: NotificationManagerCompat,
    builder: NotificationCompat.Builder,
    notificationId: Int,
    photoUrl: String,
    defaultPhoto: Bitmap
) {
    CoroutineScope(Dispatchers.IO).launch {
        val photoPlaceholder = getPhotoPlaceholderResId(context)

        if (!TextUtils.isEmpty(photoUrl)) {
            val futureTarget = Glide.with(context)
                .asBitmap()
                .load(photoUrl)
                .transform(CircleCrop())
                .error(photoPlaceholder)
                .placeholder(photoPlaceholder)
                .submit()

            try {
                val bitmap = futureTarget.get()
                builder.setLargeIcon(bitmap)

                Glide.with(context).clear(futureTarget)

                postNotification(notificationId, notificationManager, builder)
            } catch (e: Exception) {
                builder.setLargeIcon(defaultPhoto)
                postNotification(notificationId, notificationManager, builder)
            }
        } else {
            builder.setLargeIcon(defaultPhoto)
            postNotification(notificationId, notificationManager, builder)
        }
    }
}

fun getLaunchIntent(context: Context): Intent? {
    val packageName = context.packageName
    val packageManager: PackageManager = context.packageManager
    return packageManager.getLaunchIntentForPackage(packageName)
}

fun createCallNotification(
    context: Context,
    title: String,
    callName: String?,
    pendingIntent: PendingIntent,
    ringtone: Uri,
    isVideoCall: Boolean,
    callData: Bundle
): NotificationCompat.Builder {
    val person = Person.Builder()
        .setName(title)
        .setImportant(true)
        .build()

    val style = NotificationCompat.CallStyle.forIncomingCall(
        person,
        getRejectCallIntent(context, callData, title.hashCode()),
        getAcceptCallIntent(context, callData, title.hashCode())
    )
    style.setIsVideo(isVideoCall)

    val notificationBuilder = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
    notificationBuilder
        .setContentText(callName)
        .setStyle(style)
        .addPerson(person)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setAutoCancel(true)
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setContentIntent(pendingIntent)
        .setSound(ringtone)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setTimeoutAfter(60000)
    return notificationBuilder
}

fun getAcceptCallIntent(
    context: Context,
    callData: Bundle,
    requestCode: Int
): PendingIntent {
    return PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, EventReceiver::class.java)
            .setAction(ACTION_CALL_ACCEPT)
            .putExtras(callData),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
    )
}

fun getRejectCallIntent(
    context: Context,
    callData: Bundle,
    requestCode: Int
): PendingIntent {
    return PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, EventReceiver::class.java)
            .setAction(ACTION_CALL_REJECT)
            .putExtras(callData),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
    )
}

fun addCallFullScreenIntent(
    context: Context,
    notificationBuilder: NotificationCompat.Builder,
    callId: String,
    callType: Int,
    callInitiatorId: Int,
    callInitiatorName: String,
    callOpponents: ArrayList<Int>,
    callPhoto: String?,
    userInfo: String
) {
    val callFullScreenIntent: Intent = createStartIncomingScreenIntent(
        context,
        callId,
        callType,
        callInitiatorId,
        callInitiatorName,
        callOpponents,
        callPhoto,
        userInfo
    )
    val fullScreenPendingIntent = PendingIntent.getActivity(
        context,
        callId.hashCode(),
        callFullScreenIntent,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
    )
    notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
}

fun addCancelCallNotificationIntent(
    appContext: Context?,
    notificationBuilder: NotificationCompat.Builder,
    requestCode: Int,
    callData: Bundle
) {

    val deleteCallNotificationPendingIntent = PendingIntent.getBroadcast(
        appContext,
        requestCode,
        Intent(appContext, EventReceiver::class.java)
            .setAction(ACTION_CALL_NOTIFICATION_CANCELED)
            .putExtras(callData),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
    )
    notificationBuilder.setDeleteIntent(deleteCallNotificationPendingIntent)
}

fun createCallNotificationChannel(notificationManager: NotificationManagerCompat, sound: Uri) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CALL_CHANNEL_ID,
            CALL_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.setSound(
            sound, AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()
        )
        channel.enableVibration(true)
        channel.lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        notificationManager.createNotificationChannel(channel)
    }
}

fun setNotificationSmallIcon(
    context: Context,
    notificationBuilder: NotificationCompat.Builder,
    isVideoCall: Boolean
) {
    val appMetadata = context.packageManager.getApplicationInfo(
        context.packageName,
        PackageManager.GET_META_DATA
    ).metaData

    var iconId =
        if (isVideoCall) appMetadata.getInt("com.connectycube.flutter.connectycube_flutter_call_kit.video_call_notification_icon") else appMetadata.getInt(
            "com.connectycube.flutter.connectycube_flutter_call_kit.audio_call_notification_icon"
        )
    if (iconId == 0) {
        iconId =
            appMetadata.getInt("com.connectycube.flutter.connectycube_flutter_call_kit.app_notification_icon")
    }

    if (iconId == 0) {
        try {
            val customIconOld = getString(context, "notification_icon")
            iconId = context.resources.getIdentifier(customIconOld, "drawable", context.packageName)
        } catch (e: Exception) {
            iconId = context.applicationInfo.icon
        }
    }

    if (iconId == 0) {
        iconId = context.applicationInfo.icon
    }

    notificationBuilder.setSmallIcon(iconId)
}

fun setNotificationLargeIcon(
    notificationBuilder: NotificationCompat.Builder,
    largeIcon: Bitmap
) {
    notificationBuilder.setLargeIcon(largeIcon)
}

fun setNotificationColor(context: Context, notificationBuilder: NotificationCompat.Builder) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val color = getString(context, "color")

        if (!TextUtils.isEmpty(color)) {
            notificationBuilder.color = Color.parseColor(color)
        } else {
            val accentID = context.resources.getIdentifier(
                "call_notification_color_accent",
                "color",
                context.packageName
            )
            if (accentID != 0) {
                notificationBuilder.color = context.resources.getColor(accentID, null)
            } else {
                notificationBuilder.color = Color.parseColor("#4CAF50")
            }
        }
    }
}

fun canUseFullScreenIntent(context: Context): Boolean {
    return NotificationManagerCompat.from(context).canUseFullScreenIntent()
}
