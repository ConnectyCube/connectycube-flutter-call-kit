package com.connectycube.flutter.connectycube_flutter_call_kit.background_isolates

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import io.flutter.embedding.engine.FlutterShellArgs
import java.util.*
import java.util.concurrent.CountDownLatch


class ConnectycubeFlutterBgPerformingService : JobIntentService() {

    override fun onCreate() {
        super.onCreate()
        if (flutterBackgroundExecutor == null) {
            flutterBackgroundExecutor = FlutterConnectycubeBackgroundExecutor()
        }
        flutterBackgroundExecutor!!.startBackgroundIsolate()
    }

    /**
     * Executes a Dart callback, as specified within the incoming `intent`.
     *
     *
     * Invoked by our [JobIntentService] superclass after a call to [ ][JobIntentService.enqueueWork].
     *
     *
     * If there are no pre-existing callback execution requests, other than the incoming `intent`, then the desired Dart callback is invoked immediately.
     *
     *
     * If there are any pre-existing callback requests that have yet to be executed, the incoming
     * `intent` is added to the [.messagingQueue] to be invoked later, after all
     * pre-existing callbacks have been executed.
     */
    @SuppressLint("LongLogTag")
    override fun onHandleWork(intent: Intent) {
        if (!flutterBackgroundExecutor!!.isDartBackgroundHandlerRegistered) {
            Log.w(
                TAG,
                "A background message could not be handled in Dart as no onBackgroundMessage handler has been registered."
            )
            return
        }

        // If we're in the middle of processing queued messages, add the incoming
        // intent to the queue and return.
        synchronized(messagingQueue) {
            if (flutterBackgroundExecutor!!.isNotRunning) {
                Log.i(
                    TAG,
                    "Service has not yet started, messages will be queued."
                )
                messagingQueue.add(intent)
                return
            }
        }

        // There were no pre-existing callback requests. Execute the callback
        // specified by the incoming intent.
        val latch = CountDownLatch(1)
        Handler(mainLooper)
            .post {
                flutterBackgroundExecutor!!.executeDartCallbackInBackgroundIsolate(
                    intent,
                    latch
                )
            }
        try {
            latch.await()
        } catch (ex: InterruptedException) {
            Log.i(TAG, "Exception waiting to execute Dart callback", ex)
        }
    }

    companion object {
        private const val TAG = "ConnectycubeFlutterBgPerformingService"
        private val messagingQueue = Collections.synchronizedList(LinkedList<Intent>())

        /** Background Dart execution context.  */
        private var flutterBackgroundExecutor: FlutterConnectycubeBackgroundExecutor? = null

        /**
         * Schedule the message to be handled by the [ConnectycubeFlutterBgPerformingService].
         */
        fun enqueueMessageProcessing(context: Context, callEventIntent: Intent) {
            enqueueWork(
                context,
                ConnectycubeFlutterBgPerformingService::class.java,
                2022,
                callEventIntent,
                true
            )
        }

        /**
         * Starts the background isolate for the [ConnectycubeFlutterBgPerformingService].
         *
         *
         * Preconditions:
         *
         *
         *  * The given `callbackHandle` must correspond to a registered Dart callback. If the
         * handle does not resolve to a Dart callback then this method does nothing.
         *  * A static [.pluginRegistrantCallback] must exist, otherwise a [       ] will be thrown.
         *
         */
        @SuppressLint("LongLogTag")
        fun startBackgroundIsolate(callbackHandle: Long, shellArgs: FlutterShellArgs?) {
            if (flutterBackgroundExecutor != null) {
                Log.w(TAG, "Attempted to start a duplicate background isolate. Returning...")
                return
            }
            flutterBackgroundExecutor = FlutterConnectycubeBackgroundExecutor()
            flutterBackgroundExecutor?.startBackgroundIsolate(callbackHandle, shellArgs)
        }

        /**
         * Called once the Dart isolate (`flutterBackgroundExecutor`) has finished initializing.
         *
         *
         * Invoked by [ConnectycubeFlutterCallKitPlugin] .
         */
        /* package */
        @SuppressLint("LongLogTag")
        fun onInitialized() {
            Log.i(TAG, "ConnectycubeFlutterBgPerformingService started!")
            synchronized(messagingQueue) {

                // Handle all the message events received before the Dart isolate was
                // initialized, then clear the queue.
                for (intent in messagingQueue) {
                    flutterBackgroundExecutor?.executeDartCallbackInBackgroundIsolate(
                        intent,
                        null
                    )
                }
                messagingQueue.clear()
            }
        }
    }
}