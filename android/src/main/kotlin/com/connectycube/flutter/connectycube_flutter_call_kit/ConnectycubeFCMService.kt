package com.connectycube.flutter.connectycube_flutter_call_kit

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ConnectycubeFCMService : FirebaseMessagingService() {
    private val TAG = "ConnectycubeFCMService"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        // Added for commenting purposes;
        // We don't handle the message here as we already handle it in the receiver and don't want to duplicate.
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "onNewToken")

        LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(Intent(ACTION_TOKEN_REFRESHED).putExtra(EXTRA_PUSH_TOKEN, token))
    }
}