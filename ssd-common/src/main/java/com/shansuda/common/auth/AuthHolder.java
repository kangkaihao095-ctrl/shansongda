package com.shansuda.common.auth;

public final class AuthHolder {

    private static final ThreadLocal<AuthUser> HOLDER = new ThreadLocal<>();

    private AuthHolder() {
    }

    public static void set(AuthUser user) {
        HOLDER.set(user);
    }

    public static AuthUser get() {
        return HOLDER.get();
    }

    public static AuthUser require() {
        AuthUser user = HOLDER.get();
        if (user == null) {
            throw new IllegalStateException("未登录");
        }
        return user;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
