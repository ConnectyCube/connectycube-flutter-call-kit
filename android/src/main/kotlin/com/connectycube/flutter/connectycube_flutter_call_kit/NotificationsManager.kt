package com.connectycube.flutter.connectycube_flutter_call_kit

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.connectycube.flutter.connectycube_flutter_call_kit.utils.getColorizedText

const val CALL_CHANNEL_ID = "calls_channel_id"
const val CALL_CHANNEL_NAME = "Calls"


fun cancelCallNotification(context: Context, callId: String) {
    val notificationManager = NotificationManagerCompat.from(context)
    notificationManager.cancel(callId.hashCode())
}

fun showCallNotification(context: Context, callId: String, callType: Int, callInitiatorId: Int, callInitiatorName: String) {
    val notificationManager = NotificationManagerCompat.from(context)

    val intent = getLaunchIntent(context)
//    intent!!.action = "SELECT_NOTIFICATION"
//    intent.putExtra("payload", notificationDetails.payload)

    val pendingIntent = PendingIntent.getActivity(context, callId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT)

    val ringtone: Uri = RingtoneManager.getActualDefaultRingtoneUri(context.applicationContext, RingtoneManager.TYPE_RINGTONE)

    val callTypeTitle = String.format(CALL_TYPE_PLACEHOLDER, if (callType == 1) "Video" else "Audio")

    val builder: NotificationCompat.Builder = createCallNotification(context, callInitiatorName, callTypeTitle, pendingIntent, ringtone)

    // Add actions
    addCallRejectAction(context, builder, callId, callType, callInitiatorId, callInitiatorName)
    addCallAcceptAction(context, builder, callId, callType, callInitiatorId, callInitiatorName)

    // Add full screen intent (to show on lock screen)
    addCallFullScreenIntent(context, builder, callId, callType, callInitiatorId, callInitiatorName)

    // Add action when delete call notification
    addCancelCallNotificationIntent(context, builder, callId, callType, callInitiatorId, callInitiatorName)

    builder.setSmallIcon(context.applicationInfo.icon)

    createCallNotificationChannel(notificationManager, ringtone)

//    context.startActivity(intent)
    notificationManager.notify(callId.hashCode(), builder.build())

//    NotificationCreator.setNotificationSmallIcon(activityOrServiceContext, notificationBuilder);
//    NotificationCreator.setNotificationColor(activityOrServiceContext, notificationBuilder);


}

fun getLaunchIntent(context: Context): Intent? {
    val packageName = context.packageName
    val packageManager: PackageManager = context.packageManager
    return packageManager.getLaunchIntentForPackage(packageName)
}

fun createCallNotification(context: Context, title: String, text: String?, pendingIntent: PendingIntent, ringtone: Uri): NotificationCompat.Builder {
    val notificationBuilder = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
    notificationBuilder
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setContentTitle(title)
            .setContentText(text)
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

fun addCallRejectAction(context: Context, notificationBuilder: NotificationCompat.Builder,
                        callId: String, callType: Int, callInitiatorId: Int, callInitiatorName: String) {
    val bundle = Bundle()
    bundle.putString(EXTRA_CALL_ID, callId)
    bundle.putInt(EXTRA_CALL_TYPE, callType)
    bundle.putInt(EXTRA_CALL_INITIATOR_ID, callInitiatorId)
    bundle.putString(EXTRA_CALL_INITIATOR_NAME, callInitiatorName)

    val declinePendingIntent: PendingIntent = PendingIntent.getBroadcast(
            context,
            callId.hashCode(),
            Intent(context, EventReceiver::class.java)
                    .setAction(ACTION_CALL_REJECT)
                    .putExtras(bundle),
            PendingIntent.FLAG_UPDATE_CURRENT)
    val declineAction: NotificationCompat.Action = NotificationCompat.Action.Builder(
            R.drawable.ic_menu_close_clear_cancel,
            getColorizedText("Reject", "#E02B00"),
            declinePendingIntent)
            .build()

    notificationBuilder.addAction(declineAction)
}

fun addCallAcceptAction(context: Context, notificationBuilder: NotificationCompat.Builder,
                        callId: String, callType: Int, callInitiatorId: Int, callInitiatorName: String) {
    val bundle = Bundle()
    bundle.putString(EXTRA_CALL_ID, callId)
    bundle.putInt(EXTRA_CALL_TYPE, callType)
    bundle.putInt(EXTRA_CALL_INITIATOR_ID, callInitiatorId)
    bundle.putString(EXTRA_CALL_INITIATOR_NAME, callInitiatorName)

    val acceptPendingIntent: PendingIntent = PendingIntent.getBroadcast(
            context,
            callId.hashCode(),
            Intent(context, EventReceiver::class.java)
                    .setAction(ACTION_CALL_ACCEPT)
                    .putExtras(bundle),
            PendingIntent.FLAG_UPDATE_CURRENT)
    val acceptAction: NotificationCompat.Action = NotificationCompat.Action.Builder(
            R.drawable.ic_menu_call,
            getColorizedText("Accept", "#4CB050"),
            acceptPendingIntent)
            .build()
    notificationBuilder.addAction(acceptAction)
}

fun addCallFullScreenIntent(
        context: Context, notificationBuilder: NotificationCompat.Builder,
        callId: String, callType: Int, callInitiatorId: Int, callInitiatorName: String) {
    val callFullScreenIntent: Intent = createStartIncomingScreenIntent(context, callId, callType, callInitiatorId, callInitiatorName)
    val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            callId.hashCode(),
            callFullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT)
    notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
}

fun addCancelCallNotificationIntent(appContext: Context?, notificationBuilder: NotificationCompat.Builder, callId: String, callType: Int, callInitiatorId: Int, callInitiatorName: String) {
    val bundle = Bundle()
    bundle.putString(EXTRA_CALL_ID, callId)
    bundle.putInt(EXTRA_CALL_TYPE, callType)
    bundle.putInt(EXTRA_CALL_INITIATOR_ID, callInitiatorId)
    bundle.putString(EXTRA_CALL_INITIATOR_NAME, callInitiatorName)

    val deleteCallNotificationPendingIntent = PendingIntent.getBroadcast(
            appContext,
            callId.hashCode(),
            Intent(appContext, EventReceiver::class.java)
                    .setAction(ACTION_CALL_NOTIFICATION_CANCELED)
                    .putExtras(bundle),
            PendingIntent.FLAG_UPDATE_CURRENT)
    notificationBuilder.setDeleteIntent(deleteCallNotificationPendingIntent)
}


private fun createCallNotificationChannel(notificationManager: NotificationManagerCompat, sound: Uri) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(CALL_CHANNEL_ID, CALL_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
        channel.setSound(sound, AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build())
        notificationManager.createNotificationChannel(channel)
    }
}
