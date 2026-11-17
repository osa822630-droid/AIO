package coreUtils.base;

import static java.lang.Thread.setDefaultUncaughtExceptionHandler;

import android.app.Application;

import com.nextgen.BuildConfig;

import coreUtils.library.process.CrashLogWriter;
import coreUtils.library.process.LoggerUtils;
import coreUtils.library.process.ThemeManager;
import coreUtils.library.process.ThreadTask;
import dataRepo.appConfigs.AppConfigsRepo;
import dataRepo.dbManager.ObjectBoxHelper;
import dataRepo.downloads.DownloadsInfoRepo;
import dataRepo.userDetails.AppUserRepo;
import sysModules.crashedHandler.GlobalCrashedHandler;
import sysModules.interCaches.AppRawFiles;
import sysModules.newPipeLib.cache.YtStreamInfoRepo;
import sysModules.newPipeLib.libs.NewPipeLibraryManager;
import sysModules.ytdlpWrapper.YtDlpLibraryManager;

/**
 * Application entry point for TubeAIO Speed.
 * Applies Day/Night theme via {@link ThemeManager}, initializes ObjectBox repositories,
 * and loads heavy extraction libraries (yt-dlp, FFmpeg, NewPipe, Lottie) asynchronously
 * on a background thread to prevent main-thread startup stalls or ANRs.
 */
public class BaseApplication extends Application {

    private final LoggerUtils logger = LoggerUtils.from(getClass());
    public static BaseApplication AppContext;

    public static BaseApplication getInstance() {
        return AppContext;
    }

    public static boolean isDebugBuild() {
        return BuildConfig.IS_DEBUG_MODE_ON;
    }

    @Override
    public void onCreate() {
        ThemeManager.applySavedTheme(this);
        super.onCreate();
        AppContext = this;
        registerGlobalCrashHandler();
        initObjectBoxRepositories();
        initHeavyLibrariesInBackground();
    }

    @Override
    public void onTerminate() {
        CrashLogWriter.shutdown();
        AppUserRepo.release();
        super.onTerminate();
    }

    private void registerGlobalCrashHandler() {
        setDefaultUncaughtExceptionHandler(new GlobalCrashedHandler());
    }

    private void initObjectBoxRepositories() {
        long startTime = System.currentTimeMillis();
        ObjectBoxHelper.initialize(this);
        AppConfigsRepo.initialize(ObjectBoxHelper.getAppConfigBox());
        AppUserRepo.initialize(ObjectBoxHelper.getAppUserBox());
        YtStreamInfoRepo.initialize(ObjectBoxHelper.getYtStreamInfoBox());
        DownloadsInfoRepo.initialize(ObjectBoxHelper.getDownloadInfoBox());
        long endTime = System.currentTimeMillis();
        logger.debug("initRepositories took: " + (endTime - startTime) + " ms");
    }

    private void initHeavyLibrariesInBackground() {
        ThreadTask.executeInBackground(() -> {
            try {
                AppRawFiles.loadAllCompositions();
            } catch (Exception e) {
                logger.error("Lottie preload failed", e);
            }
            try {
                NewPipeLibraryManager.initializeNewPipeLibrary();
            } catch (Exception e) {
                logger.error("NewPipe init failed", e);
            }
            try {
                YtDlpLibraryManager.initialize(this);
            } catch (Exception e) {
                logger.error("yt-dlp init failed", e);
            }
        });
    }
}
