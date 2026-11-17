package sysModules.ytdlpWrapper;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import coreUtils.base.BaseApplication;
import coreUtils.library.process.LoggerUtils;
import coreUtils.library.process.ThreadTask;

/**
 * Thread-safe initializer and readiness gate for the embedded yt-dlp and FFmpeg binaries.
 * Does NOT auto-update binaries over the network at startup; guarantees that any extraction
 * or download call waits until native libraries are unpacked and ready.
 */
public final class YtDlpLibraryManager {

    private static final LoggerUtils logger = LoggerUtils.from(YtDlpLibraryManager.class);
    private static final CountDownLatch INIT_LATCH = new CountDownLatch(1);
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final AtomicBoolean INITIALIZING = new AtomicBoolean(false);

    private YtDlpLibraryManager() {}

    public static void initialize(BaseApplication appContext) {
        if (INITIALIZED.get()) {
            INIT_LATCH.countDown();
            return;
        }
        if (!INITIALIZING.compareAndSet(false, true)) {
            return;
        }
        ThreadTask.executeInBackground(() -> {
            initSync(appContext);
        });
    }

    public static synchronized boolean initSync(BaseApplication appContext) {
        if (INITIALIZED.get()) {
            INIT_LATCH.countDown();
            return true;
        }
        try {
            YoutubeDL.getInstance().init(appContext);
            FFmpeg.getInstance().init(appContext);
            INITIALIZED.set(true);
            logger.debug("yt-dlp and FFmpeg initialized successfully");
            return true;
        } catch (Exception error) {
            logger.error("yt-dlp / FFmpeg initialization failed", error);
            return false;
        } finally {
            INIT_LATCH.countDown();
        }
    }

    /**
     * Waits up to timeoutSeconds for yt-dlp and FFmpeg native binaries to finish unpacking.
     * If not yet started, triggers synchronous initialization.
     */
    public static boolean awaitReady(long timeoutSeconds) {
        if (INITIALIZED.get()) return true;
        try {
            if (!INITIALIZING.get() && BaseApplication.getInstance() != null) {
                return initSync(BaseApplication.getInstance());
            }
            INIT_LATCH.await(timeoutSeconds, TimeUnit.SECONDS);
            if (!INITIALIZED.get() && BaseApplication.getInstance() != null) {
                return initSync(BaseApplication.getInstance());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return INITIALIZED.get();
    }
}
