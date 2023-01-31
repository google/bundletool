# bundletool

Bundletool is a tool to manipulate Android App Bundles and Android SDK Bundles.

The **Android App Bundle** is a
[format](https://developer.android.com/guide/app-bundle/app-bundle-format) for
publishing Android apps in app distribution stores such as Google Play.

The **Android SDK Bundle** is a
[format](https://developer.android.com/studio/command-line/bundletool#asb-format)
for publishing
[runtime-enabled SDKs](https://developer.android.com/design-for-safety/privacy-sandbox/sdk-runtime)
to SDK distribution platforms such as Google Play SDK Console.

Bundletool has a few different responsibilities:

*   **Build an Android App Bundle** from pre-compiled modules of a project.

*   **Generate an APK Set archive** containing APKs for all possible devices.

*   **Extract APK(s)** from the APK Set compatible with a given device.

*   **Install APK(s)** from the APK Set compatible with a connected device.

*   **Extract device spec** from a device as a JSON file.

*   **Add code transparency** to an Android App Bundle. Code transparency is an
    optional code signing mechanism.

*   **Verify code transparency** inside an Android App Bundle, APK files or an
    application installed on a connected device.

*   **Build an Android SDK Bundle** from a pre-compiled module of a project.

*   **Generate an APK Set archive** containing the SDK APKs for all devices.

*   **Generate an
    [Android SDK archive](https://developer.android.com/studio/command-line/bundletool#asar-format)**,
    a Maven-friendly representation of an Android SDK Bundle.

Read more about the App Bundle format and Bundletool's usage at
[g.co/androidappbundle](https://g.co/androidappbundle)

Documentation of bundletool commands can be found at:
https://developer.android.com/studio/command-line/bundletool

## Releases

Latest release: [1.14.0](https://github.com/google/bundletool/releases)
