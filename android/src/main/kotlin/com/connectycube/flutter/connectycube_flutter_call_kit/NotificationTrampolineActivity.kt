package com.connectycube.flutter.connectycube_flutter_call_kit

import android.app.Activity
import android.os.Bundle
import android.content.Intent

class NotificationTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == ACTION_CALL_ACCEPT) {
            val startCallIntent = Intent(this, EventReceiver::class.java)
            startCallIntent.action = ACTION_CALL_ACCEPT
            startCallIntent.putExtras(intent.extras!!)
            applicationContext.sendBroadcast(startCallIntent)
        }
        finishAndRemoveTask()
    }
}