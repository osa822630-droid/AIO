package sysModules.downloadSys;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.nextgen.R;

import coreUtils.base.BaseApplication;
import userInterface.mainScreen.MainActivity;

/**
 * Persistent Foreground Service keeping speed-modified downloads and FFmpeg post-processing
 * alive when the screen turns off or the user switches apps.
 */
public final class SpeedDownloadForegroundService extends Service {

    public static final String CHANNEL_ID = "tubeaio_speed_downloads";
    public static final int NOTIFICATION_ID = 4091;

    public static final String ACTION_UPDATE = "ACTION_UPDATE_SPEED_DOWNLOAD";
    public static final String ACTION_STOP_IDLE = "ACTION_STOP_SPEED_DOWNLOAD";
    public static final String ACTION_CANCEL_ALL = "ACTION_CANCEL_ALL_DOWNLOADS";

    public static final String EXTRA_TITLE = "EXTRA_TITLE";
    public static final String EXTRA_STATUS = "EXTRA_STATUS";
    public static final String EXTRA_PROGRESS = "EXTRA_PROGRESS";
    public static final String EXTRA_ACTIVE_COUNT = "EXTRA_ACTIVE_COUNT";

    private static long lastNotifyMs = 0L;

    public static void startOrUpdate(@NonNull String title, @NonNull String status, int progress, int activeCount, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && (now - lastNotifyMs) < 750L) {
            return;
        }
        lastNotifyMs = now;
        try {
            Context ctx = BaseApplication.getInstance();
            Intent intent = new Intent(ctx, SpeedDownloadForegroundService.class);
            intent.setAction(ACTION_UPDATE);
            intent.putExtra(EXTRA_TITLE, title);
            intent.putExtra(EXTRA_STATUS, status);
            intent.putExtra(EXTRA_PROGRESS, Math.max(0, Math.min(100, progress)));
            intent.putExtra(EXTRA_ACTIVE_COUNT, Math.max(1, activeCount));
            ContextCompat.startForegroundService(ctx, intent);
        } catch (Exception ignored) {}
    }

    public static void stopIfIdle() {
        try {
            Context ctx = BaseApplication.getInstance();
            Intent intent = new Intent(ctx, SpeedDownloadForegroundService.class);
            intent.setAction(ACTION_STOP_IDLE);
            ctx.startService(intent);
        } catch (Exception ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureNotificationChannel();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL_ALL.equals(intent.getAction())) {
            YtDlpSpeedDownloadEngine.cancelAllActiveDownloads();
            stopForegroundCompat();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_STOP_IDLE.equals(intent.getAction())) {
            stopForegroundCompat();
            stopSelf();
            return START_NOT_STICKY;
        }

        String title = intent != null && intent.getStringExtra(EXTRA_TITLE) != null
                ? intent.getStringExtra(EXTRA_TITLE)
                : getString(R.string.label_app_name_full);
        String status = intent != null && intent.getStringExtra(EXTRA_STATUS) != null
                ? intent.getStringExtra(EXTRA_STATUS)
                : getString(R.string.status_downloading_ellipsis);
        int progress = intent != null ? intent.getIntExtra(EXTRA_PROGRESS, 0) : 0;
        int activeCount = intent != null ? intent.getIntExtra(EXTRA_ACTIVE_COUNT, 1) : 1;

        Notification notification = buildNotification(title, status, progress, activeCount);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        return START_STICKY;
    }

    @NonNull
    private Notification buildNotification(@NonNull String title, @NonNull String status, int progress, int activeCount) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.putExtra(MainActivity.EXTRA_OPEN_TAB, "DOWNLOADS");
        openIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPending = PendingIntent.getActivity(
                this, 10, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent cancelIntent = new Intent(this, SpeedDownloadForegroundService.class);
        cancelIntent.setAction(ACTION_CANCEL_ALL);
        PendingIntent cancelPending = PendingIntent.getService(
                this, 11, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String contentTitle = activeCount > 1
                ? getString(R.string.notif_active_tasks_fmt, activeCount, title)
                : title;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(contentTitle)
                .setContentText(status)
                .setProgress(100, progress, progress <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentPending)
                .addAction(R.drawable.ic_close_cross, getString(R.string.btn_stop), cancelPending)
                .build();
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.notif_channel_downloads_name),
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription(getString(R.string.notif_channel_downloads_desc));
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
