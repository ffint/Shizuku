package rikka.shizuku.server.util;

import android.content.pm.UserInfo;
import android.os.Build;
import android.os.IUserManager;
import android.os.RemoteException;
import android.util.ArraySet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import rikka.hidden.compat.UserManagerApis;
import rikka.hidden.compat.util.SystemServiceBinder;

/** Compatibility for the IUserManager#getUsers signature used on Android 16/17. */
public final class UserManagerCompat {

    private static final int ANDROID_16 = 36;
    private static final SystemServiceBinder<IUserManager> USER_MANAGER =
            new SystemServiceBinder<>("user", IUserManager.Stub::asInterface);

    private UserManagerCompat() {
    }

    public static List<UserInfo> getUsers(
            boolean excludePartial, boolean excludeDying, boolean excludePreCreated)
            throws RemoteException {
        if (Build.VERSION.SDK_INT >= ANDROID_16) {
            IUserManager userManager = USER_MANAGER.get();
            if (userManager == null) {
                return new ArrayList<>();
            }
            return userManager.getUsers(excludeDying);
        }
        return UserManagerApis.getUsers(excludePartial, excludeDying, excludePreCreated);
    }

    public static Collection<Integer> getUserIdsNoThrow() {
        Set<Integer> result = new ArraySet<>();
        try {
            for (UserInfo userInfo : getUsers(true, true, true)) {
                result.add(userInfo.id);
            }
        } catch (Throwable ignored) {
            result.add(0);
        }
        if (result.isEmpty()) {
            result.add(0);
        }
        return result;
    }
}
