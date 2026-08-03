# Wi-Fi Settings Access

## Feature: How will it work?

The Wi-Fi settings action opens Android's Wi-Fi panel on Android 10 and later, with the platform Wi-Fi settings screen as the older-version fallback. Android owns all viewing and changes; the app receives no password, network configuration, or settings result.

## Google Play Policy Requirements

- Require explicit confirmation of the displayed router address before launching it and never attempt automatic configuration, credential submission, default-password testing, or authentication bypass. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Open administration pages through a safe Android browser intent; prefer HTTPS and show a clear warning before user-approved HTTP access.
- Never collect, inspect, store, autofill, or transmit router usernames, passwords, cookies, page content, or configuration data.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify the Wi-Fi Settings Access action uses a recognizable icon or clear command label, has an appropriate enabled or disabled state, and gives immediate feedback.
- [ ] Trigger success, unavailable, and failure states; verify messages are concise, anchored to the action, and do not obscure existing results.

### 2. Functional Testing

- [ ] Invoke Wi-Fi Settings Access with a supported destination and verify the exact in-app route, Android settings screen, browser URI, or external intent is opened.
- [ ] Repeat with no handler, invalid destination, restricted profile, and failed launch; verify a recoverable message and unchanged source-screen state.

- [ ] Verify this action does not depend on a router address, does not open a browser, and returns to an unchanged Router Setup state after the Android panel or settings screen closes.

### 3. Compatibility Testing

- [ ] Verify Wi-Fi Settings Access on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure opening and using Wi-Fi Settings Access; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Wi-Fi Settings Access validates and bounds all host, address, port, subnet, resolver, URI, and provider data before network use or intent launch; malformed data must not cause injection or unsafe routing.
- [ ] Verify the explicit Android Settings intent carries no extras, URI, credentials, or network data and grants no permissions to another app.

- [ ] Inspect intents, WebView usage, logs, storage, and network traffic; verify no credentials, default-password attempts, JavaScript bridge, automatic setting change, or unconfirmed navigation occurs.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Wi-Fi Settings Access controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Wi-Fi Settings Access, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
