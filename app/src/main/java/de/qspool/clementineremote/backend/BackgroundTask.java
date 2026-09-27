package de.qspool.clementineremote.backend;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Work on a background thread that reports its progress and its result on the main thread, and
 * can be cancelled: what the downloaders used the deprecated {@code AsyncTask} for.
 *
 * @param <Params>   what {@link #execute} hands {@link #doInBackground}
 * @param <Progress> what {@link #publishProgress} hands {@link #onProgressUpdate}
 * @param <Result>   what {@link #doInBackground} returns
 */
public abstract class BackgroundTask<Params, Progress, Result> {

    public enum Status {
        /** Not started yet. */
        PENDING,
        /** Started, and its result not delivered yet. */
        RUNNING,
        /** Its result delivered, to {@link #onPostExecute} or {@link #onCancelled}. */
        FINISHED,
    }

    private static final AtomicInteger sThreads = new AtomicInteger();

    /** Grows with the tasks running, like AsyncTask's pool: downloads run side by side. */
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "BackgroundTask #" + sThreads.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private final Handler mMain = new Handler(Looper.getMainLooper());

    private volatile Status mStatus = Status.PENDING;

    private volatile boolean mCancelled;

    /** The thread running {@link #doInBackground}, to interrupt when cancelled. */
    private Thread mThread;

    /** Runs on a background thread. Should check {@link #isCancelled()} as it goes. */
    protected abstract Result doInBackground(Params params);

    /** On the main thread, for each {@link #publishProgress} until the task is cancelled. */
    protected void onProgressUpdate(Progress progress) {
    }

    /** On the main thread, with the result, unless the task was cancelled. */
    protected void onPostExecute(Result result) {
    }

    /** On the main thread, with whatever {@link #doInBackground} returned, once cancelled. */
    protected void onCancelled(Result result) {
    }

    /**
     * Starts the task, once. An exception from {@link #doInBackground} isn't caught: it ends
     * the app, as AsyncTask's did, rather than leaving the task running for ever.
     */
    protected final void execute(Params params) {
        if (mStatus != Status.PENDING) {
            throw new IllegalStateException("A task runs only once");
        }
        mStatus = Status.RUNNING;
        EXECUTOR.execute(() -> {
            synchronized (this) {
                mThread = Thread.currentThread();
            }
            Result result;
            try {
                result = doInBackground(params);
            } finally {
                synchronized (this) {
                    mThread = null;
                    // An interrupt from cancelling is this task's, not the next one's on the thread.
                    Thread.interrupted();
                }
            }
            mMain.post(() -> finish(result));
        });
    }

    /** Hands {@link #onProgressUpdate} the progress on the main thread. Call it in the background. */
    protected final void publishProgress(Progress progress) {
        if (!mCancelled) {
            mMain.post(() -> onProgressUpdate(progress));
        }
    }

    /**
     * Cancels the task: {@link #isCancelled()} is true from now on, and its result goes to
     * {@link #onCancelled} instead of {@link #onPostExecute}.
     *
     * @param mayInterruptIfRunning whether to interrupt the thread running it, too
     * @return false if the task had already finished
     */
    public final boolean cancel(boolean mayInterruptIfRunning) {
        if (mStatus == Status.FINISHED) {
            return false;
        }
        mCancelled = true;
        if (mayInterruptIfRunning) {
            // With the lock, the thread is still this task's when it's interrupted.
            synchronized (this) {
                if (mThread != null) {
                    mThread.interrupt();
                }
            }
        }
        return true;
    }

    public final boolean isCancelled() {
        return mCancelled;
    }

    public final Status getStatus() {
        return mStatus;
    }

    private void finish(Result result) {
        if (mCancelled) {
            onCancelled(result);
        } else {
            onPostExecute(result);
        }
        mStatus = Status.FINISHED;
    }
}
