# Web Tools

## Feature: How will it work?

Selecting Web Tools launches the corresponding supported in-app surface or Android destination. Availability is checked before launch, and a clear fallback is shown when the destination does not exist on the device.

## Google Play Policy Requirements

- Open only documented HTTPS destinations through the external browser or a tightly allowlisted safe surface; do not load arbitrary HTTP or untrusted content with a JavaScript bridge. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Show the destination domain before leaving the app, validate redirects and intents, and provide a safe failure when no handler exists.
- Do not append network identifiers, results, clipboard data, or personal data to URLs unless the user explicitly chooses a documented share action. Follow the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).

## Test Cases

### 1. UI/UX Testing

- [ ] Verify the Web Tools action uses a recognizable icon or clear command label, has an appropriate enabled or disabled state, and gives immediate feedback.
- [ ] Trigger success, unavailable, and failure states; verify messages are concise, anchored to the action, and do not obscure existing results.

### 2. Functional Testing

- [ ] Invoke Web Tools with a supported destination and verify the exact in-app route, Android settings screen, browser URI, or external intent is opened.
- [ ] Repeat with no handler, invalid destination, restricted profile, and failed launch; verify a recoverable message and unchanged source-screen state.

- [ ] Test HTTP, HTTPS, malformed, redirecting, deep-link, intent, no-handler, and unallowlisted destinations; verify only approved HTTPS navigation proceeds after user action.

### 3. Compatibility Testing

- [ ] Verify Web Tools on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on phone and tablet window sizes, portrait and landscape, light and dark themes, and with supported locale changes.

### 4. Performance Testing

- [ ] Measure opening and using Web Tools; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Web Tools validates and bounds all host, address, port, subnet, resolver, URI, and provider data before network use or intent launch; malformed data must not cause injection or unsafe routing.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Inspect WebView settings, intents, URLs, headers, logs, and redirects; verify there is no JavaScript bridge for untrusted content and no undisclosed data is appended or transmitted.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Web Tools controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Web Tools, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
