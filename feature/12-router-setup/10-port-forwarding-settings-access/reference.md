# Port Forwarding Settings Access

## Feature: How will it work?

The Router Setup screen uses port forwarding settings access to open the detected or manually validated router address through the appropriate Android intent. The destination is shown before launch and unsupported, unsafe, or unreachable destinations are handled without a crash.

## Google Play Policy Requirements

- Require explicit confirmation of the displayed router address before launching it and never attempt automatic configuration, credential submission, default-password testing, or authentication bypass. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Open administration pages through a safe Android browser intent; prefer HTTPS and show a clear warning before user-approved HTTP access.
- Never collect, inspect, store, autofill, or transmit router usernames, passwords, cookies, page content, or configuration data.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify the Port Forwarding Settings Access action uses a recognizable icon or clear command label, has an appropriate enabled or disabled state, and gives immediate feedback.
- [ ] Trigger success, unavailable, and failure states; verify messages are concise, anchored to the action, and do not obscure existing results.

### 2. Functional Testing

- [ ] Invoke Port Forwarding Settings Access with a supported destination and verify the exact in-app route, Android settings screen, browser URI, or external intent is opened.
- [ ] Repeat with no handler, invalid destination, restricted profile, and failed launch; verify a recoverable message and unchanged source-screen state.

- [ ] Test detected, manual, malformed, public, redirected, HTTP, HTTPS, and unreachable addresses; verify the exact destination is shown and requires user confirmation before launch.

### 3. Compatibility Testing

- [ ] Verify Port Forwarding Settings Access on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure opening and using Port Forwarding Settings Access; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Port Forwarding Settings Access validates and bounds all host, address, port, subnet, resolver, URI, and provider data before network use or intent launch; malformed data must not cause injection or unsafe routing.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Inspect intents, WebView usage, logs, storage, and network traffic; verify no credentials, default-password attempts, JavaScript bridge, automatic setting change, or unconfirmed navigation occurs.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Port Forwarding Settings Access controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Port Forwarding Settings Access, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
