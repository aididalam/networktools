# Manual DNS Server Entry

## Feature: How will it work?

Manual DNS Server Entry is implemented as shared behavior across applicable NetworkTools screens, with consistent validation, state handling, and feedback. Tools opt into the behavior through the same reusable UI and data contracts.

## Google Play Policy Requirements

- Validate and display detected or entered network parameters without starting probes, scans, navigation, or packet sends until the user invokes the relevant explicit action. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Reject malformed, oversized, public-range where disallowed, ambiguous, control-character, and injection-oriented input before it reaches network or intent APIs.
- Do not persist manual targets by default or send them to analytics, advertising, crash reports, or unrelated services.

## Test Cases

### 1. UI/UX Testing

- [ ] Given the General Features screen is open, verify the Manual DNS Server Entry control has a clear label, suitable input control or keyboard, and visible current value.
- [ ] Enter empty, malformed, minimum, maximum, and over-limit values; verify inline errors do not resize, overlap, or hide the action controls.

### 2. Functional Testing

- [ ] Apply valid boundary and representative values for Manual DNS Server Entry; verify the next General Features operation uses the exact normalized setting.
- [ ] Submit empty, malformed, out-of-range, duplicate, and unsupported values; verify execution is blocked with a field-specific error.

- [ ] Enter malformed, oversized, control-character, public-range, and rapidly changed values; verify validation is deterministic and no network operation starts automatically.

### 3. Compatibility Testing

- [ ] Verify Manual DNS Server Entry on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on phone and tablet window sizes, portrait and landscape, light and dark themes, and with supported locale changes.

### 4. Performance Testing

- [ ] Measure opening and using Manual DNS Server Entry; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Manual DNS Server Entry validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Trace input through storage, logs, analytics, intents, DNS, and sockets; verify unconfirmed values never trigger external behavior or undisclosed persistence.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Manual DNS Server Entry controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Manual DNS Server Entry, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
