# MIRACLO: Settings

Android 17 Settings from Qualcomm CLO with **Mira Studio**. Branch: `aurora`.
Private: access on request (see [MIRACLO](https://github.com/MIRACLO-Project/MIRACLO)).

## Where MIRACLO's work lives

| Area | Path |
|---|---|
| Mira Studio home, cards and every page | `src/com/android/settings/miraclo/studio/`, `res/xml/miraclo_studio_*.xml` |
| Settings stored in Settings.Secure (switches, lists, sliders) | `src/com/android/settings/miraclo/` |
| Status bar icon style (theme icon pack slot) | `src/com/android/settings/miraclo/MiraIconStylePreference.java` |
| Island, Liquid Glass | `src/com/android/settings/notification/MiraCLOIslandSettings.java`, `src/com/android/settings/display/MiraCLOLiquidGlassSettings.java` |
| About phone banner, MIRACLO version | `src/com/android/settings/deviceinfo/` |
| Texts | `res/values/miraclo_strings.xml` |

Testing a Settings build with `adb install -r` leaves an update in `/data/app` that hides later
system builds. Remove it afterwards: `adb shell pm uninstall-system-updates com.android.settings`.
