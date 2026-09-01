package rikka.shizuku.server.util;

import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ParceledListSlice;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import rikka.hidden.compat.util.SystemServiceBinder;

/**
 * Compatibility bridge for IPackageManager#getInstalledPackages on Android 17.
 *
 * Android 17 keeps the arguments but changes the Binder return type from
 * ParceledListSlice to PackageInfoList. Invoke the runtime platform interface by
 * reflection so the compile-time Android 16 hidden stub does not constrain the
 * return descriptor. PackageInfoList remains a ParceledListSlice subclass.
 */
public final class InstalledPackagesCompat {

    private static final String TAG = "InstalledPackagesCompat";
    private static final int ANDROID_13 = 33;

    private static final SystemServiceBinder<IPackageManager> PACKAGE_MANAGER =
            new SystemServiceBinder<>("package", IPackageManager.Stub::asInterface);

    private InstalledPackagesCompat() {
    }

    public static List<PackageInfo> getInstalledPackagesNoThrow(long flags, int userId) {
        try {
            return getInstalledPackages(flags, userId);
        } catch (Throwable e) {
            Log.w(TAG, "getInstalledPackages failed", e);
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public static List<PackageInfo> getInstalledPackages(long flags, int userId)
            throws ReflectiveOperationException {
        IPackageManager packageManager = PACKAGE_MANAGER.get();
        if (packageManager == null) {
            return Collections.emptyList();
        }

        Method method;
        Object result;
        if (Build.VERSION.SDK_INT >= ANDROID_13) {
            method = IPackageManager.class.getMethod("getInstalledPackages", long.class, int.class);
            result = invoke(method, packageManager, flags, userId);
        } else {
            method = IPackageManager.class.getMethod("getInstalledPackages", int.class, int.class);
            result = invoke(method, packageManager, (int) flags, userId);
        }

        if (result == null) {
            return Collections.emptyList();
        }
        if (result instanceof ParceledListSlice) {
            List<?> list = ((ParceledListSlice<?>) result).getList();
            return list == null ? Collections.emptyList() : (List<PackageInfo>) list;
        }

        throw new IllegalStateException(
                "Unsupported getInstalledPackages return type: " + result.getClass().getName());
    }

    private static Object invoke(Method method, Object receiver, Object... args)
            throws ReflectiveOperationException {
        try {
            return method.invoke(receiver, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ReflectiveOperationException) {
                throw (ReflectiveOperationException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw e;
        }
    }
}
