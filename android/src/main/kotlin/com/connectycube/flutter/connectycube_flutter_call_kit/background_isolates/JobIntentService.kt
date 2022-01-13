package com.connectycube.flutter.connectycube_flutter_call_kit.background_isolates

import android.app.Service
import android.app.job.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import java.util.*


abstract class JobIntentService : Service() {
    private var mJobImpl: CompatJobEngine? = null
    private var mCompatWorkEnqueuer: WorkEnqueuer? = null
    private var mCurProcessor: CommandProcessor? = null
    var mInterruptIfStopped = false
    var mStopped = false
    var mDestroyed = false

    private var mCompatQueue: ArrayList<CompatWorkItem>? = null

    // Class only used to create a unique hash key for sClassWorkEnqueuer
    private class ComponentNameWithWakeful internal constructor(
        private val componentName: ComponentName,
        private val useWakefulService: Boolean
    )

    /**
     * Base class for the target service we can deliver work to and the implementation of how to
     * deliver that work.
     */
    abstract class WorkEnqueuer(val mComponentName: ComponentName) {
        var mHasJobId = false
        var mJobId = 0
        fun ensureJobId(jobId: Int) {
            if (!mHasJobId) {
                mHasJobId = true
                mJobId = jobId
            } else require(mJobId == jobId) { "Given job ID $jobId is different than previous $mJobId" }
        }

        abstract fun enqueueWork(work: Intent?)
        open fun serviceStartReceived() {}
        open fun serviceProcessingStarted() {}
        open fun serviceProcessingFinished() {}
    }

    /** Get rid of lint warnings about API levels.  */
    interface CompatJobEngine {
        fun compatGetBinder(): IBinder?
        fun dequeueWork(): GenericWorkItem?
    }

    /** An implementation of WorkEnqueuer that works for pre-O (raw Service-based).  */
    class CompatWorkEnqueuer(context: Context, cn: ComponentName) :
        WorkEnqueuer(cn) {
        private val mContext: Context = context.applicationContext
        private val mLaunchWakeLock: PowerManager.WakeLock
        private val mRunWakeLock: PowerManager.WakeLock
        var mLaunchingService = false
        var mServiceProcessing = false
        override fun enqueueWork(work: Intent?) {
            val intent = Intent(work)
            intent.component = mComponentName
            if (DEBUG) Log.d(TAG, "Starting service for work: $work")
            if (mContext.startService(intent) != null) {
                synchronized(this) {
                    if (!mLaunchingService) {
                        mLaunchingService = true
                        if (!mServiceProcessing) {
                            // If the service is not already holding the wake lock for
                            // itself, acquire it now to keep the system running until
                            // we get this work dispatched.  We use a timeout here to
                            // protect against whatever problem may cause it to not get
                            // the work.
                            mLaunchWakeLock.acquire((60 * 1000).toLong())
                        }
                    }
                }
            }
        }

        override fun serviceStartReceived() {
            synchronized(this) {
                // Once we have started processing work, we can count whatever last
                // enqueueWork() that happened as handled.
                mLaunchingService = false
            }
        }

        override fun serviceProcessingStarted() {
            synchronized(this) {
                // We hold the wake lock as long as the service is processing commands.
                if (!mServiceProcessing) {
                    mServiceProcessing = true
                    // Keep the device awake, but only for at most 10 minutes at a time
                    // (Similar to JobScheduler.)
                    mRunWakeLock.acquire(10 * 60 * 1000L)
                    mLaunchWakeLock.release()
                }
            }
        }

        override fun serviceProcessingFinished() {
            synchronized(this) {
                if (mServiceProcessing) {
                    // If we are transitioning back to a wakelock with a timeout, do the same
                    // as if we had enqueued work without the service running.
                    if (mLaunchingService) {
                        mLaunchWakeLock.acquire((60 * 1000).toLong())
                    }
                    mServiceProcessing = false
                    mRunWakeLock.release()
                }
            }
        }

        init {
            // Make wake locks.  We need two, because the launch wake lock wants to have
            // a timeout, and the system does not do the right thing if you mix timeout and
            // non timeout (or even changing the timeout duration) in one wake lock.
            val pm = context.getSystemService(POWER_SERVICE) as PowerManager
            mLaunchWakeLock =
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, cn.className + ":launch")
            mLaunchWakeLock.setReferenceCounted(false)
            mRunWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, cn.className + ":run")
            mRunWakeLock.setReferenceCounted(false)
        }
    }

    /** Implementation of a JobServiceEngine for interaction with JobIntentService.  */
    @RequiresApi(26)
    internal class JobServiceEngineImpl(private val mService: JobIntentService) :
        JobServiceEngine(mService), CompatJobEngine {
        val mLock = Any()
        var mParams: JobParameters? = null

        internal inner class WrapperWorkItem(private val mJobWork: JobWorkItem) :
            GenericWorkItem {
            override val intent: Intent
                get() = mJobWork.intent

            override fun complete() {
                synchronized(mLock) {
                    if (mParams != null) {
                        try {
                            mParams!!.completeWork(mJobWork)
                            // The following catches are to prevent errors completely work that
                            //    is done or hasn't started.
                            // Example:
                            // Caused by java.lang.IllegalArgumentException:
                            //     Given work is not active: JobWorkItem {
                            //       id=4 intent=Intent { (has extras) } dcount=1
                            //     }
                            // Issue: https://github.com/OneSignal/OneSignal-Android-SDK/issues/644
                        } catch (e: SecurityException) {
                            Log.e(
                                TAG,
                                "SecurityException: Failed to run mParams.completeWork(mJobWork)!",
                                e
                            )
                        } catch (e: IllegalArgumentException) {
                            Log.e(
                                TAG,
                                "IllegalArgumentException: Failed to run mParams.completeWork(mJobWork)!",
                                e
                            )
                        }
                    }
                }
            }
        }

        override fun compatGetBinder(): IBinder {
            return binder
        }

        override fun onStartJob(params: JobParameters): Boolean {
            if (DEBUG) Log.d(
                TAG,
                "onStartJob: $params"
            )
            mParams = params
            // We can now start dequeuing work!
            mService.ensureProcessorRunningLocked(false)
            return true
        }

        override fun onStopJob(params: JobParameters): Boolean {
            if (DEBUG) Log.d(
                TAG,
                "onStopJob: $params"
            )
            val result = mService.doStopCurrentWork()
            synchronized(mLock) {
                // Once we return, the job is stopped, so its JobParameters are no
                // longer valid and we should not be doing anything with them.
                mParams = null
            }
            return result
        }

        /** Dequeue some work.  */
        override fun dequeueWork(): GenericWorkItem? {
            var work: JobWorkItem?
            synchronized(mLock) {
                if (mParams == null) return null
                work = try {
                    mParams!!.dequeueWork()
                } catch (e: SecurityException) {
                    // Work around for https://issuetracker.google.com/issues/63622293
                    // https://github.com/OneSignal/OneSignal-Android-SDK/issues/673
                    // Caller no longer running, last stopped +###ms because: last work dequeued
                    Log.e(
                        TAG,
                        "Failed to run mParams.dequeueWork()!",
                        e
                    )
                    return null
                }
            }
            return if (work != null) {
                work!!.intent.setExtrasClassLoader(mService.classLoader)
                WrapperWorkItem(work!!)
            } else null
        }

        companion object {
            const val TAG = "JobServiceEngineImpl"
            const val DEBUG = false
        }
    }

    @RequiresApi(26)
    internal class JobWorkEnqueuer(context: Context, cn: ComponentName?, jobId: Int) :
        JobIntentService.WorkEnqueuer(cn!!) {
        private val mJobInfo: JobInfo
        private val mJobScheduler: JobScheduler
        override fun enqueueWork(work: Intent?) {
            if (DEBUG) Log.d(TAG, "Enqueueing work: $work")
            mJobScheduler.enqueue(mJobInfo, JobWorkItem(work))
        }

        init {
            ensureJobId(jobId)
            val b = JobInfo.Builder(jobId, mComponentName)
            mJobInfo = b.setOverrideDeadline(0).build()
            mJobScheduler =
                context.applicationContext.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
        }
    }

    /** Abstract definition of an item of work that is being dispatched.  */
    interface GenericWorkItem {
        val intent: Intent?

        fun complete()
    }


    /**
     * An implementation of GenericWorkItem that dispatches work for pre-O platforms: intents received
     * through a raw service's onStartCommand.
     */
    inner class CompatWorkItem(override val intent: Intent, val mStartId: Int) :
        GenericWorkItem {
        override fun complete() {
            if (DEBUG) Log.d(TAG, "Stopping self: #$mStartId")
            stopSelf(mStartId)
        }
    }

    /** This is a task to dequeue and process work in the background.  */
    inner class CommandProcessor :
        AsyncTask<Void?, Void?, Void?>() {
        override fun doInBackground(vararg params: Void?): Void? {
            var work: GenericWorkItem?
            if (DEBUG) Log.d(TAG, "Starting to dequeue work...")
            while (dequeueWork().also { work = it } != null) {
                if (DEBUG) Log.d(TAG, "Processing next work: $work")
                onHandleWork(work?.intent!!)
                if (DEBUG) Log.d(TAG, "Completing work: $work")
                work?.complete()
            }
            if (DEBUG) Log.d(TAG, "Done processing work!")
            return null
        }

        override fun onCancelled(aVoid: Void?) {
            processorFinished()
        }

        override fun onPostExecute(aVoid: Void?) {
            processorFinished()
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (DEBUG) Log.d(TAG, "CREATING: $this")
        if (Build.VERSION.SDK_INT >= 26) {
            mJobImpl = JobServiceEngineImpl(this)
            mCompatWorkEnqueuer = null
        }
        val cn = ComponentName(this, this.javaClass)
        mCompatWorkEnqueuer = getWorkEnqueuer(this, cn, false, 0, true)
    }

    /**
     * Processes start commands when running as a pre-O service, enqueueing them to be later
     * dispatched in [.onHandleWork].
     */
    override fun onStartCommand(@Nullable intent: Intent?, flags: Int, startId: Int): Int {
        mCompatWorkEnqueuer!!.serviceStartReceived()
        if (DEBUG) Log.d(TAG, "Received compat start command #$startId: $intent")
        synchronized(mCompatQueue!!) {
            mCompatQueue!!.add(
                CompatWorkItem(
                    intent ?: Intent(),
                    startId
                )
            )
            ensureProcessorRunningLocked(true)
        }
        return START_REDELIVER_INTENT
    }

    /**
     * Returns the IBinder for the [android.app.job.JobServiceEngine] when running as a
     * JobService on O and later platforms.
     */
    override fun onBind(intent: Intent): IBinder? {
        return if (mJobImpl != null) {
            val engine = mJobImpl!!.compatGetBinder()
            if (DEBUG) Log.d(TAG, "Returning engine: $engine")
            engine
        } else {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        doStopCurrentWork()
        synchronized(mCompatQueue!!) {
            mDestroyed = true
            mCompatWorkEnqueuer!!.serviceProcessingFinished()
        }
    }


    /**
     * Called serially for each work dispatched to and processed by the service. This method is called
     * on a background thread, so you can do long blocking operations here. Upon returning, that work
     * will be considered complete and either the next pending work dispatched here or the overall
     * service destroyed now that it has nothing else to do.
     *
     *
     * Be aware that when running as a job, you are limited by the maximum job execution time and
     * any single or total sequential items of work that exceeds that limit will cause the service to
     * be stopped while in progress and later restarted with the last unfinished work. (There is
     * currently no limit on execution duration when running as a pre-O plain Service.)
     *
     * @param intent The intent describing the work to now be processed.
     */
    protected abstract fun onHandleWork(intent: Intent)

    /**
     * Control whether code executing in [.onHandleWork] will be interrupted if the job
     * is stopped. By default this is false. If called and set to true, any time [ ][.onStopCurrentWork] is called, the class will first call [ AsyncTask.cancel(true)][AsyncTask.cancel] to interrupt the running task.
     *
     * @param interruptIfStopped Set to true to allow the system to interrupt actively running work.
     */
    open fun setInterruptIfStopped(interruptIfStopped: Boolean) {
        mInterruptIfStopped = interruptIfStopped
    }

    /**
     * Returns true if [.onStopCurrentWork] has been called. You can use this, while executing
     * your work, to see if it should be stopped.
     */
    open fun isStopped(): Boolean {
        return mStopped
    }

    /**
     * This will be called if the JobScheduler has decided to stop this job. The job for this service
     * does not have any constraints specified, so this will only generally happen if the service
     * exceeds the job's maximum execution time.
     *
     * @return True to indicate to the JobManager whether you'd like to reschedule this work, false to
     * drop this and all following work. Regardless of the value returned, your service must stop
     * executing or the system will ultimately kill it. The default implementation returns true,
     * and that is most likely what you want to return as well (so no work gets lost).
     */
    open fun onStopCurrentWork(): Boolean {
        return true
    }

    open fun doStopCurrentWork(): Boolean {
        if (mCurProcessor != null) {
            mCurProcessor!!.cancel(mInterruptIfStopped)
        }
        mStopped = true
        return onStopCurrentWork()
    }

    open fun ensureProcessorRunningLocked(reportStarted: Boolean) {
        if (mCurProcessor == null) {
            mCurProcessor = CommandProcessor()
            if (mCompatWorkEnqueuer != null && reportStarted) {
                mCompatWorkEnqueuer!!.serviceProcessingStarted()
            }
            if (DEBUG) Log.d(TAG, "Starting processor: $mCurProcessor")
            mCurProcessor!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }
    }

    open fun processorFinished() {
        if (mCompatQueue != null) {
            synchronized(mCompatQueue!!) {
                mCurProcessor = null
                // The async task has finished, but we may have gotten more work scheduled in the
                // meantime.  If so, we need to restart the new processor to execute it.  If there
                // is no more work at this point, either the service is in the process of being
                // destroyed (because we called stopSelf on the last intent started for it), or
                // someone has already called startService with a new Intent that will be
                // arriving shortly.  In either case, we want to just leave the service
                // waiting -- either to get destroyed, or get a new onStartCommand() callback
                // which will then kick off a new processor.
                if (mCompatQueue != null && mCompatQueue!!.size > 0) {
                    ensureProcessorRunningLocked(false)
                } else if (!mDestroyed) {
                    mCompatWorkEnqueuer!!.serviceProcessingFinished()
                }
            }
        }
    }

    open fun dequeueWork(): GenericWorkItem? {
        if (mJobImpl != null) {
            val jobWork = mJobImpl!!.dequeueWork()
            if (jobWork != null) return jobWork
        }
        synchronized(mCompatQueue!!) { return if (mCompatQueue!!.size > 0) mCompatQueue!!.removeAt(0) else null }
    }

    companion object {
        const val TAG = "JobIntentService"
        const val DEBUG = false
        private val sLock = Any()
        private val sClassWorkEnqueuer: HashMap<ComponentNameWithWakeful, WorkEnqueuer> = HashMap()

        /**
         * Call this to enqueue work for your subclass of [JobIntentService]. This will either
         * directly start the service (when running on pre-O platforms) or enqueue work for it as a job
         * (when running on O and later). In either case, a wake lock will be held for you to ensure you
         * continue running. The work you enqueue will ultimately appear at [.onHandleWork].
         *
         * @param context Context this is being called from.
         * @param cls The concrete class the work should be dispatched to (this is the class that is
         * published in your manifest).
         * @param jobId A unique job ID for scheduling; must be the same value for all work enqueued for
         * the same class.
         * @param work The Intent of work to enqueue.
         */
        fun enqueueWork(
            context: Context,
            cls: Class<*>,
            jobId: Int,
            work: Intent,
            useWakefulService: Boolean
        ) {
            enqueueWork(context, ComponentName(context, cls), jobId, work, useWakefulService)
        }

        /**
         * Like [.enqueueWork], but supplies a ComponentName
         * for the service to interact with instead of its class.
         *
         * @param context Context this is being called from.
         * @param component The published ComponentName of the class this work should be dispatched to.
         * @param jobId A unique job ID for scheduling; must be the same value for all work enqueued for
         * the same class.
         * @param work The Intent of work to enqueue.
         */
        fun enqueueWork(
            context: Context,
            component: ComponentName,
            jobId: Int,
            work: Intent,
            useWakefulService: Boolean
        ) {
            synchronized(sLock) {
                var we =
                    getWorkEnqueuer(context, component, true, jobId, useWakefulService)
                we.ensureJobId(jobId)

                // Can throw on API 26+ if useWakefulService=true and app is NOT whitelisted.
                // One example is when an FCM high priority message is received the system will
                // temporarily whitelist the app. However it is possible that it does not end up getting
                //    whitelisted so we need to catch this and fall back to a job service.
                try {
                    we.enqueueWork(work)
                } catch (e: IllegalStateException) {
                    if (useWakefulService) {
                        we = getWorkEnqueuer(context, component, true, jobId, false)
                        we.enqueueWork(work)
                    } else throw e
                }
            }
        }

        fun getWorkEnqueuer(
            context: Context?,
            cn: ComponentName?,
            hasJobId: Boolean,
            jobId: Int,
            useWakefulService: Boolean
        ): WorkEnqueuer {
            val key = ComponentNameWithWakeful(cn!!, useWakefulService)
            var we = sClassWorkEnqueuer[key]
            if (we == null) {
                we = if (Build.VERSION.SDK_INT >= 26 && !useWakefulService) {
                    require(hasJobId) { "Can't be here without a job id" }
                    JobWorkEnqueuer(context!!, cn, jobId)
                } else CompatWorkEnqueuer(
                    context!!, cn
                )
                sClassWorkEnqueuer[key] = we
            }
            return we
        }
    }

    /** Default empty constructor.  */
    init {
        mCompatQueue = ArrayList()
    }
}