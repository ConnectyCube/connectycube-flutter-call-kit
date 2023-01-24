package com.connectycube.flutter.connectycube_flutter_call_kit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.connectycube.flutter.connectycube_flutter_call_kit.utils.ContextHolder
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject


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
        val callId = data["session_id"] ?: return


        processCallEnded(applicationContext, callId)
    }

    private fun processInviteCallEvent(applicationContext: Context, data: Map<String, String>) {
        val callId = data["session_id"]

        if (callId == null || CALL_STATE_UNKNOWN != getCallState(
                applicationContext,
                callId
            )
        ) {
            return
        }

        val callType = data["call_type"]
        val callInitiatorId = data["caller_id"]
        val callInitiatorName = data["caller_name"]
        val callOpponentsString = data["call_opponents"]
        var callOpponents = ArrayList<Int>()
        if (callOpponentsString != null) {
            callOpponents = ArrayList(callOpponentsString.split(',').map { it.toInt() })
        }

        val userInfo = data["user_info"] ?: JSONObject(emptyMap<String, String>()).toString()

        if (callType == null || callInitiatorId == null || callInitiatorName == null || callOpponents.isEmpty()) {
            return
        }

        showCallNotification(
            applicationContext,
            callId,
            callType,
            callInitiatorId,
            callInitiatorName,
            callOpponents,
            userInfo
        )

        saveCallState(applicationContext, callId, CALL_STATE_PENDING)
        saveCallData(applicationContext, callId, data)
        saveCallId(applicationContext, callId)
    }
}