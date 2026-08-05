# System Wi-Fi QR Sharing

## Feature: How will it work?

The currently connected access point displays a QR icon instead of the bookmark star. The Saved tab also provides an Android saved-networks action and QR actions for locally observed entries. Each action first explains that Android owns the password and QR code; after consent, NetworkTools opens Android Wi-Fi Settings, where the user selects the network and uses the system Share/QR control. Android may require screen-lock authentication. No credential or QR payload returns to NetworkTools.

## Platform and Google Play Requirements

- A normal app targeting Android 10 or later cannot enumerate the complete configured-network list with `WifiManager.getConfiguredNetworks()`; the API returns an empty list except for documented privileged roles.
- There is no public Android intent that opens a specific network's protected QR screen. Use the documented `Settings.ACTION_WIFI_SETTINGS`, handle missing activities, and accurately explain the remaining system steps.
- Obtain affirmative consent before leaving the app, but do not describe consent as permission to bypass Android credential protections.
- Never read, request, persist, log, upload, analyze, or place Wi-Fi passwords or QR credentials in analytics, clipboard, intents, backups, or screenshots. Follow the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).

## Test Cases

### 1. UI/UX Testing
- [ ] Verify the connected access point uses a QR icon while unconnected bookmarkable access points retain the star icon.
- [ ] Verify the dialog clearly names Android Settings, screen-lock authentication, and that NetworkTools cannot see credentials.

### 2. Functional Testing
- [ ] Cancel each QR action and verify no settings activity opens; confirm it and verify Android Wi-Fi Settings opens.
- [ ] In Android Settings, select a saved network and verify the system QR/password workflow behaves according to the device OEM and Android version.

### 3. Compatibility Testing
- [ ] Verify Settings navigation and system authentication on supported Pixel and Moto devices and across the minimum, target, and latest Android APIs.
- [ ] Verify graceful behavior when the Wi-Fi Settings activity or OEM QR feature is unavailable.

### 4. Performance Testing
- [ ] Repeatedly open and cancel the consent dialog and verify no retained activities, callbacks, or memory growth.

### 5. Security Testing
- [ ] Inspect app storage, logs, clipboard, intents, screenshots, analytics, and backups; verify no password or QR payload enters NetworkTools.
- [ ] Verify crafted SSIDs cannot inject intent actions, extras, URIs, dialog markup, or accessibility descriptions.

### 6. Accessibility Testing
- [ ] Verify TalkBack announces the connected-network QR action, consent dialog, Cancel, and Open Wi-Fi Settings in logical order.
- [ ] Verify focus, contrast, touch targets, dark/light themes, and 200% text scaling.

### 7. Interruption Testing
- [ ] Rotate, background, lock, switch Wi-Fi, or recreate the process while the dialog or Settings is open; verify consent is not replayed and no stale credential claim is shown.
