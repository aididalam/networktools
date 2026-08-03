# Copy Results

## Feature: How will it work?

The user can copy the currently available results from the General Features screen to the system clipboard. The action is enabled only when content exists and confirms completion without changing the result.

## Google Play Policy Requirements

- Copy or share only after an explicit user action and preview the exact eligible payload. Follow the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).
- Exclude unavailable, permission-protected, secret, credential, internal-debug, and undisclosed metadata fields.
- Never copy or share automatically after a lookup, scan, calculation, refresh, interruption, or background event.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify the Copy Results action uses a recognizable icon or clear command label, has an appropriate enabled or disabled state, and gives immediate feedback.
- [ ] Trigger success, unavailable, and failure states; verify messages are concise, anchored to the action, and do not obscure existing results.

### 2. Functional Testing

- [ ] With a known General Features result, invoke Copy Results and verify the clipboard text exactly matches the visible eligible fields and formatting.
- [ ] Verify the action is disabled or explains the empty state when no result exists, and that repeated copies do not mutate the source data.

- [ ] Verify no clipboard write or Sharesheet launch occurs without pressing the visible action, and confirm excluded and unavailable fields never enter the payload.

### 3. Compatibility Testing

- [ ] Verify Copy Results on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on phone and tablet window sizes, portrait and landscape, light and dark themes, and with supported locale changes.

### 4. Performance Testing

- [ ] Measure opening and using Copy Results; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Copy Results validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Inspect clipboard, intents, temporary files, logs, and lifecycle callbacks; verify payload creation is explicit, minimal, and not repeated after configuration or process recreation.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Copy Results controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Copy Results, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
