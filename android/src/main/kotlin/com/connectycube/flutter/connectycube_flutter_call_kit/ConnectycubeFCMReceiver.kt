package com.connectycube.flutter.connectycube_flutter_call_kit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.connectycube.flutter.connectycube_flutter_call_kit.utils.ContextHolder
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import android.os.Bundle


class ConnectycubeFCMReceiver : BroadcastReceiver() {
    private val TAG = "ConnectycubeFCMReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "broadcast received for message")

        ContextHolder.applicationContext = context!!.applicationContext

        if (intent!!.extras == null) {
            Log.d(
                TAG,
                "broadcast received but intent contained no extras to process RemoteMessage. Operation cancelled."
            )
            return
        }

        val remoteMessage = RemoteMessage(intent.extras!!)

        val data = remoteMessage.data
        if (data.containsKey("signal_type")) {
            when (data["signal_type"]) {
                "startCall" -> {
                    processInviteCallEvent(context.applicationContext, data)
                }

                "endCall" -> {
                    processEndCallEvent(context.applicationContext, data)
                }

                "rejectCall" -> {
                    processEndCallEvent(context.applicationContext, data)
                }
            }

        }
    }

    private fun processEndCallEvent(applicationContext: Context, data: Map<String, String>) {
        Log.d(TAG, "[processEndCallEvent]")

        val callId = data["session_id"] ?: return


        processCallEnded(applicationContext, callId)
    }

    private fun processInviteCallEvent(applicationContext: Context, data: Map<String, String>) {
        Log.d(TAG, "[processInviteCallEvent]")
        val callId = data["session_id"]

        if (callId == null || CALL_STATE_UNKNOWN != getCallState(
                applicationContext,
                callId
            )
        ) {
            Log.d(TAG, "[processInviteCallEvent] callId == null || CALL_STATE_UNKNOWN != getCallState(applicationContext, callId)")
            return
        }

        val callType = data["call_type"]?.toInt()
        val callInitiatorId = data["caller_id"]?.toInt()
        val callInitiatorName = data["caller_name"]
        val callPhoto = data["photo_url"]
        val callOpponentsString = data["call_opponents"]
        var callOpponents = ArrayList<Int>()
        if (callOpponentsString != null) {
            callOpponents = ArrayList(callOpponentsString.split(',').map { it.toInt() })
        }
        val userInfo = data["user_info"] ?: JSONObject(emptyMap<String, String>()).toString()

        if (callType == null || callInitiatorId == null || callInitiatorName == null || callOpponents.isEmpty()) {
            Log.d(TAG, "[processInviteCallEvent] callType == null || callInitiatorId == null || callInitiatorName == null || callOpponents.isEmpty()")
            return
        }

        if (data["signal_type"] == "startCall") {
            Log.d(TAG, "[processInviteCallEvent] data[signal_type] == startCall")
            // val callData = new Bundle()
            // callData.putString("extra_call_id", callId)
            // callData.putInt(EXTRA_CALL_TYPE, callType)
            // callData.putInt(EXTRA_CALL_INITIATOR_ID, callInitiatorId)
            // callData.putString(EXTRA_CALL_INITIATOR_NAME, callInitiatorName)
            // callData.putIntegerArrayList(EXTRA_CALL_OPPONENTS, callOpponents)
            // callData.putString(EXTRA_CALL_PHOTO, callPhoto)
            // callData.putString(EXTRA_CALL_USER_INFO, userInfo)

            LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(Intent(ACTION_CALL_INCOMING).putExtra(EXTRA_CALL_ID, callId))

            Log.d(TAG, "[processInviteCallEvent] ACTION_CALL_INCOMING sent")
        }

        showCallNotification(
            applicationContext,
            callId,
            callType,
            callInitiatorId,
            callInitiatorName,
            callOpponents,
            callPhoto,
            userInfo
        )

        saveCallState(applicationContext, callId, CALL_STATE_PENDING)
        saveCallData(applicationContext, callId, data)
        saveCallId(applicationContext, callId)
    }
}