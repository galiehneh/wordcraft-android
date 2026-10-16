package com.galiehneh.wordcraft.web;

import java.util.Locale;

/** Decides what to do with a navigation or openExternal target. Pure. */
public final class NavPolicy {
    public static final String APP_HOST = "appassets.androidplatform.net";

    public enum Action { ALLOW_IN_APP, OPEN_EXTERNAL, BLOCK }

    private NavPolicy() {}

    public static Action classify(String scheme, String host) {
        if (scheme == null) return Action.BLOCK;
        String s = scheme.toLowerCase(Locale.US);
        if (s.equals("https") && APP_HOST.equalsIgnoreCase(host)) return Action.ALLOW_IN_APP;
        if (s.equals("http") || s.equals("https") || s.equals("mailto")) return Action.OPEN_EXTERNAL;
        return Action.BLOCK;
    }

    /** openExternal from JS may never target the app origin itself. */
    public static boolean canOpenExternally(String scheme, String host) {
        return classify(scheme, host) == Action.OPEN_EXTERNAL;
    }
}
