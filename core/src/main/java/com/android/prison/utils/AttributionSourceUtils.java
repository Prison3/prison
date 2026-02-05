package com.android.prison.utils;

import com.android.prison.core.PrisonCore;

/**
 * Centralized utility class for fixing AttributionSource UID issues
 * This eliminates code duplication across multiple proxy classes
 */
public class AttributionSourceUtils {
    private static final String TAG = AttributionSourceUtils.class.getSimpleName();

    /**
     * Fix AttributionSource objects in method arguments
     */
    public static void fixAttributionSourceInArgs(Object[] args) {
        if (args == null) return;
        
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg != null && arg.getClass().getName().contains("AttributionSource")) {
                try {
                    fixAttributionSourceUid(arg);
                    Logger.d(TAG, "Fixed AttributionSource UID in method arguments");
                } catch (Exception e) {
                    Logger.w(TAG, "Failed to fix AttributionSource in args: " + e.getMessage());
                }
            }
        }
        
        // Also check for Bundle objects that might contain AttributionSource
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg != null && arg.getClass().getName().contains("Bundle")) {
                try {
                    fixAttributionSourceInBundle(arg);
                } catch (Exception e) {
                    Logger.w(TAG, "Failed to fix AttributionSource in Bundle: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Fix AttributionSource UID
     */
    public static void fixAttributionSourceUid(Object attributionSource) {
        try {
            if (attributionSource == null) return;
            
            Class<?> attributionSourceClass = attributionSource.getClass();
            
            // Try multiple field names that might exist
            String[] uidFieldNames = {"mUid", "uid", "mCallingUid", "callingUid", "mSourceUid", "sourceUid"};
            
            for (String fieldName : uidFieldNames) {
                try {
                    java.lang.reflect.Field uidField = attributionSourceClass.getDeclaredField(fieldName);
                    uidField.setAccessible(true);
                    uidField.set(attributionSource, PrisonCore.getUid());
                    Logger.d(TAG, "Fixed AttributionSource UID via field: " + fieldName);
                    break;
                } catch (NoSuchFieldException e) {
                    // Continue to next field name
                }
            }
            
            // Try using setter methods if fields don't work
            try {
                java.lang.reflect.Method setUidMethod = attributionSourceClass.getDeclaredMethod("setUid", int.class);
                setUidMethod.setAccessible(true);
                setUidMethod.invoke(attributionSource, PrisonCore.getUid());
                Logger.d(TAG, "Fixed AttributionSource UID via setter method");
            } catch (Exception e) {
                // Ignore setter method errors
            }
            
            // Fix package name
            String[] packageFieldNames = {"mPackageName", "packageName", "mSourcePackage", "sourcePackage"};
            
            for (String fieldName : packageFieldNames) {
                try {
                    java.lang.reflect.Field packageField = attributionSourceClass.getDeclaredField(fieldName);
                    packageField.setAccessible(true);
                    packageField.set(attributionSource, PrisonCore.getPackageName());
                    Logger.d(TAG, "Fixed AttributionSource package name via field: " + fieldName);
                    break;
                } catch (NoSuchFieldException e) {
                    // Continue to next field name
                }
            }
            
        } catch (Exception e) {
            Logger.w(TAG, "Error fixing AttributionSource UID: " + e.getMessage());
        }
    }

    /**
     * Fix AttributionSource objects in Bundle objects
     */
    public static void fixAttributionSourceInBundle(Object bundle) {
        try {
            if (bundle == null) return;
            
            // Try to get the keySet and iterate through values
            java.lang.reflect.Method keySetMethod = bundle.getClass().getMethod("keySet");
            java.util.Set<String> keys = (java.util.Set<String>) keySetMethod.invoke(bundle);
            
            for (String key : keys) {
                try {
                    java.lang.reflect.Method getMethod = bundle.getClass().getMethod("get", String.class);
                    Object value = getMethod.invoke(bundle, key);
                    
                    if (value != null && value.getClass().getName().contains("AttributionSource")) {
                        fixAttributionSourceUid(value);
                        Logger.d(TAG, "Fixed AttributionSource UID in Bundle key: " + key);
                    }
                } catch (Exception e) {
                    // Ignore individual key errors
                }
            }
        } catch (Exception e) {
            Logger.w(TAG, "Error fixing AttributionSource in Bundle: " + e.getMessage());
        }
    }

    /**
     * Create a safe AttributionSource object
     */
    public static Object createSafeAttributionSource() {
        try {
            // Try to create a safe AttributionSource using reflection
            Class<?> attributionSourceClass = Class.forName("android.content.AttributionSource");
            
            // Try different constructor signatures
            Object attributionSource = null;
            
            try {
                // Try constructor with UID and package name
                java.lang.reflect.Constructor<?> constructor = attributionSourceClass.getDeclaredConstructor(int.class, String.class);
                constructor.setAccessible(true);
                attributionSource = constructor.newInstance(PrisonCore.getUid(), PrisonCore.getPackageName());
            } catch (Exception e) {
                try {
                    // Try default constructor
                    java.lang.reflect.Constructor<?> constructor = attributionSourceClass.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    attributionSource = constructor.newInstance();
                    
                    // Set UID and package name using reflection
                    fixAttributionSourceUid(attributionSource);
                } catch (Exception e2) {
                    Logger.w(TAG, "Could not create safe AttributionSource: " + e2.getMessage());
                    return null;
                }
            }
            
            return attributionSource;
        } catch (Exception e) {
            Logger.w(TAG, "Error creating safe AttributionSource: " + e.getMessage());
            return null;
        }
    }

    /**
     * Enhanced method to handle AttributionSource validation errors
     */
    public static boolean validateAttributionSource(Object attributionSource) {
        try {
            if (attributionSource == null) return false;
            
            // Check if UID is valid
            Class<?> attributionSourceClass = attributionSource.getClass();
            String[] uidFieldNames = {"mUid", "uid", "mCallingUid", "callingUid", "mSourceUid", "sourceUid"};
            
            for (String fieldName : uidFieldNames) {
                try {
                    java.lang.reflect.Field uidField = attributionSourceClass.getDeclaredField(fieldName);
                    uidField.setAccessible(true);
                    Object uidValue = uidField.get(attributionSource);
                    if (uidValue instanceof Integer) {
                        int uid = (Integer) uidValue;
                        if (uid > 0) {
                            Logger.d(TAG, "AttributionSource UID validation passed: " + uid);
                            return true;
                        }
                    }
                } catch (Exception e) {
                    // Continue to next field
                }
            }
            
            // If validation fails, fix the AttributionSource
            Logger.w(TAG, "AttributionSource validation failed, attempting to fix");
            fixAttributionSourceUid(attributionSource);
            return true;
            
        } catch (Exception e) {
            Logger.w(TAG, "Error validating AttributionSource: " + e.getMessage());
            return false;
        }
    }
}
