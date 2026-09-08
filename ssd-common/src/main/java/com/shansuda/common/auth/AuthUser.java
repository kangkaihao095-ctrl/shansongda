package com.shansuda.common.auth;

public record AuthUser(long userId, String role, Long riderId) {

    public boolean isRider() {
        return "RIDER".equals(role);
    }

    public boolean isMerchant() {
        return "MERCHANT".equals(role);
    }

    public boolean isUser() {
        return "USER".equals(role);
    }
}
