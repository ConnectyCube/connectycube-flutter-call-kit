package com.connectycube.flutter.connectycube_flutter_call_kit.background_isolates

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import com.connectycube.flutter.connectycube_flutter_call_kit.*
import com.connectycube.flutter.connectycube_flutter_call_kit.utils.ContextHolder
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterShellArgs
import io.flutter.embedding.engine.dart.DartExecutor.DartCallback
import io.flutter.embedding.engine.loader.FlutterLoader
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.view.FlutterCallbackInformation
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean


/**
 * An background execution abstraction which handles initializing a background isolate running a
 * callback dispatcher, used to invoke Dart callbacks while backgrounded.
 */
class FlutterConnectycubeBackgroundExecutor : MethodCallHandler {
    private val isCallbackDispatcherReady = AtomicBoolean(false)

    /**
     * The [MethodChannel] that connects the Android side of this plugin with the background
     * Dart isolate that was created by this plugin.
     */
    private var backgroundChannel: MethodChannel? = null
    private var backgroundFlutterEngine: FlutterEngine? = null

    /**
     * Returns true when the background isolate has started and is ready to handle background
     * messages.
     */
    val isNotRunning: Boolean
        get() = !isCallbackDispatcherReady.get()

    private fun onInitialized() {
        isCallbackDispatcherReady.set(true)
        ConnectycubeFlutterBgPerformingService.onInitialized()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        val method = call.method
        try {
            if (method == "onBackgroundHandlerInitialized") {
                // This message is sent by the background method channel as soon as the background isolate
                // is running. From this point forward, the Android side of this plugin can send
                // callback handles through the background method channel, and the Dart side will execute
                // the Dart methods corresponding to those callback handles.
                onInitialized()
                result.success(true)
            } else {
                result.notImplemented()
            }
        } catch (e: Exception) {
            result.error("error", "Flutter ConnectycubeCallKit error: " + e.localizedMessage, null)
        }
    }

    /**
     * Starts running a background Dart isolate within a new [FlutterEngine] using a previously
     * used entrypoint.
     *
     *
     * The isolate is configured as follows:
     *
     *
     *  * Bundle Path: `io.flutter.view.FlutterMain.findAppBundlePath(context)`.
     *  * Entrypoint: The Dart method used the last time this plugin was initialized in the
     * foreground.
     *  * Run args: none.
     *
     *
     *
     * Preconditions:
     *
     *
     *  * The given callback must correspond to a registered Dart callback. If the handle does not
     * resolve to a Dart callback then this method does nothing.
     *
     */
    fun startBackgroundIsolate() {
        if (isNotRunning) {
            val callbackHandle = getBackgroundHandler(ContextHolder.applicationContext)
            if (callbackHandle != -1L) {
                startBackgroundIsolate(callbackHandle, null)
            }
        }
    }

    /**
     * Starts running a background Dart isolate within a new [FlutterEngine].
     *
     *
     * The isolate is configured as follows:
     *
     *
     *  * Bundle Path: `io.flutter.view.FlutterMain.findAppBundlePath(context)`.
     *  * Entrypoint: The Dart method represented by `callbackHandle`.
     *  * Run args: none.
     *
     *
     *
     * Preconditions:
     *
     *
     *  * The given `callbackHandle` must correspond to a registered Dart callback. If the
     * handle does not resolve to a Dart callback then this method does nothing.
     *
     */
    @SuppressLint("LongLogTag")
    fun startBackgroundIsolate(callbackHandle: Long, shellArgs: FlutterShellArgs?) {
        if (backgroundFlutterEngine != null) {
            Log.e("FlutterConnectycubeBackgroundExecutor", "Background isolate already started.")
            return
        }
        val loader = FlutterLoader()
        val mainHandler = Handler(Looper.getMainLooper())
        val myRunnable = Runnable {
            loader.startInitialization(ContextHolder.applicationContext!!)
            loader.ensureInitializationCompleteAsync(
                ContextHolder.applicationContext!!,
                null,
                mainHandler
            ) {
                val appBundlePath = loader.findAppBundlePath()
                val assets: AssetManager = ContextHolder.applicationContext!!.assets
                if (isNotRunning) {
                    if (shellArgs != null) {
                        Log.i(
                            "FlutterConnectycubeBackgroundExecutor",
                            "Creating background FlutterEngine instance, with args: "
                                    + shellArgs.toArray().contentToString()
                        )
                        backgroundFlutterEngine = FlutterEngine(
                            ContextHolder.applicationContext!!, shellArgs.toArray()
                        )
                    } else {
                        Log.i(
                            "FlutterConnectycubeBackgroundExecutor",
                            "Creating background FlutterEngine instance."
                        )
                        backgroundFlutterEngine =
                            FlutterEngine(ContextHolder.applicationContext!!)
                    }
                    // We need to create an instance of `FlutterEngine` before looking up the
                    // callback. If we don't, the callback cache won't be initialized and the
                    // lookup will fail.
                    val flutterCallback =
                        FlutterCallbackInformation.lookupCallbackInformation(callbackHandle)
                    val executor = backgroundFlutterEngine!!.dartExecutor
                    initializeMethodChannel(executor)
                    val dartCallback =
                        DartCallback(assets, appBundlePath, flutterCallback)
                    executor.executeDartCallback(dartCallback)
                }
            }
        }
        mainHandler.post(myRunnable)
    }

    val isDartBackgroundHandlerRegistered: Boolean
        get() = getBackgroundHandler(ContextHolder.applicationContext) != -1L

    /**
     * Executes the desired Dart callback in a background Dart isolate.
     *
     *
     * The given `intent` should contain a `long` extra called "userCallbackHandleName", which
     * corresponds to a callback registered with the Dart VM.
     */
    @SuppressLint("LongLogTag")
    fun executeDartCallbackInBackgroundIsolate(intent: Intent, latch: CountDownLatch?) {
        if (backgroundFlutterEngine == null) {
            Log.i(
                "FlutterConnectycubeBackgroundExecutor",
                "A background event could not be handled in Dart as no background handlers has been registered."
            )
            return
        }
        var result: MethodChannel.Result? = null
        if (latch != null) {
            result = object : MethodChannel.Result {
                override fun success(result: Any?) {
                    // If another thread is waiting, then wake that thread when the callback returns a result.
                    latch.countDown()
                }

                override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                    latch.countDown()
                }

                override fun notImplemented() {
                    latch.countDown()
                }
            }
        }

        // Handle the message event in Dart.
        val callEventName = intent.getStringExtra("userCallbackHandleName")
        var userCallbackHandle = -1L
        if (REJECTED_IN_BACKGROUND == callEventName) {
            userCallbackHandle = getBackgroundRejectHandler(ContextHolder.applicationContext)
        } else if (ACCEPTED_IN_BACKGROUND == callEventName) {
            userCallbackHandle = getBackgroundAcceptHandler(ContextHolder.applicationContext)
        } else if (INCOMING_IN_BACKGROUND == callEventName) {
            userCallbackHandle = getBackgroundIncomingCallHandler(ContextHolder.applicationContext)
        }

        if (userCallbackHandle == -1L) {
            Log.e(
                "FlutterConnectycubeBackgroundExecutor",
                "${intent.action} background handler has not been registered."
            )
            latch?.countDown()
            return
        }


        val callIdToProcess: String? = intent.getStringExtra(EXTRA_CALL_ID)
        if (TextUtils.isEmpty(callIdToProcess)) {
            Log.e(
                "FlutterConnectycubeBackgroundExecutor",
                "The call data not found in Intent."
            )
            latch?.countDown()
            return
        }

        val parameters = HashMap<String, Any?>()
        parameters["session_id"] = intent.getStringExtra(EXTRA_CALL_ID)
        parameters["call_type"] = intent.getIntExtra(EXTRA_CALL_TYPE, -1)
        parameters["caller_id"] = intent.getIntExtra(EXTRA_CALL_INITIATOR_ID, -1)
        parameters["caller_name"] = intent.getStringExtra(EXTRA_CALL_INITIATOR_NAME)
        parameters["call_opponents"] =
            intent.getIntegerArrayListExtra(EXTRA_CALL_OPPONENTS)?.joinToString(separator = ",")
        parameters["photo_url"] = intent.getStringExtra(EXTRA_CALL_PHOTO)
        parameters["user_info"] = intent.getStringExtra(EXTRA_CALL_USER_INFO)


        backgroundChannel?.invokeMethod(
            "onBackgroundEvent",
            object : HashMap<String?, Any?>() {
                init {
                    put("userCallbackHandle", userCallbackHandle)
                    put("args", parameters)
                }
            },
            result
        )

    }


    private fun initializeMethodChannel(isolate: BinaryMessenger) {
        // backgroundChannel is the channel responsible for receiving the following messages from
        // the background isolate that was setup by this plugin method call:
        // - "onBackgroundHandlerInitialized"
        //
        // This channel is also responsible for sending requests from Android to Dart to execute Dart
        // callbacks in the background isolate.
        backgroundChannel =
            MethodChannel(isolate, "connectycube_flutter_call_kit.methodChannel.background")
        backgroundChannel?.setMethodCallHandler(this)
    }


}