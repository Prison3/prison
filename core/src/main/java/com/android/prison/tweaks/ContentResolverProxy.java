package com.android.prison.tweaks;

import android.net.Uri;

import java.lang.reflect.Method;

import com.android.prison.base.MethodHook;
import com.android.prison.core.PrisonCore;
import com.android.prison.base.ProxyMethod;
import com.android.prison.base.ClassInvocationStub;
import com.android.prison.utils.Logger;

/**
 * ContentResolver proxy to handle MediaStore audio queries in virtualized apps.
 */
public class ContentResolverProxy extends ClassInvocationStub {
    public static final String TAG = ContentResolverProxy.class.getSimpleName();

    public ContentResolverProxy() {
        super();
    }

    @Override
    protected Object getWho() {
        // Return the ContentResolver from the context
        return PrisonCore.getContext().getContentResolver();
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        // Hook ContentResolver class methods directly
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    // Hook ContentResolver.query() for audio URIs
    @ProxyMethod("query")
    public static class Query extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof Uri) {
                Uri uri = (Uri) args[0];
                String uriString = uri.toString();
                
                // Check if this is an audio-related query
                if (uriString.contains("audio") || uriString.contains("media") || 
                    uriString.contains("content://media/external/audio") ||
                    uriString.contains("content://media/internal/audio") ||
                    uriString.contains("content://media/external/file") ||
                    uriString.contains("content://media/internal/file")) {
                    
                    Logger.d(TAG, "ContentResolver: Allowing audio query: " + uriString);
                    
                    // Allow audio queries to proceed normally instead of returning empty cursor
                    return method.invoke(who, args);
                }
            }
            
            // For non-audio queries, proceed normally
            return method.invoke(who, args);
        }
    }

    // Hook ContentResolver.query() with projection for audio URIs
    @ProxyMethod("query")
    public static class QueryWithProjection extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 1 && args[0] instanceof Uri) {
                Uri uri = (Uri) args[0];
                String uriString = uri.toString();
                
                // Check if this is an audio-related query
                if (uriString.contains("audio") || uriString.contains("media") || 
                    uriString.contains("content://media/external/audio") ||
                    uriString.contains("content://media/internal/audio") ||
                    uriString.contains("content://media/external/file") ||
                    uriString.contains("content://media/internal/file")) {
                    
                    Logger.d(TAG, "ContentResolver: Allowing audio query with projection: " + uriString);
                    
                    // Allow audio queries to proceed normally instead of returning empty cursor
                    return method.invoke(who, args);
                }
            }
            
            // For non-audio queries, proceed normally
            return method.invoke(who, args);
        }
    }

    // Hook ContentResolver.insert() for audio URIs
    @ProxyMethod("insert")
    public static class Insert extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof Uri) {
                Uri uri = (Uri) args[0];
                String uriString = uri.toString();
                
                if (uriString.contains("audio") || uriString.contains("media")) {
                    Logger.d(TAG, "ContentResolver: insert called for audio URI: " + uriString);
                }
            }
            
            return method.invoke(who, args);
        }
    }

    // Hook ContentResolver.update() for audio URIs
    @ProxyMethod("update")
    public static class Update extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof Uri) {
                Uri uri = (Uri) args[0];
                String uriString = uri.toString();
                
                if (uriString.contains("audio") || uriString.contains("media")) {
                    Logger.d(TAG, "ContentResolver: update called for audio URI: " + uriString);
                }
            }
            
            return method.invoke(who, args);
        }
    }

    // Hook ContentResolver.delete() for audio URIs
    @ProxyMethod("delete")
    public static class Delete extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof Uri) {
                Uri uri = (Uri) args[0];
                String uriString = uri.toString();
                
                if (uriString.contains("audio") || uriString.contains("media")) {
                    Logger.d(TAG, "ContentResolver: delete called for audio URI: " + uriString);
                }
            }
            
            return method.invoke(who, args);
        }
    }
}
