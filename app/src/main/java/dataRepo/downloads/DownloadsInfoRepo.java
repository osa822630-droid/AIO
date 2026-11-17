package dataRepo.downloads;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import coreUtils.library.process.LoggerUtils;
import coreUtils.library.process.ThreadTask;
import dataRepo.dbManager.ObjectBoxHelper;
import io.objectbox.Box;
import io.objectbox.query.LazyList;
import io.objectbox.query.Query;
import sysModules.downloadSys.DownloadSysInf;

/**
 * Full ObjectBox repository for managing DownloadInfo records and notifying UI observers in real time.
 */
public final class DownloadsInfoRepo implements DownloadSysInf {

    private static final LoggerUtils logger = LoggerUtils.from(DownloadsInfoRepo.class);
    private static volatile Box<DownloadInfo> downloadBox;
    private static final Set<DownloadsObserver> observers = new CopyOnWriteArraySet<>();
    private static final DownloadsInfoRepo INSTANCE = new DownloadsInfoRepo();

    public interface DownloadsObserver {
        void onDownloadsUpdated(@NonNull List<DownloadInfo> allDownloads);
        default void onSingleDownloadProgress(@NonNull DownloadInfo info) {}
    }

    public static DownloadsInfoRepo getInstance() {
        return INSTANCE;
    }

    public static void initialize(@NonNull Box<DownloadInfo> box) {
        downloadBox = box;
        logger.debug("DownloadsInfoRepo initialized with " + box.count() + " records.");
    }

    private static Box<DownloadInfo> getBox() {
        if (downloadBox == null) {
            downloadBox = ObjectBoxHelper.getDownloadInfoBox();
        }
        return downloadBox;
    }

    public static synchronized long save(@NonNull DownloadInfo info) {
        info.lastModifiedTimeDate = System.currentTimeMillis();
        long id = getBox().put(info);
        info.downloadId = id;
        notifySingleProgress(info);
        notifyAllUpdated();
        return id;
    }

    public static synchronized void updateProgressOnly(@NonNull DownloadInfo info) {
        info.lastModifiedTimeDate = System.currentTimeMillis();
        getBox().put(info);
        notifySingleProgress(info);
    }

    @Nullable
    public static DownloadInfo getById(long downloadId) {
        if (downloadId <= 0) return null;
        return getBox().get(downloadId);
    }

    @NonNull
    public static List<DownloadInfo> getAllDownloads() {
        try (Query<DownloadInfo> query = getBox().query()
                .equal(DownloadInfo_.isDeleted, false)
                .orderDesc(DownloadInfo_.startTimeDate)
                .build()) {
            return query.find();
        } catch (Exception e) {
            logger.error("Error querying all downloads", e);
            return Collections.emptyList();
        }
    }

    @NonNull
    public static List<DownloadInfo> getActiveDownloads() {
        try (Query<DownloadInfo> query = getBox().query()
                .equal(DownloadInfo_.isDeleted, false)
                .equal(DownloadInfo_.isComplete, false)
                .orderDesc(DownloadInfo_.startTimeDate)
                .build()) {
            return query.find();
        } catch (Exception e) {
            logger.error("Error querying active downloads", e);
            return Collections.emptyList();
        }
    }

    @NonNull
    public static List<DownloadInfo> getCompletedDownloads() {
        try (Query<DownloadInfo> query = getBox().query()
                .equal(DownloadInfo_.isDeleted, false)
                .equal(DownloadInfo_.isComplete, true)
                .orderDesc(DownloadInfo_.completedTimeDate)
                .build()) {
            return query.find();
        } catch (Exception e) {
            logger.error("Error querying completed downloads", e);
            return Collections.emptyList();
        }
    }

    public static synchronized void removeDownload(long downloadId, boolean deleteFileOnDisk) {
        DownloadInfo info = getById(downloadId);
        if (info != null) {
            if (deleteFileOnDisk) {
                File dest = info.getDestinationFile();
                if (dest.exists()) {
                    dest.delete();
                }
                if (info.finalFilePath != null && !info.finalFilePath.isEmpty()) {
                    File finalFile = new File(info.finalFilePath);
                    if (finalFile.exists()) {
                        finalFile.delete();
                    }
                }
            }
            info.isDeleted = true;
            getBox().remove(downloadId);
            notifyAllUpdated();
        }
    }

    public static void registerObserver(@NonNull DownloadsObserver observer) {
        observers.add(observer);
    }

    public static void unregisterObserver(@NonNull DownloadsObserver observer) {
        observers.remove(observer);
    }

    public static void notifyAllUpdated() {
        if (observers.isEmpty()) return;
        List<DownloadInfo> snapshot = new ArrayList<>(getAllDownloads());
        ThreadTask.executeOnMainThread(() -> {
            for (DownloadsObserver obs : observers) {
                try {
                    obs.onDownloadsUpdated(snapshot);
                } catch (Exception e) {
                    logger.error("Observer error onDownloadsUpdated", e);
                }
            }
        });
    }

    private static void notifySingleProgress(@NonNull DownloadInfo info) {
        if (observers.isEmpty()) return;
        ThreadTask.executeOnMainThread(() -> {
            for (DownloadsObserver obs : observers) {
                try {
                    obs.onSingleDownloadProgress(info);
                } catch (Exception e) {
                    logger.error("Observer error onSingleDownloadProgress", e);
                }
            }
        });
    }

    @Override
    public boolean isDownloadSystemInitialize() {
        return downloadBox != null;
    }

    @Override
    public LazyList<DownloadInfo> getAllDownloadRecordsFromRepo() {
        Query<DownloadInfo> query = getBox().query()
                .equal(DownloadInfo_.isDeleted, false)
                .orderDesc(DownloadInfo_.startTimeDate)
                .build();
        return query.findLazy();
    }

    @Override
    public LazyList<DownloadInfo> getAllFinishedDownloadRecordsFromRepo() {
        Query<DownloadInfo> query = getBox().query()
                .equal(DownloadInfo_.isDeleted, false)
                .equal(DownloadInfo_.isComplete, true)
                .orderDesc(DownloadInfo_.completedTimeDate)
                .build();
        return query.findLazy();
    }
}
