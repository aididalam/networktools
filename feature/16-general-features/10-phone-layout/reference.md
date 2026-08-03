# Phone Layout

## Feature: How will it work?

Phone Layout adapts navigation, spacing, content width, and result presentation to the target window size while retaining the same feature set and state. Insets, orientation changes, and large text are handled without overlap or clipped controls.

## Google Play Policy Requirements

- Use no additional sensitive permissions, background services, package visibility, network collection, or user-data processing solely for this shared UI behavior. Follow the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).
- Preserve permission-denied, offline, empty, error, and authorization states without hiding warnings or making restricted actions easier to trigger accidentally.
- Do not start or repeat network operations during rotation, resizing, theme switching, process restoration, or accessibility changes.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify Phone Layout at compact, medium, and expanded window widths with portrait and landscape insets; no content may overlap or become unreachable.
- [ ] Verify cards, toolbars, fixed controls, and result areas retain stable dimensions while content changes.

### 2. Functional Testing

- [ ] Run the complete primary workflow in Phone Layout and verify every control, result, dialog, and navigation action remains available.
- [ ] Resize the window and rotate during data entry and result display; verify state is retained and the layout changes without recreating the operation incorrectly.

- [ ] Change theme, orientation, window size, font scale, locale, and process state around a pending restricted action; verify the action is neither auto-started nor repeated.

### 3. Compatibility Testing

- [ ] Verify Phone Layout on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on phone and tablet window sizes, portrait and landscape, light and dark themes, and with supported locale changes.

### 4. Performance Testing

- [ ] Measure opening and using Phone Layout; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Phone Layout validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Inspect lifecycle callbacks, restored state, permissions, services, and network traffic; verify shared UI behavior introduces no new data access or background execution.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Phone Layout controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Phone Layout, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
