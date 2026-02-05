package com.android.prison.tweaks;

import android.database.sqlite.SQLiteException;

import java.lang.reflect.Method;

import com.android.prison.base.ClassInvocationStub;
import com.android.prison.base.MethodHook;
import com.android.prison.base.ProxyMethod;
import com.android.prison.utils.Logger;

/**
 * SQLiteDatabase proxy to handle database-related issues in sandboxed apps.
 */
public class SQLiteDatabaseProxy extends ClassInvocationStub {
    public static final String TAG = SQLiteDatabaseProxy.class.getSimpleName();

    public SQLiteDatabaseProxy() {
        super();
    }

    @Override
    protected Object getWho() {
        return null; // Not needed for class method hooks
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        // Hook SQLiteDatabase class methods directly
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    // Hook rawQuery to handle missing tables gracefully
    @ProxyMethod("rawQuery")
    public static class RawQuery extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                String sql = (String) args[0];
                if (sql != null && sql.contains("DurableJob")) {
                    Logger.w(TAG, "SQLiteDatabase: rawQuery called with DurableJob table, returning empty cursor");
                    // Return an empty cursor instead of crashing
                    return null;
                }
                return method.invoke(who, args);
            } catch (SQLiteException e) {
                Logger.w(TAG, "SQLiteDatabase: rawQuery failed, returning null", e);
                return null;
            } catch (Exception e) {
                Logger.e(TAG, "SQLiteDatabase: rawQuery error", e);
                return method.invoke(who, args);
            }
        }
    }

    // Hook query to handle missing tables gracefully
    @ProxyMethod("query")
    public static class Query extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                String table = (String) args[0];
                if (table != null && table.equals("DurableJob")) {
                    Logger.w(TAG, "SQLiteDatabase: query called on DurableJob table, returning empty cursor");
                    // Return an empty cursor instead of crashing
                    return null;
                }
                return method.invoke(who, args);
            } catch (SQLiteException e) {
                Logger.w(TAG, "SQLiteDatabase: query failed, returning null", e);
                return null;
            } catch (Exception e) {
                Logger.e(TAG, "SQLiteDatabase: query error", e);
                return method.invoke(who, args);
            }
        }
    }

    // Hook execSQL to handle missing tables gracefully
    @ProxyMethod("execSQL")
    public static class ExecSQL extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                String sql = (String) args[0];
                if (sql != null && sql.contains("DurableJob")) {
                    Logger.w(TAG, "SQLiteDatabase: execSQL called with DurableJob table, ignoring");
                    // Ignore DurableJob table operations
                    return null;
                }
                return method.invoke(who, args);
            } catch (SQLiteException e) {
                Logger.w(TAG, "SQLiteDatabase: execSQL failed, ignoring", e);
                return null;
            } catch (Exception e) {
                Logger.e(TAG, "SQLiteDatabase: execSQL error", e);
                return method.invoke(who, args);
            }
        }
    }
}
