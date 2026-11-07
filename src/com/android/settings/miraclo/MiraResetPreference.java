/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SeekBarPreference;
import androidx.preference.TwoStatePreference;

import com.android.settings.R;

import org.json.JSONObject;

import java.lang.reflect.Field;

/**
 * "Reset to default" at the bottom of a Mira Studio page. After asking, it puts every setting on
 * the page back to how MIRACLO ships it: the page's switches, lists and sliders take their XML
 * default again, the settings the page's own controls keep (listed per page below) are cleared,
 * and the page reopens showing the defaults.
 */
public class MiraResetPreference extends Preference {

    public MiraResetPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSelectable(false);
        setPersistent(false);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        ViewGroup root = (ViewGroup) holder.itemView;
        root.removeAllViews();
        root.setBackground(null);
        final float d = getContext().getResources().getDisplayMetrics().density;
        root.setPadding((int) (16 * d), (int) (20 * d), (int) (16 * d), (int) (28 * d));
        boolean dark = (getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        TextView button = new TextView(getContext());
        button.setText(R.string.miraclo_reset_page);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setTypeface(Typeface.create(Typeface.create("mira-sans", Typeface.NORMAL), 600, false));
        // Material 3 error container: clear that it undoes things, without shouting.
        button.setTextColor(dark ? 0xFFFFB4AB : 0xFF8C1D18);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(dark ? 0xFF3B1D20 : 0xFFFFDAD6);
        bg.setCornerRadius(28 * d);
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(dark ? 0x33FFB4AB : 0x228C1D18), bg, null));
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> confirm());
        root.addView(button, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (56 * d)));
        holder.itemView.post(() -> holder.itemView.setBackground(null));
    }

    private void confirm() {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.miraclo_reset_page_title)
                .setMessage(R.string.miraclo_reset_page_message)
                .setPositiveButton(R.string.miraclo_reset_page_confirm, (dlg, w) -> reset())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void reset() {
        Context ctx = getContext();
        ContentResolver cr = ctx.getContentResolver();
        PreferenceGroup screen = getPreferenceManager().getPreferenceScreen();
        resetGroup(screen, cr);
        String page = screen.getKey() == null ? "" : screen.getKey();
        // Settings the page's own controls (drawn in Compose) keep.
        int id = ctx.getResources().getIdentifier("miraclo_reset_" + page, "array", ctx.getPackageName());
        if (id != 0) {
            for (String key : ctx.getResources().getStringArray(id)) {
                Settings.Secure.putString(cr, key, null);
            }
        }
        resetSpecial(page, ctx, cr);
        Activity a = activity(ctx);
        if (a != null) a.recreate();
    }

    private static void resetGroup(PreferenceGroup group, ContentResolver cr) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference p = group.getPreference(i);
            if (p instanceof PreferenceGroup) {
                resetGroup((PreferenceGroup) p, cr);
                continue;
            }
            if (p.getKey() == null || p instanceof MiraResetPreference) continue;
            Object def = defaultOf(p);
            if (p instanceof TwoStatePreference && def instanceof Boolean) {
                ((TwoStatePreference) p).setChecked((Boolean) def);
            } else if (p instanceof ListPreference && def instanceof String) {
                ((ListPreference) p).setValue((String) def);
            } else if (p instanceof SeekBarPreference && def instanceof Integer) {
                ((SeekBarPreference) p).setValue((Integer) def);
            } else if (p.getPreferenceDataStore() instanceof SecureSettingsDataStore) {
                Settings.Secure.putString(cr, p.getKey(), null);
            }
        }
    }

    /** The android:defaultValue a preference was inflated with. */
    private static Object defaultOf(Preference p) {
        try {
            Field f = Preference.class.getDeclaredField("mDefaultValue");
            f.setAccessible(true);
            return f.get(p);
        } catch (Exception e) {
            return null;
        }
    }

    /** Pages whose defaults live outside Settings.Secure. */
    private static void resetSpecial(String page, Context ctx, ContentResolver cr) {
        switch (page) {
            case "miraclo_studio_tuning_display":
                // Adaptive refresh rate.
                Settings.System.putFloat(cr, "min_refresh_rate", 0f);
                Settings.System.putString(cr, "peak_refresh_rate", null);
                break;
            case "miraclo_studio_fonts":
                Settings.System.putFloat(cr, Settings.System.FONT_SCALE, 1f);
                Settings.Secure.putInt(cr, Settings.Secure.FONT_WEIGHT_ADJUSTMENT, 0);
                break;
            case "miraclo_studio_home":
                // Round icons and no themed icons, through the launcher's own settings.
                new com.android.settings.miraclo.studio.MiraStudioResetBridge(ctx).resetLauncher();
                com.android.settings.miraclo.studio.MiraStudioPacks.INSTANCE.reset(ctx);
                break;
            case "miraclo_studio_colors":
                // Colours from the wallpaper again; the icon pack and font choices stay.
                try {
                    String key = "theme_customization_overlay_packages";
                    String old = Settings.Secure.getString(cr, key);
                    JSONObject json = old == null ? new JSONObject() : new JSONObject(old);
                    json.remove("android.theme.customization.system_palette");
                    json.remove("android.theme.customization.accent_color");
                    json.remove("android.theme.customization.color_index");
                    json.put("android.theme.customization.color_source", "home_wallpaper");
                    json.put("_applied_timestamp", System.currentTimeMillis());
                    Settings.Secure.putString(cr, key, json.toString());
                } catch (Exception ignored) {
                }
                break;
            default:
                break;
        }
    }

    private static Activity activity(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }
}
