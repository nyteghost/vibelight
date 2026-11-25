package com.limelight.tv.logic;

/** Local, account-free greeting from the device clock and an optional saved name. */
public final class Greeting {
    private Greeting() {}

    public static String forHour(int hour, String name) {
        String base = hour >= 5 && hour < 12 ? "Good morning" : hour >= 12 && hour < 18 ? "Good afternoon" : "Good evening";
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() > 32) trimmed = trimmed.substring(0, 32);
        return trimmed.isEmpty() ? base : base + ", " + trimmed;
    }
}
