# Copy Network Information

## Feature: How will it work?

The user can copy the currently available network information from the IP Information screen to the system clipboard. The action is enabled only when content exists and confirms completion without changing the result.

## Google Play Policy Requirements

- Collect network information only while the user is viewing this feature or explicitly requests refresh; do not run hidden background monitoring.
- Request only the minimum Wi-Fi or location permission required for the selected value, immediately before use, and keep unaffected values usable after denial. Follow the [Permissions and Sensitive APIs policy](https://support.google.com/googleplay/android-developer/answer/16909972).
- Keep SSID, BSSID, addresses, and lookup results on-device except for the minimum keyless request needed to resolve public IP, RDAP, or ASN data and an explicit user-initiated share. Follow the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).

## Test Cases

### 1. UI/UX Testing

- [ ] Verify the Copy Network Information action uses a recognizable icon or clear command label, has an appropriate enabled or disabled state, and gives immediate feedback.
- [ ] Trigger success, unavailable, and failure states; verify messages are concise, anchored to the action, and do not obscure existing results.

### 2. Functional Testing

- [ ] With a known IP Information result, invoke Copy Network Information and verify the clipboard text exactly matches the visible eligible fields and formatting.
- [ ] Verify the action is disabled or explains the empty state when no result exists, and that repeated copies do not mutate the source data.

- [ ] Verify opening unrelated tools does not request Wi-Fi or location permission, permission denial leaves unrestricted network values available, and refresh never continues after leaving the screen.

### 3. Compatibility Testing

- [ ] Verify Copy Network Information on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure opening and using Copy Network Information; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Copy Network Information validates and bounds all host, address, port, subnet, resolver, URI, and provider data before network use or intent launch; malformed data must not cause injection or unsafe routing.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Capture network, analytics, logs, and local storage during use; verify protected Wi-Fi identifiers and results are not transmitted or recorded beyond the documented keyless lookup and explicit share actions.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Copy Network Information controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] While Copy Network Information is loading or running, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify no duplicate jobs, crashes, or corrupted results.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
