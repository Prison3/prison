package com.android.prison.manager;

import java.util.HashMap;
import java.util.Map;

import com.android.prison.base.IInjector;
import com.android.prison.tweaks.AppInstrumentation;
import com.android.prison.tweaks.HCallbackProxy;
import com.android.prison.tweaks.IAccessibilityManagerProxy;
import com.android.prison.tweaks.IAccountManagerProxy;
import com.android.prison.tweaks.IActivityClientProxy;
import com.android.prison.tweaks.IActivityManagerProxy;
import com.android.prison.tweaks.IActivityTaskManagerProxy;
import com.android.prison.tweaks.IAlarmManagerProxy;
import com.android.prison.tweaks.IAppOpsManagerProxy;
import com.android.prison.tweaks.IAppWidgetManagerProxy;
import com.android.prison.tweaks.IAttributionSourceProxy;
import com.android.prison.tweaks.IAutofillManagerProxy;
import com.android.prison.tweaks.ISensitiveContentProtectionManagerProxy;
import com.android.prison.tweaks.ISettingsSystemProxy;
import com.android.prison.tweaks.IConnectivityManagerProxy;
import com.android.prison.tweaks.ISystemSensorManagerProxy;
import com.android.prison.tweaks.IContentProviderProxy;
import com.android.prison.tweaks.IXiaomiAttributionSourceProxy;
import com.android.prison.tweaks.IXiaomiSettingsProxy;
import com.android.prison.tweaks.IXiaomiMiuiServicesProxy;
import com.android.prison.tweaks.IDnsResolverProxy;
import com.android.prison.tweaks.IContextHubServiceProxy;
import com.android.prison.tweaks.IDeviceIdentifiersPolicyProxy;
import com.android.prison.tweaks.IDevicePolicyManagerProxy;
import com.android.prison.tweaks.IDisplayManagerProxy;
import com.android.prison.tweaks.IFingerprintManagerProxy;
import com.android.prison.tweaks.IGraphicsStatsProxy;
import com.android.prison.tweaks.IJobServiceProxy;
import com.android.prison.tweaks.ILauncherAppsProxy;
import com.android.prison.tweaks.ILocationManagerProxy;
import com.android.prison.tweaks.IMediaRouterServiceProxy;
import com.android.prison.tweaks.IMediaSessionManagerProxy;
import com.android.prison.tweaks.IAudioServiceProxy;
import com.android.prison.tweaks.ISensorPrivacyManagerProxy;
import com.android.prison.tweaks.ContentResolverProxy;
import com.android.prison.tweaks.IWebViewUpdateServiceProxy;
import com.android.prison.tweaks.IMiuiSecurityManagerProxy;
import com.android.prison.tweaks.SystemLibraryProxy;
import com.android.prison.tweaks.ReLinkerProxy;
import com.android.prison.tweaks.WebViewProxy;
import com.android.prison.tweaks.WebViewFactoryProxy;
import com.android.prison.tweaks.MediaRecorderProxy;
import com.android.prison.tweaks.AudioRecordProxy;
import com.android.prison.tweaks.MediaRecorderClassProxy;
import com.android.prison.tweaks.SQLiteDatabaseProxy;
import com.android.prison.tweaks.ClassLoaderProxy;
import com.android.prison.tweaks.FileSystemProxy;
import com.android.prison.tweaks.GmsProxy;
import com.android.prison.tweaks.LevelDbProxy;
import com.android.prison.tweaks.DeviceIdProxy;
import com.android.prison.tweaks.GoogleAccountManagerProxy;
import com.android.prison.tweaks.AuthenticationProxy;
import com.android.prison.tweaks.AndroidIdProxy;
import com.android.prison.tweaks.AudioPermissionProxy;
import com.android.prison.tweaks.INetworkManagementServiceProxy;
import com.android.prison.tweaks.INotificationManagerProxy;
import com.android.prison.tweaks.IPackageManagerProxy;
import com.android.prison.tweaks.IPermissionManagerProxy;
import com.android.prison.tweaks.IPersistentDataBlockServiceProxy;
import com.android.prison.tweaks.IPhoneSubInfoProxy;
import com.android.prison.tweaks.IPowerManagerProxy;
import com.android.prison.tweaks.ApkAssetsProxy;
import com.android.prison.tweaks.ResourcesManagerProxy;
import com.android.prison.tweaks.IShortcutManagerProxy;
import com.android.prison.tweaks.IStorageManagerProxy;
import com.android.prison.tweaks.IStorageStatsManagerProxy;
import com.android.prison.tweaks.ISystemUpdateProxy;
import com.android.prison.tweaks.ITelephonyManagerProxy;
import com.android.prison.tweaks.ITelephonyRegistryProxy;
import com.android.prison.tweaks.IUserManagerProxy;
import com.android.prison.tweaks.IVibratorServiceProxy;
import com.android.prison.tweaks.IVpnManagerProxy;
import com.android.prison.tweaks.IWifiManagerProxy;
import com.android.prison.tweaks.IWifiScannerProxy;
import com.android.prison.tweaks.IWindowManagerProxy;
import com.android.prison.tweaks.ContentServiceStub;
import com.android.prison.tweaks.RestrictionsManagerStub;
import com.android.prison.tweaks.OsStub;
import com.android.prison.tweaks.ISettingsProviderProxy;
import com.android.prison.tweaks.FeatureFlagUtilsProxy;
import com.android.prison.tweaks.WorkManagerProxy;
import com.android.prison.utils.Logger;
import com.android.prison.utils.BuildCompat;

public class InjectorManager {
    public static final String TAG = InjectorManager.class.getSimpleName();

    private static final InjectorManager sInjectorManager = new InjectorManager();

    private final Map<Class<?>, IInjector> mInjectors = new HashMap<>();

    public static InjectorManager get() {
        return sInjectorManager;
    }

    private static final Class<? extends IInjector>[] INJECTOR_CLASSES = new Class[]{
            IDisplayManagerProxy.class,
            OsStub.class,
            IActivityManagerProxy.class,
            IPackageManagerProxy.class,
            ITelephonyManagerProxy.class,
            HCallbackProxy.class,
            IAppOpsManagerProxy.class,
            INotificationManagerProxy.class,
            IAlarmManagerProxy.class,
            IAppWidgetManagerProxy.class,
            ContentServiceStub.class,
            IWindowManagerProxy.class,
            IUserManagerProxy.class,
            RestrictionsManagerStub.class,
            IMediaSessionManagerProxy.class,
            IAudioServiceProxy.class,
            ISensorPrivacyManagerProxy.class,
            ContentResolverProxy.class,
            IWebViewUpdateServiceProxy.class,
            SystemLibraryProxy.class,
            ReLinkerProxy.class,
            WebViewProxy.class,
            WebViewFactoryProxy.class,
            WorkManagerProxy.class,
            MediaRecorderProxy.class,
            AudioRecordProxy.class,
            IMiuiSecurityManagerProxy.class,
            ISettingsProviderProxy.class,
            FeatureFlagUtilsProxy.class,
            MediaRecorderClassProxy.class,
            SQLiteDatabaseProxy.class,
            ClassLoaderProxy.class,
            FileSystemProxy.class,
            GmsProxy.class,
            LevelDbProxy.class,
            DeviceIdProxy.class,
            GoogleAccountManagerProxy.class,
            AuthenticationProxy.class,
            AndroidIdProxy.class,
            AudioPermissionProxy.class,
            ILocationManagerProxy.class,
            IStorageManagerProxy.class,
            ILauncherAppsProxy.class,
            IJobServiceProxy.class,
            IAccessibilityManagerProxy.class,
            ITelephonyRegistryProxy.class,
            IDevicePolicyManagerProxy.class,
            IAccountManagerProxy.class,
            IConnectivityManagerProxy.class,
            IDnsResolverProxy.class,
            IAttributionSourceProxy.class,
            IContentProviderProxy.class,
            ISettingsSystemProxy.class,
            ISystemSensorManagerProxy.class,
            // Xiaomi-specific proxies to prevent crashes on MIUI devices
            IXiaomiAttributionSourceProxy.class,
            IXiaomiSettingsProxy.class,
            IXiaomiMiuiServicesProxy.class,
            IPhoneSubInfoProxy.class,
            IMediaRouterServiceProxy.class,
            IPowerManagerProxy.class,
            IContextHubServiceProxy.class,
            IVibratorServiceProxy.class,
            IPersistentDataBlockServiceProxy.class,
            /*
            * It takes time to test and enhance the compatibility of WifiManager
            * (only tested in Android 10).
            * commented by Prisoning at 2022/03/08
            * */
            IWifiManagerProxy.class,
            IWifiScannerProxy.class,
            ApkAssetsProxy.class,
            ResourcesManagerProxy.class,
            IVpnManagerProxy.class,
            IPermissionManagerProxy.class,
            IActivityTaskManagerProxy.class,
            ISystemUpdateProxy.class,
            IAutofillManagerProxy.class,
            IDeviceIdentifiersPolicyProxy.class,
            IStorageStatsManagerProxy.class,
            IShortcutManagerProxy.class,
            INetworkManagementServiceProxy.class,
            IFingerprintManagerProxy.class,
            IGraphicsStatsProxy.class,
            IJobServiceProxy.class
    };

    public void inject() {
        Logger.d(TAG, "inject: Starting injector initialization");
        int successCount = 0;
        int failCount = 0;

        // Register standard injectors from class array
        for (Class<? extends IInjector> clazz : INJECTOR_CLASSES) {
            try {
                IInjector injector = clazz.getDeclaredConstructor().newInstance();
                registerInjector(injector);
                successCount++;
        } catch (Exception e) {
            failCount++;
            Logger.e(TAG, "inject: Failed to instantiate injector " + clazz.getSimpleName(), e);
            }
        }

        // Register special cases that require custom instantiation
        try {
            registerInjector(AppInstrumentation.get());
            successCount++;
        } catch (Exception e) {
            failCount++;
            Logger.e(TAG, "inject: Failed to register AppInstrumentation", e);
        }

        try {
            registerInjector(new IActivityClientProxy(null));
            successCount++;
        } catch (Exception e) {
            failCount++;
            Logger.e(TAG, "inject: Failed to register IActivityClientProxy", e);
        }
        // 14.0 (Safe to try on S+, will skip if service missing)
        if (BuildCompat.isS()) {
            registerInjector(new ISensitiveContentProtectionManagerProxy());
        }

        Logger.d(TAG, "inject: Completed - Success: " + successCount + ", Failed: " + failCount);
        
        // Inject all registered injectors
        Logger.d(TAG, "inject: Starting injection for " + mInjectors.size() + " injectors");
        int injectSuccessCount = 0;
        int injectFailCount = 0;

        for (IInjector injector : mInjectors.values()) {
            try {
                injector.inject();
                injectSuccessCount++;
            } catch (Exception e) {
                injectFailCount++;
                Logger.e(TAG, "inject: Failed to inject " + injector.getClass().getSimpleName(), e);
                handleInjectionFailure(injector, e);
            }
        }

        Logger.d(TAG, "inject: Injection completed - Success: " + injectSuccessCount + ", Failed: " + injectFailCount);
    }

    public void checkEnvironment(Class<?> clazz) {
        IInjector injector = mInjectors.get(clazz);
        if (injector != null && injector.isBadEnv()) {
            Logger.d(TAG, "checkEnvironment: " + clazz.getSimpleName() + " detected bad environment, injecting");
            injector.inject();
        }
    }

    public void checkAllEnvironments() {
        Logger.d(TAG, "checkAllEnvironments: Checking all injector environments");
        int injectedCount = 0;
        for (Class<?> clazz : mInjectors.keySet()) {
            IInjector injector = mInjectors.get(clazz);
            if (injector != null && injector.isBadEnv()) {
                Logger.d(TAG, "checkAllEnvironments: " + clazz.getSimpleName() + " detected bad environment, injecting");
                injector.inject();
                injectedCount++;
            }
        }
        Logger.d(TAG, "checkAllEnvironments: Completed - Injected: " + injectedCount);
    }

    void registerInjector(IInjector injector) {
        if (injector == null) {
            Logger.w(TAG, "registerInjector: Attempted to register null injector");
            return;
        }
        Class<?> clazz = injector.getClass();
        IInjector existing = mInjectors.put(clazz, injector);
        if (existing != null) {
            Logger.w(TAG, "registerInjector: Replaced existing injector for " + clazz.getSimpleName());
        }
    }

    /**
     * Enhanced error handling for injection failures
     */
    private void handleInjectionFailure(IInjector injector, Exception e) {
        String injectorName = injector.getClass().getSimpleName();
        Logger.e(TAG, "handleInjectionFailure: Injection failed for " + injectorName + " - " + e.getMessage(), e);

        // Special handling for critical injectors that could cause crashes
        if (isCriticalInjector(injectorName)) {
            Logger.w(TAG, "handleInjectionFailure: Critical injector " + injectorName + " failed, attempting recovery");
            try {
                if (injector.isBadEnv()) {
                    Logger.d(TAG, "handleInjectionFailure: Attempting to recover injector " + injectorName);
                    injector.inject();
                    Logger.d(TAG, "handleInjectionFailure: Successfully recovered injector " + injectorName);
                }
            } catch (Exception recoveryException) {
                Logger.e(TAG, "handleInjectionFailure: Injector recovery failed for " + injectorName, recoveryException);
            }
        }
    }

    private boolean isCriticalInjector(String injectorName) {
        return injectorName.contains("ActivityManager") ||
               injectorName.contains("PackageManager") ||
               injectorName.contains("WebView") ||
               injectorName.contains("ContentProvider");
    }

    /**
     * Check if all critical injectors are properly installed
     */
    public boolean areCriticalInjectorsInstalled() {
        String[] criticalInjectors = {
            "IActivityManagerProxy",
            "IPackageManagerProxy",
            "WebViewProxy",
            "IContentProviderProxy"
        };

        Logger.d(TAG, "areCriticalInjectorsInstalled: Checking " + criticalInjectors.length + " critical injectors");
        for (String injectorName : criticalInjectors) {
            boolean found = false;
            for (Class<?> injectorClass : mInjectors.keySet()) {
                if (injectorClass.getSimpleName().equals(injectorName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                Logger.w(TAG, "areCriticalInjectorsInstalled: Critical injector missing - " + injectorName);
                return false;
            }
        }

        Logger.d(TAG, "areCriticalInjectorsInstalled: All critical injectors are installed");
        return true;
    }

    /**
     * Force re-initialization of all injectors
     */
    public void reinitializeInjectors() {
        Logger.d(TAG, "reinitializeInjectors: Starting injector reinitialization");
        int previousCount = mInjectors.size();

        // Clear existing injectors
        mInjectors.clear();
        Logger.d(TAG, "reinitializeInjectors: Cleared " + previousCount + " existing injectors");

        // Re-initialize
        inject();

        Logger.d(TAG, "reinitializeInjectors: Injector reinitialization completed - New count: " + mInjectors.size());
    }
}
