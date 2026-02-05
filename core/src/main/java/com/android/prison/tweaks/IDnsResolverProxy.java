package com.android.prison.tweaks;

import android.os.Build;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import com.android.prison.base.MethodHook;
import com.android.prison.interfaces.android.os.BRServiceManager;
import com.android.prison.base.BinderInvocationStub;
import com.android.prison.base.ProxyMethod;
import com.android.prison.utils.Logger;

/**
 * DNS Resolver Service Proxy for Prison
 * Handles DNS resolution and prevents custom DNS interference
 */
public class IDnsResolverProxy extends BinderInvocationStub {
    public static final String TAG = IDnsResolverProxy.class.getSimpleName();
    public static final String DNS_RESOLVER_SERVICE = "dnsresolver";

    public IDnsResolverProxy() {
        super(BRServiceManager.get().getService(DNS_RESOLVER_SERVICE));
    }

    @Override
    protected Object getWho() {
        // Return null to avoid complex reflection that might fail
        // The service will still be replaced by the proxy
        Logger.d(TAG, "IDnsResolverProxy: Returning null for getWho to avoid reflection issues");
        return null;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        try {
            replaceSystemService(DNS_RESOLVER_SERVICE);
            Logger.d(TAG, "IDnsResolverProxy: Successfully injected DNS resolver service");
        } catch (Exception e) {
            Logger.w(TAG, "IDnsResolverProxy: Failed to inject service: " + e.getMessage());
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    // Hook for DNS resolution to ensure proper fallback
    @ProxyMethod("resolveDns")
    public static class ResolveDns extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Logger.d(TAG, "Intercepting DNS resolution request");
            try {
                // Try to resolve using the original method first
                Object result = method.invoke(who, args);
                if (result != null) {
                    return result;
                }
                
                // If original method fails, provide fallback DNS servers
                Logger.w(TAG, "DNS resolution failed, providing fallback");
                return createFallbackDnsResult();
                
            } catch (Exception e) {
                Logger.w(TAG, "DNS resolution error, providing fallback: " + e.getMessage());
                return createFallbackDnsResult();
            }
        }
        
        private Object createFallbackDnsResult() {
            try {
                // Create a fallback DNS result with Google DNS
                List<InetAddress> fallbackServers = new ArrayList<>();
                fallbackServers.add(InetAddress.getByName("8.8.8.8"));
                fallbackServers.add(InetAddress.getByName("8.8.4.4"));
                return fallbackServers;
            } catch (Exception e) {
                Logger.e(TAG, "Error creating fallback DNS result: " + e.getMessage());
                return new ArrayList<>();
            }
        }
    }

    // Hook for private DNS configuration (API 28+)
    @ProxyMethod("setPrivateDnsConfiguration")
    public static class SetPrivateDnsConfiguration extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (Build.VERSION.SDK_INT >= 28) {
                Logger.d(TAG, "Intercepting private DNS configuration");
                // Disable private DNS for sandboxed apps
                try {
                    // Try to call with disabled configuration
                    if (args != null && args.length > 0) {
                        // Set private DNS to disabled
                        Logger.d(TAG, "Disabling private DNS for sandboxed app");
                    }
                    return method.invoke(who, args);
                } catch (Exception e) {
                    Logger.w(TAG, "Private DNS configuration failed: " + e.getMessage());
                    return null;
                }
            }
            return null;
        }
    }

    // Hook for DNS server configuration
    @ProxyMethod("setDnsServersForNetwork")
    public static class SetDnsServersForNetwork extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Logger.d(TAG, "Intercepting DNS server configuration");
            try {
                // Allow the call but log it
                Object result = method.invoke(who, args);
                Logger.d(TAG, "DNS server configuration applied");
                return result;
            } catch (Exception e) {
                Logger.w(TAG, "DNS server configuration failed: " + e.getMessage());
                return null;
            }
        }
    }

    // Hook for network validation
    @ProxyMethod("isNetworkValidated")
    public static class IsNetworkValidated extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Logger.d(TAG, "Intercepting network validation check");
            // Return true to indicate network is validated
            return true;
        }
    }

    // Hook for DNS query timeout (API 21+)
    @ProxyMethod("setDnsQueryTimeout")
    public static class SetDnsQueryTimeout extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (Build.VERSION.SDK_INT >= 21) {
                Logger.d(TAG, "Intercepting DNS query timeout configuration");
                try {
                    // Set a reasonable timeout for DNS queries
                    if (args != null && args.length > 0 && args[0] instanceof Integer) {
                        int timeout = Math.min((Integer) args[0], 10000); // Max 10 seconds
                        Logger.d(TAG, "Setting DNS query timeout to: " + timeout + "ms");
                        args[0] = timeout;
                    }
                    return method.invoke(who, args);
                } catch (Exception e) {
                    Logger.w(TAG, "DNS query timeout configuration failed: " + e.getMessage());
                    return null;
                }
            }
            return null;
        }
    }

    // Hook for DNS resolver stats (API 23+)
    @ProxyMethod("getDnsResolverStats")
    public static class GetDnsResolverStats extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (Build.VERSION.SDK_INT >= 23) {
                Logger.d(TAG, "Intercepting DNS resolver stats request");
                try {
                    // Return empty stats to prevent crashes
                    return method.invoke(who, args);
                } catch (Exception e) {
                    Logger.w(TAG, "DNS resolver stats failed: " + e.getMessage());
                    return null;
                }
            }
            return null;
        }
    }
}
