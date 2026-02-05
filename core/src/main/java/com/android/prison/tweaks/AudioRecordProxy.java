package com.android.prison.tweaks;

import java.lang.reflect.Method;

import com.android.prison.base.ClassInvocationStub;
import com.android.prison.base.MethodHook;
import com.android.prison.base.ProxyMethod;
import com.android.prison.utils.Logger;

/**
 * AudioRecord proxy to handle low-level audio recording in sandboxed apps.
 */
public class AudioRecordProxy extends ClassInvocationStub {
    public static final String TAG = AudioRecordProxy.class.getSimpleName();

    public AudioRecordProxy() {
        super();
    }

    @Override
    protected Object getWho() {
        return null; // Not needed for class method hooks
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        // Hook AudioRecord class methods directly
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    // Hook AudioRecord constructor
    @ProxyMethod("<init>")
    public static class Constructor extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Logger.d(TAG, "AudioRecord: Constructor called, allowing");
            return method.invoke(who, args);
        }
    }

    // Hook startRecording
    @ProxyMethod("startRecording")
    public static class StartRecording extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Logger.d(TAG, "AudioRecord: startRecording called, allowing");
            return method.invoke(who, args);
        }
    }

    // Hook stop
    @ProxyMethod("stop")
    public static class Stop extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Logger.d(TAG, "AudioRecord: stop called, allowing");
            return method.invoke(who, args);
        }
    }

    // Hook read
    @ProxyMethod("read")
    public static class Read extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Logger.d(TAG, "AudioRecord: read called, allowing");
            return method.invoke(who, args);
        }
    }

    // Hook release
    @ProxyMethod("release")
    public static class Release extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Logger.d(TAG, "AudioRecord: release called, allowing");
            return method.invoke(who, args);
        }
    }

    // Hook getState
    @ProxyMethod("getState")
    public static class GetState extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Logger.d(TAG, "AudioRecord: getState called, allowing");
            return method.invoke(who, args);
        }
    }
}
