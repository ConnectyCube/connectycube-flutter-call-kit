package com.connectycube.flutter.connectycube_flutter_call_kit

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ConnectycubeFCMService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        if(data.containsKey("signal_type")){
            when (data["signal_type"]) {
                "startCall" -> {


                }

                "endCall" ->{

                }

                "rejectCall" -> {

                }
            }

        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)


    }
}