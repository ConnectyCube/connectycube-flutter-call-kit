package com.connectycube.flutter.connectycube_flutter_call_kit

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.Nullable
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.flutter.Log


fun createStartIncomingScreenIntent(context: Context, callId: String, callType: Int, callInitiatorId: Int,
                                    callInitiatorName: String?): Intent {
    val intent = Intent(context, IncomingCallActivity::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    intent.putExtra(EXTRA_CALL_ID, callId)
    intent.putExtra(EXTRA_CALL_TYPE, callType)
    intent.putExtra(EXTRA_CALL_INITIATOR_ID, callInitiatorId)
    intent.putExtra(EXTRA_CALL_INITIATOR_NAME, callInitiatorName)
    return intent
}

class IncomingCallActivity : Activity() {


    private var callStateReceiver: BroadcastReceiver? = null
    private var localBroadcastManager: LocalBroadcastManager? = null

    private var callId: String? = null
    private var callType: Int? = null
    private var callInitiatorId: Int? = null
    private var callInitiatorName: String? = null




    override fun onCreate(@Nullable savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(resources.getIdentifier("activity_incoming_call", "layout", packageName))

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }

        processIncomingData(intent)
        initUi()
        initCallStateReceiver()
        registerCallStateReceiver()
        Log.d("IncomingCallActivity", "onCreate(), callId = $callId")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("IncomingCallActivity", "onNewIntent, extras = " + intent.getExtras())
    }

    private fun initCallStateReceiver() {
        localBroadcastManager = LocalBroadcastManager.getInstance(this)
        callStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null || TextUtils.isEmpty(intent.action)) return
                val action: String? = intent.action
                Log.d("IncomingCallActivity", "onReceive(), action  = $action")
                if (ACTION_CALL_REJECT != action
                        && ACTION_CALL_ACCEPT != action
                        && ACTION_CALL_NOTIFICATION_CANCELED != action) {
                    return
                }
                val callIdToProcess: String = intent.getStringExtra(EXTRA_CALL_ID)
                Log.d("IncomingCallActivity", "onReceive(), callId = $callIdToProcess")
                if (TextUtils.isEmpty(callIdToProcess) || callIdToProcess != callId) {
                    Log.d("IncomingCallActivity", "ignore action for call $callIdToProcess")
                    return
                }
                when (action) {
                    ACTION_CALL_NOTIFICATION_CANCELED, ACTION_CALL_REJECT -> {
                        Log.d("IncomingCallActivity", "finishAndRemoveTask()")
                        finishAndRemoveTask()
                    }
                    ACTION_CALL_ACCEPT -> finishDelayed()
                }
            }
        }
    }

    private fun finishDelayed() {
        Log.d("IncomingCallActivity", "finishDelayed()")
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("IncomingCallActivity", "run finishAndRemoveTask()")
            finishAndRemoveTask()
        }, 1000)
    }

    private fun registerCallStateReceiver() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_CALL_NOTIFICATION_CANCELED)
        intentFilter.addAction(ACTION_CALL_REJECT)
        intentFilter.addAction(ACTION_CALL_ACCEPT)
        localBroadcastManager?.registerReceiver(callStateReceiver!!, intentFilter)
    }

    private fun unRegisterCallStateReceiver() {
        localBroadcastManager?.unregisterReceiver(callStateReceiver!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("IncomingCallActivity", "onDestroy")
        unRegisterCallStateReceiver()
    }

    private fun processIncomingData(intent: Intent) {
        callId = intent.getStringExtra(EXTRA_CALL_ID)
        callType = intent.getIntExtra(EXTRA_CALL_TYPE, -1)
        callInitiatorId = intent.getIntExtra(EXTRA_CALL_INITIATOR_ID, -1)
        callInitiatorName = intent.getStringExtra(EXTRA_CALL_INITIATOR_NAME)
    }

    private fun initUi() {
        val callTitleTxt: TextView = findViewById(resources.getIdentifier("user_name_txt", "id", packageName))
        callTitleTxt.text = callInitiatorName
        val callSubTitleTxt: TextView = findViewById(resources.getIdentifier("call_type_txt", "id", packageName))
        callSubTitleTxt.text = String.format(CALL_TYPE_PLACEHOLDER, if (callType == 1) "Video" else "Audio")
    }

    fun onEndCall(view: View?) {
        val bundle = Bundle()
        bundle.putString(EXTRA_CALL_ID, callId)
        bundle.putInt(EXTRA_CALL_TYPE, callType!!)
        bundle.putInt(EXTRA_CALL_INITIATOR_ID, callInitiatorId!!)
        bundle.putString(EXTRA_CALL_INITIATOR_NAME, callInitiatorName)

        val endCallIntent = Intent(this, EventReceiver::class.java)
        endCallIntent.action = ACTION_CALL_REJECT
        endCallIntent.putExtras(bundle)
        applicationContext.sendBroadcast(endCallIntent)
    }

    fun onStartCall(view: View?) {
        val bundle = Bundle()
        bundle.putString(EXTRA_CALL_ID, callId)
        bundle.putInt(EXTRA_CALL_TYPE, callType!!)
        bundle.putInt(EXTRA_CALL_INITIATOR_ID, callInitiatorId!!)
        bundle.putString(EXTRA_CALL_INITIATOR_NAME, callInitiatorName)

        val endCallIntent = Intent(this, EventReceiver::class.java)
        endCallIntent.action = ACTION_CALL_ACCEPT
        endCallIntent.putExtras(bundle)
        applicationContext.sendBroadcast(endCallIntent)
    }
}
