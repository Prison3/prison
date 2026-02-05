package com.android.prison.manager;

import android.content.ContentValues;
import com.android.prison.utils.Logger;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.android.prison.utils.FileUtils;
import com.android.prison.utils.ShellUtils;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Manages logcat logging functionality
 */
public class LogcatManager {
    public static final String TAG = LogcatManager.class.getSimpleName();

    private static LogcatManager sInstance;
    private Context mContext;
    private java.lang.Process mLogcatProcess;

    private LogcatManager() {
    }

    public static LogcatManager get() {
        if (sInstance == null) {
            synchronized (LogcatManager.class) {
                if (sInstance == null) {
                    sInstance = new LogcatManager();
                }
            }
        }
        return sInstance;
    }

    /**
     * Initialize the logcat manager with context
     * @return this instance for method chaining
     */
    public LogcatManager init(Context context) {
        mContext = context;
        return this;
    }

    /**
     * Start logcat logging to file
     */
    public void start() {
        if (mContext == null) {
            Logger.e(TAG, "Context not initialized, cannot start logcat");
            return;
        }

        // Use a separate thread with low priority to avoid blocking system operations
        Thread logcatThread = new Thread(() -> {
            try {
                // Set thread priority to background to avoid affecting system responsiveness
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                
                String fileName = mContext.getPackageName() + "_logcat.txt";
                boolean useMediaStore = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;

                if (useMediaStore) {
                    startWithMediaStore(fileName);
                } else {
                    startWithFile(fileName);
                }
            } catch (Exception e) {
                Logger.e(TAG, "Failed to start logcat: " + e.getMessage(), e);
            }
        }, "LogcatManager");
        logcatThread.setDaemon(true);
        logcatThread.start();
    }

    /**
     * Start logcat using MediaStore for Android 10+
     */
    private void startWithMediaStore(String fileName) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/logs");

            Uri uri = mContext.getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

            if (uri != null) {
                // Clear logcat buffer asynchronously
                new Thread(() -> {
                    try {
                        ShellUtils.execCommand("logcat -c", false);
                    } catch (Exception e) {
                        Logger.w(TAG, "Failed to clear logcat: " + e.getMessage());
                    }
                }).start();
                
                // Start logcat process in background without blocking
                new Thread(() -> {
                    try (OutputStream out = mContext.getContentResolver().openOutputStream(uri)) {
                        if (out != null) {
                            mLogcatProcess = Runtime.getRuntime().exec("logcat");
                            try (InputStream in = mLogcatProcess.getInputStream()) {
                                byte[] buffer = new byte[4096];
                                int len;
                                while ((len = in.read(buffer)) != -1 && mLogcatProcess != null) {
                                    out.write(buffer, 0, len);
                                    out.flush();
                                }
                            }
                        }
                    } catch (Exception e) {
                        Logger.e(TAG, "Failed to write logcat to MediaStore: " + e.getMessage(), e);
                    }
                }, "LogcatWriter").start();
                
                Logger.d(TAG, "Logcat started with MediaStore: " + fileName);
            } else {
                Logger.e(TAG, "Failed to create MediaStore entry for logcat");
            }
        } catch (Exception e) {
            Logger.e(TAG, "Failed to start logcat with MediaStore: " + e.getMessage(), e);
        }
    }

    /**
     * Start logcat using file system for Android 9 and below
     */
    private void startWithFile(String fileName) {
        try {
            File documentsDir = new File(
                    mContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "logs");
            if (!documentsDir.exists()) {
                documentsDir.mkdirs();
            }

            File logFile = new File(documentsDir, fileName);
            FileUtils.deleteDir(logFile);

            ShellUtils.execCommand("logcat -c", false);
            ShellUtils.execCommand("logcat -f " + logFile.getAbsolutePath(), false);

            Logger.d(TAG, "Logcat started with file: " + logFile.getAbsolutePath());
        } catch (Exception e) {
            Logger.e(TAG, "Failed to start logcat with file: " + e.getMessage(), e);
        }
    }

    /**
     * Stop logcat logging
     */
    public void stop() {
        if (mLogcatProcess != null) {
            try {
                mLogcatProcess.destroy();
                mLogcatProcess = null;
                Logger.d(TAG, "Logcat stopped");
            } catch (Exception e) {
                Logger.e(TAG, "Failed to stop logcat: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Clear logcat buffer
     */
    public void clear() {
        try {
            ShellUtils.execCommand("logcat -c", false);
            Logger.d(TAG, "Logcat buffer cleared");
        } catch (Exception e) {
            Logger.e(TAG, "Failed to clear logcat: " + e.getMessage(), e);
        }
    }
}
