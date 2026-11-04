package com.android.settings.deviceinfo;

import android.content.Context;
import android.os.SystemProperties;

import com.android.settings.core.BasePreferenceController;

public class MiraCLOVersionPreferenceController extends BasePreferenceController {

    private static final String PROP = "ro.miraclo.version";
    private static final String CODENAME_PROP = "ro.miraclo.codename";

    public MiraCLOVersionPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return SystemProperties.get(PROP).isEmpty() ? UNSUPPORTED_ON_DEVICE : AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        return formatVersion();
    }

    /**
     * The MIRACLO version as shown to users, e.g. "1.0 (Aurora)" or "1.0 Beta (Aurora)".
     * Built from ro.miraclo.version ("1.0", or "1.0-beta" with a variant: "_" becomes a space and
     * the variant is capitalised) and ro.miraclo.codename.
     */
    public static String formatVersion() {
        String v = SystemProperties.get(PROP);
        String[] p = v.split("-", 2);
        String text;
        if (p.length < 2 || p[1].isEmpty()) {
            text = v;
        } else {
            String variant = p[1].replace('_', ' ');
            text = p[0] + " " + Character.toUpperCase(variant.charAt(0)) + variant.substring(1);
        }
        String codename = SystemProperties.get(CODENAME_PROP);
        return codename.isEmpty() ? text : text + " (" + codename + ")";
    }
}
