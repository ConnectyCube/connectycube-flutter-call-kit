package com.connectycube.flutter.connectycube_flutter_call_kit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.TextUtils
import androidx.annotation.Keep
import androidx.annotation.NonNull
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry


/** ConnectycubeFlutterCallKitPlugin */
@Keep
class ConnectycubeFlutterCallKitPlugin : FlutterPlugin, MethodCallHandler, PluginRegistry.NewIntentListener, BroadcastReceiver() {
    private lateinit var applicationContext: Context
    private lateinit var channel: MethodChannel
    private lateinit var localBroadcastManager: LocalBroadcastManager

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        onAttachedToEngine(flutterPluginBinding.applicationContext, flutterPluginBinding.binaryMessenger)
        registerCallStateReceiver()
    }

    private fun onAttachedToEngine(context: Context, binaryMessenger: BinaryMessenger) {
        this.applicationContext = context
        channel = MethodChannel(binaryMessenger, "connectycube_flutter_call_kit")
        this.channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "showCallNotification" -> {
                try {
                    val arguments: Map<String, Any> = call.arguments as Map<String, Any>
                    val callId = arguments["session_id"] as String
                    val callType = arguments["call_type"] as Int
                    val callInitiatorId = arguments["caller_id"] as Int
                    val callInitiatorName = arguments["caller_name"] as String
                    showCallNotification(applicationContext, callId, callType, callInitiatorId, callInitiatorName)

                    result.success(null)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "reportCallAccepted" -> {
                try {
                    val arguments: Map<String, Any> = call.arguments as Map<String, Any>
                    val callId = arguments["session_id"] as String
                    cancelCallNotification(applicationContext, callId)

                    result.success(null)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "reportCallEnded" -> {
                try {
                    val arguments: Map<String, Any> = call.arguments as Map<String, Any>
                    val callId = arguments["session_id"] as String
                    cancelCallNotification(applicationContext, callId)

                    result.success(null)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "getCallPushParameters" -> {

            }
            else ->
                result.notImplemented()

        }
    }

    private fun registerCallStateReceiver() {
        localBroadcastManager = LocalBroadcastManager.getInstance(applicationContext)
        val intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_CALL_REJECT)
        intentFilter.addAction(ACTION_CALL_ACCEPT)
        localBroadcastManager.registerReceiver(this, intentFilter)
    }

    private fun unRegisterCallStateReceiver() {
        localBroadcastManager.unregisterReceiver(this)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        unRegisterCallStateReceiver()
    }

    override fun onNewIntent(intent: Intent?): Boolean {
        TODO("Not yet implemented")
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || TextUtils.isEmpty(intent.action)) return

        val action: String? = intent.action
        if (ACTION_CALL_REJECT != action && ACTION_CALL_ACCEPT != action) {
            return
        }
        val callIdToProcess: String? = intent.getStringExtra(EXTRA_CALL_ID)
        if (TextUtils.isEmpty(callIdToProcess)) return

        val  parameters = HashMap<String, String?>()
        parameters["session_id"] = callIdToProcess

        when (action) {
            ACTION_CALL_REJECT -> {
                channel.invokeMethod("onCallRejected", parameters)
            }
            ACTION_CALL_ACCEPT -> {
                channel.invokeMethod("onCallAccepted", parameters)
            }
        }
    }
}
