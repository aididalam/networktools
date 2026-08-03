# Share Results

## Feature: How will it work?

The user can share the currently available results through Android's Sharesheet. The app builds a readable plain-text payload, excludes unavailable fields, and does not send anything until the user chooses a destination.

## Google Play Policy Requirements

- Copy or share only after an explicit user action and preview the exact eligible payload. Follow the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).
- Exclude unavailable, permission-protected, secret, credential, internal-debug, and undisclosed metadata fields.
- Never copy or share automatically after a lookup, scan, calculation, refresh, interruption, or background event.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify the Share Results action uses a recognizable icon or clear command label, has an appropriate enabled or disabled state, and gives immediate feedback.
- [ ] Trigger success, unavailable, and failure states; verify messages are concise, anchored to the action, and do not obscure existing results.

### 2. Functional Testing

- [ ] With a known General Features result, invoke Share Results and verify the Android Sharesheet receives the expected MIME type, subject, and plain-text payload.
- [ ] Cancel the Sharesheet and test with no compatible target; verify no result is lost and no automatic transmission occurs.

- [ ] Verify no clipboard write or Sharesheet launch occurs without pressing the visible action, and confirm excluded and unavailable fields never enter the payload.

### 3. Compatibility Testing

- [ ] Verify Share Results on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on phone and tablet window sizes, portrait and landscape, light and dark themes, and with supported locale changes.

### 4. Performance Testing

- [ ] Measure opening and using Share Results; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Share Results validates and bounds all host, address, port, subnet, resolver, URI, and provider data before network use or intent launch; malformed data must not cause injection or unsafe routing.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Inspect clipboard, intents, temporary files, logs, and lifecycle callbacks; verify payload creation is explicit, minimal, and not repeated after configuration or process recreation.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Share Results controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Share Results, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
