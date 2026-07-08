package com.exploreceylon.backend.util;

import jakarta.servlet.http.HttpServletRequest;

/** Best-effort User-Agent → "Browser OS" label, for login history / session display. */
public final class DeviceInfoUtils {

    private DeviceInfoUtils() {}

    public static String describe(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "Unknown Device";

        String browser = userAgent.contains("Edg/") ? "Edge"
                : userAgent.contains("Chrome/") ? "Chrome"
                : userAgent.contains("Firefox/") ? "Firefox"
                : userAgent.contains("Safari/") ? "Safari"
                : "Unknown Browser";

        String os = userAgent.contains("Windows") ? "Windows"
                : userAgent.contains("Mac OS") ? "macOS"
                : userAgent.contains("Android") ? "Android"
                : userAgent.contains("iPhone") || userAgent.contains("iPad") ? "iOS"
                : userAgent.contains("Linux") ? "Linux"
                : "Unknown OS";

        return browser + " on " + os;
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
