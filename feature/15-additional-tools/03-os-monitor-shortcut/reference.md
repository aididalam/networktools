# OS Monitor Shortcut

## Feature: How will it work?

Selecting OS Monitor Shortcut launches the corresponding supported in-app surface or Android destination. Availability is checked before launch, and a clear fallback is shown when the destination does not exist on the device.

## Google Play Policy Requirements

- Launch OS Monitor through a targeted package or documented Android settings intent only; do not request broad installed-app visibility. Follow the [Package Visibility policy](https://support.google.com/googleplay/android-developer/answer/10158779).
- Do not install, download, update, or recommend APKs outside Google Play, and do not treat absence of the destination app as an error requiring broad package discovery.
- Show the destination before launch and provide a safe fallback when the exact package or intent handler is unavailable.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify the OS Monitor Shortcut action uses a recognizable icon or clear command label, has an appropriate enabled or disabled state, and gives immediate feedback.
- [ ] Trigger success, unavailable, and failure states; verify messages are concise, anchored to the action, and do not obscure existing results.

### 2. Functional Testing

- [ ] Invoke OS Monitor Shortcut with a supported destination and verify the exact in-app route, Android settings screen, browser URI, or external intent is opened.
- [ ] Repeat with no handler, invalid destination, restricted profile, and failed launch; verify a recoverable message and unchanged source-screen state.

- [ ] Test the exact destination installed, absent, disabled, incompatible, and without an intent handler; verify targeted resolution and a safe fallback without scanning installed apps.

### 3. Compatibility Testing

- [ ] Verify OS Monitor Shortcut on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on phone and tablet window sizes, portrait and landscape, light and dark themes, and with supported locale changes.

### 4. Performance Testing

- [ ] Measure opening and using OS Monitor Shortcut; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify OS Monitor Shortcut validates and bounds all host, address, port, subnet, resolver, URI, and provider data before network use or intent launch; malformed data must not cause injection or unsafe routing.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Inspect the manifest and runtime package queries; verify `QUERY_ALL_PACKAGES`, package installation, external APK download, and broad app-inventory collection are absent.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify OS Monitor Shortcut controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During OS Monitor Shortcut, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
