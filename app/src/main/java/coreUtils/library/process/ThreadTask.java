package coreUtils.library.process;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lifecycle-aware background task runner with UI thread callbacks.
 * Fixed to retain strong references to active lambda callbacks during execution
 * so garbage collection never drops completion, progress, or error listeners.
 */
public final class ThreadTask<TaskResult, ProgressType> {

    private final LoggerUtils logger = LoggerUtils.from(getClass());
    private static final Handler UI_HANDLER = new Handler(Looper.getMainLooper());
    private static final ExecutorService SHARED_EXECUTOR;

    static {
        int threadPoolSize = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        SHARED_EXECUTOR = Executors.newFixedThreadPool(threadPoolSize);
    }

    private volatile BackgroundTask<TaskResult, ProgressType> backgroundTask;
    private volatile ResultTask<TaskResult> resultTask;
    private volatile ProgressUpdateTask<ProgressType> progressTask;
    private volatile ErrorTask errorTask;

    private ExecutorService executorService;
    private FutureTask<?> backgroundFutureTask;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile TaskResult result;
    private long maxExecutionTimeMs = 0;
    private Runnable timeoutRunnable;
    private DefaultLifecycleObserver lifecycleObserver;
    private WeakReference<LifecycleOwner> lifecycleOwnerRef;

    public void observeLifecycle(@Nullable LifecycleOwner owner) {
        if (owner == null) return;
        UI_HANDLER.post(() -> {
            LifecycleOwner previousOwner =
                    lifecycleOwnerRef != null ? lifecycleOwnerRef.get() : null;

            if (previousOwner != null && lifecycleObserver != null) {
                previousOwner.getLifecycle().removeObserver(lifecycleObserver);
            }

            lifecycleOwnerRef = new WeakReference<>(owner);
            lifecycleObserver = new DefaultLifecycleObserver() {
                @Override
                public void onDestroy(@NonNull LifecycleOwner destroyedOwner) {
                    cancel();
                    destroyedOwner.getLifecycle().removeObserver(this);
                    lifecycleObserver = null;
                    lifecycleOwnerRef = null;
                    logger.debug("ThreadTask auto-cancelled by lifecycle.");
                }
            };

            owner.getLifecycle().addObserver(lifecycleObserver);
        });
    }

    public void start() {
        if (backgroundFutureTask != null && !backgroundFutureTask.isDone()) {
            logger.warning("Task already running");
            return;
        }

        final BackgroundTask<TaskResult, ProgressType> taskInstance = backgroundTask;
        if (taskInstance == null) {
            logger.error("Cannot start: BackgroundTask is null.");
            return;
        }

        cancelInternal(false);
        cancelled.set(false);
        result = null;

        if (maxExecutionTimeMs > 0) {
            timeoutRunnable = () -> {
                if (!cancelled.get()) {
                    logger.warning("Task timed out. Cancelling...");
                    String message = "Task timeout after " + maxExecutionTimeMs + " ms";
                    RuntimeException timeoutException = new RuntimeException(message);
                    deliverError(timeoutException);
                    UI_HANDLER.post(this::cancel);
                }
            };

            UI_HANDLER.postDelayed(timeoutRunnable, maxExecutionTimeMs);
        }

        Runnable taskRunnable = () -> {
            try {
                if (cancelled.get()) return;
                result = taskInstance.runInBackground(progress -> {
                    if (!cancelled.get()) {
                        deliverProgress(progress);
                    }
                });

                if (!cancelled.get()) {
                    deliverResult();
                }

            } catch (Exception error) {
                logger.error("ThreadTask execution failed.", error);
                if (!cancelled.get()) {
                    deliverError(error);
                }
            } finally {
                cleanupTimeout();
            }
        };

        ExecutorService executor = executorService != null
                ? executorService : SHARED_EXECUTOR;

        backgroundFutureTask = new FutureTask<>(taskRunnable, null);
        executor.submit(backgroundFutureTask);
    }

    private void deliverResult() {
        final ResultTask<TaskResult> callback = this.resultTask;
        final TaskResult finalResult = this.result;
        UI_HANDLER.post(() -> {
            if (cancelled.get()) return;
            if (callback != null) {
                callback.onResult(finalResult);
            }
        });
    }

    private void deliverProgress(ProgressType progress) {
        final ProgressUpdateTask<ProgressType> callback = this.progressTask;
        UI_HANDLER.post(() -> {
            if (cancelled.get()) return;
            if (callback != null) {
                callback.onProgressUpdate(progress);
            }
        });
    }

    private void deliverError(@NonNull Throwable error) {
        final ErrorTask callback = this.errorTask;
        UI_HANDLER.post(() -> {
            if (callback != null) {
                callback.onError(error);
            }
        });
    }

    public void cancel() {
        cancelInternal(true);
    }

    private void cancelInternal(boolean clearCallbacks) {
        cancelled.set(true);

        if (backgroundFutureTask != null) {
            backgroundFutureTask.cancel(true);
            backgroundFutureTask = null;
        }

        cleanupTimeout();
        result = null;

        if (clearCallbacks) {
            backgroundTask = null;
            resultTask = null;
            progressTask = null;
            errorTask = null;
        }
    }

    private void cleanupTimeout() {
        if (timeoutRunnable != null) {
            UI_HANDLER.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void setBackgroundTask(@Nullable BackgroundTask<TaskResult, ProgressType> task) {
        this.backgroundTask = task;
    }

    public void setResultTask(@Nullable ResultTask<TaskResult> task) {
        this.resultTask = task;
    }

    public void setProgressUpdateTask(@Nullable ProgressUpdateTask<ProgressType> task) {
        this.progressTask = task;
    }

    public void setErrorTask(@Nullable ErrorTask task) {
        this.errorTask = task;
    }

    public void setMaxExecutionTimeMs(long ms) {
        this.maxExecutionTimeMs = ms;
    }

    public void setExecutorService(@NonNull ExecutorService executorService) {
        this.executorService = executorService;
    }

    public static void executeOnMainThread(@NonNull UITask task) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.runOnUIThread();
        } else {
            UI_HANDLER.post(task::runOnUIThread);
        }
    }

    public static void executeInBackground(@NonNull BackgroundTaskNoResult task) {
        SHARED_EXECUTOR.execute(task::runInBackground);
    }

    public interface BackgroundTask<Result, Progress> {
        Result runInBackground(@NonNull ProgressCallback<Progress> callback);
    }

    public interface ResultTask<Result> {
        void onResult(Result result);
    }

    public interface ProgressUpdateTask<Progress> {
        void onProgressUpdate(Progress progress);
    }

    public interface ProgressCallback<Progress> {
        void onProgress(Progress progress);
    }

    public interface ErrorTask {
        void onError(@NonNull Throwable error);
    }

    public interface UITask {
        void runOnUIThread();
    }

    public interface BackgroundTaskNoResult {
        void runInBackground();
    }

    public static class Builder<JobResult, ProgressType> {
        private BackgroundTask<JobResult, ProgressType> backgroundTask;
        private ResultTask<JobResult> resultTask;
        private ProgressUpdateTask<ProgressType> progressTask;
        private ErrorTask errorTask;
        private ExecutorService executorService;
        private LifecycleOwner lifecycleOwner;
        private long timeoutMs;

        public Builder<JobResult, ProgressType> withBackgroundTask(
                BackgroundTask<JobResult, ProgressType> task) {
            this.backgroundTask = task;
            return this;
        }

        public Builder<JobResult, ProgressType> withResultTask(ResultTask<JobResult> task) {
            this.resultTask = task;
            return this;
        }

        public Builder<JobResult, ProgressType> withProgressTask(ProgressUpdateTask<ProgressType> task) {
            this.progressTask = task;
            return this;
        }

        public Builder<JobResult, ProgressType> withErrorTask(ErrorTask task) {
            this.errorTask = task;
            return this;
        }

        public Builder<JobResult, ProgressType> withExecutor(ExecutorService executor) {
            this.executorService = executor;
            return this;
        }

        public Builder<JobResult, ProgressType> withLifecycle(@Nullable LifecycleOwner owner) {
            this.lifecycleOwner = owner;
            return this;
        }

        public Builder<JobResult, ProgressType> withTimeout(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public ThreadTask<JobResult, ProgressType> build() {
            ThreadTask<JobResult, ProgressType> task = new ThreadTask<>();
            task.setBackgroundTask(backgroundTask);
            task.setResultTask(resultTask);
            task.setProgressUpdateTask(progressTask);
            task.setErrorTask(errorTask);
            task.setMaxExecutionTimeMs(timeoutMs);

            if (executorService != null) {
                task.setExecutorService(executorService);
            }

            if (lifecycleOwner != null) {
                task.observeLifecycle(lifecycleOwner);
            }

            return task;
        }
    }
}
