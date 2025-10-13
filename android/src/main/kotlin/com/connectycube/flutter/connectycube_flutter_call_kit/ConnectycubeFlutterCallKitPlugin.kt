package com.connectycube.flutter.connectycube_flutter_call_kit

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.WindowManager
import androidx.annotation.Keep
import androidx.annotation.NonNull
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.connectycube.flutter.connectycube_flutter_call_kit.background_isolates.ConnectycubeFlutterBgPerformingService
import com.connectycube.flutter.connectycube_flutter_call_kit.utils.*
import com.google.firebase.messaging.FirebaseMessaging
import io.flutter.embedding.engine.FlutterShellArgs
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry


/** ConnectycubeFlutterCallKitPlugin */
@Keep
class ConnectycubeFlutterCallKitPlugin : FlutterPlugin, MethodCallHandler,
    PluginRegistry.NewIntentListener, ActivityAware {
    private var applicationContext: Context? = null
    private var mainActivity: Activity? = null
    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel


    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        this.applicationContext = flutterPluginBinding.applicationContext
        ContextHolder.applicationContext = this.applicationContext
        this.methodChannel =
            MethodChannel(
                flutterPluginBinding.binaryMessenger,
                "connectycube_flutter_call_kit.methodChannel"
            )
        this.methodChannel.setMethodCallHandler(this)

        this.eventChannel =
            EventChannel(
                flutterPluginBinding.binaryMessenger,
                "connectycube_flutter_call_kit.callEventChannel"
            )
        this.eventChannel.setStreamHandler(CallStreamHandler(flutterPluginBinding.applicationContext))
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        applicationContext = null
        ContextHolder.applicationContext = null
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
    }

    @SuppressLint("LongLogTag")
    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "getVoipToken" -> {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w(
                            "ConnectycubeFlutterCallKitPlugin",
                            "Fetching FCM registration token failed",
                            task.exception
                        )
                        result.error("error", "Fetching FCM registration token failed", null)
                    } else {
                        result.success(task.result)
                    }
                }
            }

            "startBackgroundIsolate" -> {

                @Suppress("UNCHECKED_CAST") val arguments: Map<String, Any> =
                    call.arguments as Map<String, Any>

                val pluginCallbackHandle: Long
                val userCallbackHandle: Long
                val userCallbackHandleName: String =
                    arguments["userCallbackHandleName"]?.toString() ?: ""

                val arg1 = arguments["pluginCallbackHandle"] ?: -1L
                val arg2 = arguments["userCallbackHandle"] ?: -1L


                pluginCallbackHandle = if (arg1 is Long) {
                    arg1
                } else {
                    (arg1 as Int).toLong()
                }

                userCallbackHandle = if (arg2 is Long) {
                    arg2
                } else {
                    (arg2 as Int).toLong()
                }

                var shellArgs: FlutterShellArgs? = null
                if (mainActivity != null) {
                    // Supports both Flutter Activity types:
                    //    io.flutter.embedding.android.FlutterFragmentActivity
                    //    io.flutter.embedding.android.FlutterActivity
                    // We could use `getFlutterShellArgs()` but this is only available on `FlutterActivity`.
                    shellArgs = FlutterShellArgs.fromIntent(mainActivity!!.intent)
                }

                saveBackgroundHandler(applicationContext, pluginCallbackHandle)

                if (REJECTED_IN_BACKGROUND == userCallbackHandleName) {
                    saveBackgroundRejectHandler(applicationContext, userCallbackHandle)
                } else if (ACCEPTED_IN_BACKGROUND == userCallbackHandleName) {
                    saveBackgroundAcceptHandler(applicationContext, userCallbackHandle)
                } else if (INCOMING_IN_BACKGROUND == userCallbackHandleName) {
                    saveBackgroundIncomingCallHandler(applicationContext, userCallbackHandle)
                } else if (TAP_IN_BACKGROUND == userCallbackHandleName) {
                    saveBackgroundTapCallHandler(applicationContext, userCallbackHandle)
                }

                ConnectycubeFlutterBgPerformingService.startBackgroundIsolate(
                    pluginCallbackHandle, shellArgs
                )
            }

            "showCallNotification" -> {
                try {
                    @Suppress("UNCHECKED_CAST") val arguments: Map<String, Any> =
                        call.arguments as Map<String, Any>
                    val callId = arguments["session_id"] as String

                    if (CALL_STATE_UNKNOWN != getCallState(applicationContext, callId)) {
                        result.success(null)
                        return
                    }

                    val callType = arguments["call_type"] as Int
                    val callInitiatorId = arguments["caller_id"] as Int
                    val callInitiatorName = arguments["caller_name"] as String
                    val callOpponents = ArrayList((arguments["call_opponents"] as String)
                        .split(',')
                        .map { it.toInt() })
                    val callPhoto = arguments["photo_url"] as String?
                    val userInfo = arguments["user_info"] as String

                    showCallNotification(
                        applicationContext!!,
                        callId,
                        callType,
                        callInitiatorId,
                        callInitiatorName,
                        callOpponents,
                        callPhoto,
                        userInfo
                    )

                    saveCallState(applicationContext, callId, CALL_STATE_PENDING)
                    saveCallData(applicationContext, callId, arguments)
                    saveCallId(applicationContext, callId)

                    result.success(null)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "updateConfig" -> {
                try {
                    @Suppress("UNCHECKED_CAST") val arguments: Map<String, Any> =
                        call.arguments as Map<String, Any>
                    val ringtone = arguments["ringtone"] as String?
                    val icon = arguments["icon"] as String?
                    val notificationIcon = arguments["notification_icon"] as String?
                    val color = arguments["color"] as String?

                    putString(applicationContext!!, "ringtone", ringtone)
                    putString(applicationContext!!, "icon", icon)
                    putString(applicationContext!!, "notification_icon", notificationIcon)
                    putString(applicationContext!!, "color", color)

                    result.success(null)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "reportCallAccepted" -> {
                try {
                    @Suppress("UNCHECKED_CAST") val arguments: Map<String, Any> =
                        call.arguments as Map<String, Any>
                    val callId = arguments["session_id"] as String
                    cancelCallNotification(applicationContext!!, callId)

                    saveCallState(applicationContext, callId, CALL_STATE_ACCEPTED)

                    result.success(null)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "reportCallEnded" -> {
                try {
                    @Suppress("UNCHECKED_CAST") val arguments: Map<String, Any> =
                        call.arguments as Map<String, Any>
                    val callId = arguments["session_id"] as String

                    processCallEnded(applicationContext, callId)


                    result.success(null)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "getCallState" -> {
                try {
                    @Suppress("UNCHECKED_CAST") val arguments: Map<String, Any> =
                        call.arguments as Map<String, Any>
                    val callId = arguments["session_id"] as String

                    result.success(getCallState(applicationContext, callId))
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "setCallState" -> {
                try {
                    @Suppress("UNCHECKED_CAST") val arguments: Map<String, Any> =
                        call.arguments as Map<String, Any>
                    val callId = arguments["session_id"] as String
                    val callState = arguments["call_state"] as String

                    saveCallState(applicationContext, callId, callState)

                    result.success(null)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "getCallData" -> {
                try {
                    @Suppress("UNCHECKED_CAST") val arguments: Map<String, Any> =
                        call.arguments as Map<String, Any>
                    val callId = arguments["session_id"] as String

                    result.success(getCallData(applicationContext, callId))
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "setOnLockScreenVisibility" -> {
                try {
                    @Suppress("UNCHECKED_CAST") val arguments: Map<String, Any> =
                        call.arguments as Map<String, Any>
                    val isVisible = arguments["is_visible"] as Boolean

                    setOnLockScreenVisibility(isVisible)
                    result.success(null)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "clearCallData" -> {
                try {
                    @Suppress("UNCHECKED_CAST") val arguments: Map<String, Any> =
                        call.arguments as Map<String, Any>
                    val callId = arguments["session_id"] as String

                    clearCallData(applicationContext, callId)
                    result.success(null)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "getLastCallId" -> {
                try {
                    result.success(getLastCallId(applicationContext))
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "muteCall" -> {
                result.success(null)
            }

            "canUseFullScreenIntent" -> {
                result.success(canUseFullScreenIntent(applicationContext!!))
            }

            "provideFullScreenIntentAccess" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:${applicationContext?.packageName}")
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
                        applicationContext?.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        applicationContext?.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, applicationContext?.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                } else {
                    Log.d("ConnectycubeFlutterCallKitPlugin", "Permission request is available from API version 34 (UPSIDE_DOWN_CAKE) and above")
                }
                result.success(null)
            }

            else ->
                result.notImplemented()

        }
    }


    private fun setOnLockScreenVisibility(isVisible: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            mainActivity?.setShowWhenLocked(isVisible)
        } else {
            if (isVisible) {
                mainActivity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            } else {
                mainActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            }
        }
    }


    override fun onNewIntent(intent: Intent): Boolean {
        if (intent.action != null && intent.action == ACTION_CALL_ACCEPT) {
            setOnLockScreenVisibility(true)
        }

        return false
    }


    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        binding.addOnNewIntentListener(this)
        mainActivity = binding.activity
        val launchIntent = mainActivity?.intent

        if (launchIntent != null && launchIntent.action != null && launchIntent.action == ACTION_CALL_ACCEPT) {
            setOnLockScreenVisibility(true)
        }
    }

    override fun onDetachedFromActivityForConfigChanges() {
        mainActivity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        binding.addOnNewIntentListener(this)
        mainActivity = binding.activity
    }

    override fun onDetachedFromActivity() {
        mainActivity = null
    }
}

fun notifyAboutIncomingCall(
    context: Context, callId: String, callType: Int, callInitiatorId: Int,
    callInitiatorName: String, callOpponents: ArrayList<Int>, callPhoto: String?, userInfo: String
) {
    val intent = Intent(ACTION_CALL_INCOMING)
        .putExtra(EXTRA_CALL_ID, callId)
        .putExtra(EXTRA_CALL_TYPE, callType)
        .putExtra(EXTRA_CALL_INITIATOR_ID, callInitiatorId)
        .putExtra(EXTRA_CALL_INITIATOR_NAME, callInitiatorName)
        .putExtra(EXTRA_CALL_OPPONENTS, callOpponents)
        .putExtra(EXTRA_CALL_PHOTO, callPhoto)
        .putExtra(EXTRA_CALL_USER_INFO, userInfo)

    if (isApplicationForeground(context)) {
        LocalBroadcastManager.getInstance(context)
        .sendBroadcast(intent)
    } else {
        intent.putExtra("userCallbackHandleName", INCOMING_IN_BACKGROUND)
        ConnectycubeFlutterBgPerformingService.enqueueMessageProcessing(
            context,
            intent
        )
    }

    Log.d("ConnectycubeFlutterCallKitPlugin", "[notifyAboutIncomingCall] sendBroadcast ACTION_CALL_INCOMING $callId")
}

fun saveCallState(applicationContext: Context?, callId: String, callState: String) {
    if (applicationContext == null) return

    putString(applicationContext, callId + "_state", callState)
}

fun getCallState(applicationContext: Context?, callId: String): String {
    if (applicationContext == null) return CALL_STATE_UNKNOWN

    val callState: String? = getString(applicationContext, callId + "_state")

    if (TextUtils.isEmpty(callState)) return CALL_STATE_UNKNOWN

    return callState!!
}

fun getCallData(applicationContext: Context?, callId: String): Map<String, *>? {
    if (applicationContext == null) return null

    val callDataString: String? = getString(applicationContext, callId + "_data")

    if (TextUtils.isEmpty(callDataString)) return null

    return getMapFromJsonString(callDataString!!)
}

fun saveCallData(applicationContext: Context?, callId: String, callData: Map<String, *>) {
    if (applicationContext == null) return

    try {
        putString(applicationContext, callId + "_data", mapToJsonString(callData))
    } catch (e: Exception) {
        // ignore
    }
}

fun clearCallData(applicationContext: Context?, callId: String) {
    if (applicationContext == null) return

    try {
        remove(applicationContext, callId + "_state")
        remove(applicationContext, callId + "_data")
    } catch (e: Exception) {
        // ignore
    }
}

fun saveCallId(applicationContext: Context?, callId: String) {
    if (applicationContext == null) return

    try {
        putString(applicationContext, "last_call_id", callId)
    } catch (e: Exception) {
        // ignore
    }
}

fun saveBackgroundHandler(applicationContext: Context?, callbackId: Long) {
    if (applicationContext == null) return

    try {
        putLong(applicationContext, "background_callback", callbackId)
    } catch (e: Exception) {
        // ignore
    }
}

fun getBackgroundHandler(applicationContext: Context?): Long {
    if (applicationContext == null) return -1L

    return getLong(applicationContext, "background_callback")
}


fun saveBackgroundAcceptHandler(applicationContext: Context?, callbackId: Long) {
    if (applicationContext == null) return

    try {
        putLong(applicationContext, "background_callback_accept", callbackId)
    } catch (e: Exception) {
        // ignore
    }
}

fun getBackgroundAcceptHandler(applicationContext: Context?): Long {
    if (applicationContext == null) return -1L

    return getLong(applicationContext, "background_callback_accept")
}

fun saveBackgroundIncomingCallHandler(applicationContext: Context?, callbackId: Long) {
    if (applicationContext == null) return

    try {
        putLong(applicationContext, "background_callback_incoming_call", callbackId)
    } catch (e: Exception) {
        // ignore
    }
}

fun saveBackgroundTapCallHandler(applicationContext: Context?, callbackId: Long) {
    if (applicationContext == null) return

    try {
        putLong(applicationContext, "background_callback_tap_call", callbackId)
    } catch (e: Exception) {
        // ignore
    }
}

fun getBackgroundIncomingCallHandler(applicationContext: Context?): Long {
    if (applicationContext == null) return -1L

    return getLong(applicationContext, "background_callback_incoming_call")
}

fun getBackgroundTapCallHandler(applicationContext: Context?): Long {
    if (applicationContext == null) return -1L

    return getLong(applicationContext, "background_callback_tap_call")
}

fun saveBackgroundRejectHandler(applicationContext: Context?, callbackId: Long) {
    if (applicationContext == null) return

    try {
        putLong(applicationContext, "background_callback_reject", callbackId)
    } catch (e: Exception) {
        // ignore
    }
}

fun getBackgroundRejectHandler(applicationContext: Context?): Long {
    if (applicationContext == null) return -1L

    return getLong(applicationContext, "background_callback_reject")
}

fun processCallEnded(applicationContext: Context?, sessionId: String) {
    if (applicationContext == null) return

    saveCallState(applicationContext, sessionId, CALL_STATE_REJECTED)
    cancelCallNotification(applicationContext, sessionId)

    val broadcastIntent = Intent(ACTION_CALL_ENDED)
    val bundle = Bundle()
    bundle.putString(EXTRA_CALL_ID, sessionId)
    broadcastIntent.putExtras(bundle)
    LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(broadcastIntent)
}


fun getLastCallId(applicationContext: Context?): String? {
    if (applicationContext == null) return null

    return getString(applicationContext, "last_call_id")
}

class CallStreamHandler(private var context: Context) : EventChannel.StreamHandler,
    BroadcastReceiver() {
    private lateinit var localBroadcastManager: LocalBroadcastManager

    private var events: EventChannel.EventSink? = null

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        this.events = events

        registerCallStateReceiver(context)
    }

    override fun onCancel(arguments: Any?) {
        unRegisterCallStateReceiver()
    }

    private fun registerCallStateReceiver(context: Context) {
        localBroadcastManager = LocalBroadcastManager.getInstance(context)
        val intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_CALL_REJECT)
        intentFilter.addAction(ACTION_CALL_ACCEPT)
        intentFilter.addAction(ACTION_CALL_INCOMING)
        intentFilter.addAction(ACTION_CALL_TAP)
        localBroadcastManager.registerReceiver(this, intentFilter)
    }

    private fun unRegisterCallStateReceiver() {
        localBroadcastManager.unregisterReceiver(this)
    }

    @SuppressLint("LongLogTag")
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || TextUtils.isEmpty(intent.action)) return

        val action: String? = intent.action

        if (ACTION_TOKEN_REFRESHED == action) {
            val token = intent.getStringExtra(EXTRA_PUSH_TOKEN)

            val parameters = HashMap<String, Any?>()
            parameters["event"] = "voipToken"
            parameters["args"] = { "voipToken" to token }

            events?.success(parameters)
            return
        } else if (ACTION_CALL_REJECT != action && ACTION_CALL_ACCEPT != action && ACTION_CALL_INCOMING != action
            && ACTION_CALL_TAP != action) {
            return
        }

        val callIdToProcess: String? = intent.getStringExtra(EXTRA_CALL_ID)

        if (TextUtils.isEmpty(callIdToProcess)) return

        val callEventMap = HashMap<String, Any?>()
        callEventMap["session_id"] = callIdToProcess
        callEventMap["call_type"] = intent.getIntExtra(EXTRA_CALL_TYPE, -1)
        callEventMap["caller_id"] = intent.getIntExtra(EXTRA_CALL_INITIATOR_ID, -1)
        callEventMap["caller_name"] = intent.getStringExtra(EXTRA_CALL_INITIATOR_NAME)
        callEventMap["call_opponents"] =
            intent.getIntegerArrayListExtra(EXTRA_CALL_OPPONENTS)?.joinToString(separator = ",")
        callEventMap["photo_url"] = intent.getStringExtra(EXTRA_CALL_PHOTO)
        callEventMap["user_info"] = intent.getStringExtra(EXTRA_CALL_USER_INFO)

        Log.d("ConnectycubeFlutterCallKitPlugin", "callEventMap: $callEventMap")

        val callbackData = HashMap<String, Any?>()
        callbackData["args"] = callEventMap


        when (action) {
            ACTION_CALL_REJECT -> {
                saveCallState(context?.applicationContext, callIdToProcess!!, CALL_STATE_REJECTED)
                callbackData["event"] = "endCall"

                events?.success(callbackData)
            }

            ACTION_CALL_ACCEPT -> {
                saveCallState(context?.applicationContext, callIdToProcess!!, CALL_STATE_ACCEPTED)

                callbackData["event"] = "answerCall"

                events?.success(callbackData)

                val launchIntent = getLaunchIntent(context!!)
                launchIntent?.action = ACTION_CALL_ACCEPT
                context.startActivity(launchIntent)
            }

            ACTION_CALL_INCOMING -> {
                callbackData["event"] = "incomingCall"
                events?.success(callbackData)
            }

            ACTION_CALL_TAP -> {
                callbackData["event"] = "tapCall"
                events?.success(callbackData)
            }
        }
    }
}
