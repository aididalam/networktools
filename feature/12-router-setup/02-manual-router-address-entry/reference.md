# Manual Router Address Entry

## Feature: How will it work?

The Router Setup screen uses manual router address entry to establish the router destination for later administration actions. Automatically detected and manually supplied values remain distinguishable and are validated before use.

## Google Play Policy Requirements

- Require explicit confirmation of the displayed router address before launching it and never attempt automatic configuration, credential submission, default-password testing, or authentication bypass. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Open administration pages through a safe Android browser intent; prefer HTTPS and show a clear warning before user-approved HTTP access.
- Never collect, inspect, store, autofill, or transmit router usernames, passwords, cookies, page content, or configuration data.

## Test Cases

### 1. UI/UX Testing

- [ ] Given the Router Setup screen is open, verify the Manual Router Address Entry control has a clear label, suitable input control or keyboard, and visible current value.
- [ ] Enter empty, malformed, minimum, maximum, and over-limit values; verify inline errors do not resize, overlap, or hide the action controls.

### 2. Functional Testing

- [ ] Apply valid boundary and representative values for Manual Router Address Entry; verify the next Router Setup operation uses the exact normalized setting.
- [ ] Submit empty, malformed, out-of-range, duplicate, and unsupported values; verify execution is blocked with a field-specific error.

- [ ] Test detected, manual, malformed, public, redirected, HTTP, HTTPS, and unreachable addresses; verify the exact destination is shown and requires user confirmation before launch.

### 3. Compatibility Testing

- [ ] Verify Manual Router Address Entry on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure opening and using Manual Router Address Entry; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Manual Router Address Entry validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Inspect intents, WebView usage, logs, storage, and network traffic; verify no credentials, default-password attempts, JavaScript bridge, automatic setting change, or unconfirmed navigation occurs.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Manual Router Address Entry controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Manual Router Address Entry, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
