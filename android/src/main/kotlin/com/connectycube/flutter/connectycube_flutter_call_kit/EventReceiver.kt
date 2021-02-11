package com.connectycube.flutter.connectycube_flutter_call_kit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.flutter.Log


class EventReceiver : BroadcastReceiver() {
    val TAG = "EventReceiver"
    override fun onReceive(context: Context, intent: Intent?) {

        if (intent == null || TextUtils.isEmpty(intent.action)) return

        when (intent.action) {
            ACTION_CALL_REJECT -> {
                val extras = intent.extras
                val callId = extras?.getString(EXTRA_CALL_ID)
                val callType = extras?.getInt(EXTRA_CALL_TYPE)
                val callInitiatorId = extras?.getInt(EXTRA_CALL_INITIATOR_ID)
                val callInitiatorName = extras?.getString(EXTRA_CALL_INITIATOR_NAME)
                Log.i(TAG, "NotificationReceiver onReceive Call REJECT, callId: $callId")
                LocalBroadcastManager.getInstance(context.applicationContext)
                        .sendBroadcast(Intent(ACTION_CALL_REJECT).putExtra(EXTRA_CALL_ID, callId))

                NotificationManagerCompat.from(context).cancel(callId.hashCode())
            }
            ACTION_CALL_ACCEPT -> {
                val extras = intent.extras
                val callId = extras?.getString(EXTRA_CALL_ID)
                val callType = extras?.getInt(EXTRA_CALL_TYPE)
                val callInitiatorId = extras?.getInt(EXTRA_CALL_INITIATOR_ID)
                val callInitiatorName = extras?.getString(EXTRA_CALL_INITIATOR_NAME)
                Log.i(TAG, "NotificationReceiver onReceive Call ACCEPT, callId: $callId")

//                val launchIntent = Intent(context!!.applicationContext, OnNotificationOpenReceiver::class.java)
//                val bundle = Bundle()
//                bundle.putString(NotificationCreator.VNC_PEER_JID, callId)
//                bundle.putString(NotificationCreator.VNC_INITIATOR_JID, callInitiator)
//                bundle.putString("vncEventType", callType)
//                bundle.putInt(NotificationCreator.NOTIFY_ID, callNotificationId)
//                bundle.putString(NotificationUtils.EXTRA_CALL_ACTION, NotificationCreator.TALK_CALL_ACCEPT)
//                bundle.putString(NotificationUtils.EXTRA_CALL_JITSI_ROOM, jitsiRoom)
//                bundle.putString(NotificationUtils.EXTRA_CALL_JITSI_URL, jitsiUrl)
//                launchIntent.putExtras(bundle)
//                dismissAnotherCalls(context, callId)
//                context.sendBroadcast(launchIntent)

                LocalBroadcastManager.getInstance(context.applicationContext)
                        .sendBroadcast(Intent(ACTION_CALL_ACCEPT).putExtra(EXTRA_CALL_ID, callId))

                NotificationManagerCompat.from(context).cancel(callId.hashCode())


                context.startActivity(getLaunchIntent(context))
            }
            ACTION_CALL_NOTIFICATION_CANCELED -> {
                val extras = intent.extras
                val callId = extras?.getString(EXTRA_CALL_ID)
                val callType = extras?.getInt(EXTRA_CALL_TYPE)
                val callInitiatorId = extras?.getInt(EXTRA_CALL_INITIATOR_ID)
                val callInitiatorName = extras?.getString(EXTRA_CALL_INITIATOR_NAME)
                Log.i(TAG, "NotificationReceiver onReceive Delete Call Notification, callId: $callId")
                LocalBroadcastManager.getInstance(context.applicationContext)
                        .sendBroadcast(Intent(ACTION_CALL_NOTIFICATION_CANCELED).putExtra(EXTRA_CALL_ID, callId))
            }
        }
    }
}