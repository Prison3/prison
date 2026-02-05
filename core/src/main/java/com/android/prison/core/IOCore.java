package com.android.prison.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.text.TextUtils;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.android.prison.base.PEnvironment;
import com.android.prison.base.PActivityThread;
import com.android.prison.manager.PPackageManager;
import com.android.prison.utils.FileUtils;
import com.android.prison.utils.Logger;
import com.android.prison.utils.TrieTree;

/**
 * I/O redirection core for virtualized applications.
 * Manages path redirection from virtual app paths to actual storage locations.
 * 
 * <p>Example: /data/data/com.google/ -> /data/data/com.virtual/data/com.google/
 */
public class IOCore {
    private static final String TAG = IOCore.class.getSimpleName();
    
    // Path constants
    private static final String PRISON_PATH_MARKER = "/prison/";
    private static final String PROC_SELF = "/proc/self/";
    private static final String PROC_CMDLINE = "cmdline";
    private static final String PROFILES_DIR = "profiles";
    private static final String PROFILES_CUR = "cur";
    private static final String PROFILES_REF = "ref";
    private static final String FAKE_SUFFIX = "-fake";
    
    // Root detection paths to hide
    private static final String[] ROOT_PATHS = {
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su"
    };

    private static final IOCore INSTANCE = new IOCore();
    private static final TrieTree redirectTree = new TrieTree();
    private static final TrieTree blackListTree = new TrieTree();
    private final Map<String, String> redirectMap = new LinkedHashMap<>();

    private IOCore() {
        // Singleton
    }

    public static IOCore get() {
        return INSTANCE;
    }

    /**
     * Adds a path redirection rule.
     * 
     * @param origPath Original path to redirect from
     * @param redirectPath Target path to redirect to
     */
    public void addRedirect(String origPath, String redirectPath) {
        if (TextUtils.isEmpty(origPath) || TextUtils.isEmpty(redirectPath)) {
            return;
        }
        
        if (redirectMap.containsKey(origPath)) {
            Logger.d(TAG, "Redirect rule already exists: " + origPath);
            return;
        }
        
        redirectTree.add(origPath);
        redirectMap.put(origPath, redirectPath);
        
        // Ensure target directory exists
        File redirectFile = new File(redirectPath);
        if (!redirectFile.exists()) {
            FileUtils.mkdirs(redirectPath);
        }
        
        // Register with native layer
        NativeCore.addIORule(origPath, redirectPath);
    }

    /**
     * Adds a path to the blacklist (paths that should not be redirected).
     * 
     * @param path Path to blacklist
     */
    public void addBlackRedirect(String path) {
        if (TextUtils.isEmpty(path)) {
            return;
        }
        blackListTree.add(path);
    }

    /**
     * Redirects a path string using the registered rules.
     * 
     * @param path Original path
     * @return Redirected path, or original if no rule matches
     */
    public String redirectPath(String path) {
        if (TextUtils.isEmpty(path)) {
            return path;
        }
        
        // Skip paths already containing prison marker
        if (path.contains(PRISON_PATH_MARKER)) {
            return path;
        }
        
        // Check blacklist first
        String blackListMatch = blackListTree.search(path);
        if (!TextUtils.isEmpty(blackListMatch)) {
            return blackListMatch;
        }

        // Search for matching redirect rule
        String matchedKey = redirectTree.search(path);
        if (!TextUtils.isEmpty(matchedKey)) {
            String redirectTarget = redirectMap.get(matchedKey);
            if (redirectTarget != null) {
                return path.replace(matchedKey, redirectTarget);
            }
        }

        return path;
    }

    /**
     * Redirects a File path using the registered rules.
     * 
     * @param path Original file path
     * @return Redirected file path, or original if no rule matches
     */
    public File redirectPath(File path) {
        if (path == null) {
            return null;
        }
        String redirectedPath = redirectPath(path.getAbsolutePath());
        return new File(redirectedPath);
    }

    /**
     * Redirects a path using a custom rule map (for temporary rules).
     * 
     * @param path Original path
     * @param rule Custom redirect rule map
     * @return Redirected path
     */
    public String redirectPath(String path, Map<String, String> rule) {
        if (TextUtils.isEmpty(path) || rule == null || rule.isEmpty()) {
            return path;
        }

        String matchedKey = redirectTree.search(path);
        if (!TextUtils.isEmpty(matchedKey)) {
            String redirectTarget = rule.get(matchedKey);
            if (redirectTarget != null) {
                return path.replace(matchedKey, redirectTarget);
            }
        }

        return path;
    }

    /**
     * Redirects a File path using a custom rule map.
     * 
     * @param path Original file path
     * @param rule Custom redirect rule map
     * @return Redirected file path
     */
    public File redirectPath(File path, Map<String, String> rule) {
        if (path == null) {
            return null;
        }
        String redirectedPath = redirectPath(path.getAbsolutePath(), rule);
        return new File(redirectedPath);
    }

    /**
     * Sets up path redirection for a virtualized application.
     * Configures all necessary redirect rules for data directories, libraries, profiles, etc.
     * 
     * @param context Application context
     */
    public void setupRedirect(Context context) {
        if (context == null) {
            Logger.e(TAG, "Context is null, cannot enable redirect");
            return;
        }
        
        Map<String, String> rule = new LinkedHashMap<>();
        Set<String> blackRule = new HashSet<>();
        String packageName = context.getPackageName();

        try {
            ApplicationInfo packageInfo = PPackageManager.get().getApplicationInfo(
                packageName, 
                PackageManager.GET_META_DATA, 
                PActivityThread.getUserId()
            );
            
            int systemUserId = PrisonCore.getUserId();
            int virtualUserId = PActivityThread.getUserId();
            
            // Setup data directory redirects
            setupDataDirectoryRedirects(rule, packageName, packageInfo, systemUserId);
            
            // Setup ART profile redirects
            setupProfileRedirects(rule, packageName, virtualUserId);
            
            // Setup external storage redirects
            setupExternalStorageRedirects(rule, blackRule, systemUserId, context);
            
            // Setup root hiding and proc redirects
            setupRootHiding(rule);
            setupProcRedirects(rule);
            
        } catch (Exception e) {
            Logger.e(TAG, "Failed to setup redirect rules for package: " + packageName, e);
        }
        
        // Apply all redirect rules
        applyRedirectRules(rule, blackRule);
        
    }

    /**
     * Sets up data directory and library redirects.
     */
    private void setupDataDirectoryRedirects(Map<String, String> rule, String packageName, 
                                            ApplicationInfo packageInfo, int systemUserId) {
        // Native library directories
        rule.put(String.format("/data/data/%s/lib", packageName), packageInfo.nativeLibraryDir);
        rule.put(String.format("/data/user/%d/%s/lib", systemUserId, packageName), 
                packageInfo.nativeLibraryDir);

        // Data directories
        rule.put(String.format("/data/data/%s", packageName), packageInfo.dataDir);
        rule.put(String.format("/data/user/%d/%s", systemUserId, packageName), 
                packageInfo.dataDir);
    }

    /**
     * Sets up ART profile directory redirects.
     */
    private void setupProfileRedirects(Map<String, String> rule, String packageName, int userId) {
        File profilesRoot = new File(PEnvironment.getVirtualRoot(), PROFILES_DIR);
        FileUtils.mkdirs(profilesRoot.getAbsolutePath());
        
        // Broad redirect as safety net
        rule.put("/data/misc/profiles", profilesRoot.getAbsolutePath());

        // Current and reference profile directories
        File profilesCurDir = new File(profilesRoot, 
            String.format("%s/%d/%s", PROFILES_CUR, userId, packageName));
        File profilesRefDir = new File(profilesRoot, 
            String.format("%s/%d/%s", PROFILES_REF, userId, packageName));
        
        FileUtils.mkdirs(profilesCurDir.getAbsolutePath());
        FileUtils.mkdirs(profilesRefDir.getAbsolutePath());
        
        rule.put(String.format("/data/misc/profiles/%s/%d/%s", PROFILES_CUR, userId, packageName), 
                profilesCurDir.getAbsolutePath());
        rule.put(String.format("/data/misc/profiles/%s/%d/%s", PROFILES_REF, userId, packageName), 
                profilesRefDir.getAbsolutePath());
    }

    /**
     * Sets up external storage redirects.
     */
    private void setupExternalStorageRedirects(Map<String, String> rule, Set<String> blackRule,
                                              int systemUserId, Context context) {
        if (PrisonCore.getContext().getExternalCacheDir() == null || 
            context.getExternalCacheDir() == null) {
            return;
        }
        
        File external = PEnvironment.getExternalUserDir(PActivityThread.getUserId());
        String externalPath = external.getAbsolutePath();

        // SD card redirects
        rule.put("/sdcard", externalPath);
        rule.put(String.format("/storage/emulated/%d", systemUserId), externalPath);

        // Blacklist Pictures directory
        blackRule.add("/sdcard/Pictures");
        blackRule.add(String.format("/storage/emulated/%d/Pictures", systemUserId));
    }

    /**
     * Sets up root detection file hiding by redirecting to fake paths.
     */
    private void setupRootHiding(Map<String, String> rule) {
        for (String rootPath : ROOT_PATHS) {
            rule.put(rootPath, rootPath + FAKE_SUFFIX);
        }
    }

    /**
     * Sets up /proc directory redirects for process information.
     */
    private void setupProcRedirects(Map<String, String> rule) {
        int appPid = PActivityThread.getAppPid();
        int currentPid = Process.myPid();
        
        String procCmdlinePath = new File(PEnvironment.getProcDir(appPid), PROC_CMDLINE)
            .getAbsolutePath();
        
        rule.put(String.format("/proc/%d/%s", currentPid, PROC_CMDLINE), procCmdlinePath);
        rule.put(PROC_SELF + PROC_CMDLINE, procCmdlinePath);
    }

    /**
     * Applies all redirect rules and blacklist rules.
     */
    private void applyRedirectRules(Map<String, String> rule, Set<String> blackRule) {
        for (Map.Entry<String, String> entry : rule.entrySet()) {
            addRedirect(entry.getKey(), entry.getValue());
        }
        
        for (String blackPath : blackRule) {
            addBlackRedirect(blackPath);
        }
    }
}
