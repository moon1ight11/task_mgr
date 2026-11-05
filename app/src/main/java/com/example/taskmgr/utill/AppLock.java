package com.example.taskmgr.utill;

/** Сессионный флаг разблокировки приватного раздела. Сбрасывается при убийстве процесса. */
public final class AppLock {

    private static boolean privateUnlocked = false;

    private AppLock() {}

    public static boolean isPrivateUnlocked() {
        return privateUnlocked;
    }

    public static void setPrivateUnlocked(boolean unlocked) {
        privateUnlocked = unlocked;
    }
}