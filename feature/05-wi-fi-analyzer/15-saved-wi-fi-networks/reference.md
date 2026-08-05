# Saved Wi-Fi Networks

## Feature: How will it work?

After a user-initiated scan, Wi-Fi Analyzer automatically records the identity metadata of the access point Android identifies as currently connected. Users may also bookmark visible access points. This local list contains only observations made while NetworkTools is used and is not presented as the device's complete Wi-Fi history. The Saved tab links to Android Wi-Fi Settings for the OS-owned complete saved-network list and protected QR/password sharing.

## Google Play Policy Requirements

- Perform Wi-Fi scans only in a visible, user-initiated workflow, comply with platform scan throttling, and do not request background location. Follow the [Permissions and Sensitive APIs policy](https://support.google.com/googleplay/android-developer/answer/16909972).
- Present a just-in-time permission explanation, support denial and revocation, and never block unrelated tools because scan permission is unavailable.
- Keep SSIDs, BSSIDs, saved-network details, and scan results on-device; do not upload them, derive precise location, or send them to analytics or advertising. Follow the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).
- Do not claim that a runtime permission grants saved-network credential access. For apps targeting Android 10 or later, `WifiManager.getConfiguredNetworks()` returns an empty list except for privileged device-owner, profile-owner, system, or carrier cases.
- Delegate saved credential and QR display to Android Wi-Fi Settings after an explicit explanation. NetworkTools must never read, generate from, persist, log, or receive a saved Wi-Fi password.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify Saved Wi-Fi Networks has a clear empty state, readable populated state, deterministic ordering, and visible timestamps or identifiers where relevant.
- [ ] Populate long and duplicate-looking entries; verify text wraps or truncates intentionally and the list remains easy to scan.

### 2. Functional Testing

- [ ] Create data for Saved Wi-Fi Networks, close and relaunch the app, and verify the expected records and ordering persist without duplication.
- [ ] Exercise empty storage, maximum supported records, update, and removal flows; verify storage failures do not corrupt existing data.
- [ ] Scan while connected and verify that access point is added or updated once; verify unconnected results are not automatically recorded.
- [ ] Open Android saved networks, cancel the consent dialog, then continue and verify Android Wi-Fi Settings opens without returning credentials to NetworkTools.

- [ ] Verify no scan or permission prompt occurs before the user enters Wi-Fi Analyzer and starts scanning; test denial, revocation, throttling, Wi-Fi disabled, and leaving the screen.

### 3. Compatibility Testing

- [ ] Verify Saved Wi-Fi Networks on Android versions before and after Wi-Fi scan permission changes, including representative Pixel and non-Pixel devices.
- [ ] Verify Wi-Fi enabled and disabled, location services enabled and disabled, permission granted and denied, scan throttling, and supported 2.4 GHz, 5 GHz, and 6 GHz networks.

### 4. Performance Testing

- [ ] Measure Saved Wi-Fi Networks with small, typical, and maximum supported workloads; verify progress remains responsive and completion respects the configured timeout or product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Feed malformed, oversized, duplicated, and rapidly changing scan-result fields; verify parsing is bounded and cannot crash or corrupt the network list.
- [ ] Verify least-privilege Wi-Fi and location permissions and confirm SSIDs, BSSIDs, saved-network details, and scan results are not written to production logs, analytics, or remote services.
- [ ] Verify the app never calls configured-network credential APIs, never persists passwords or QR credentials, and cannot leave for system sharing until the user confirms.

- [ ] Inspect manifest permissions, network calls, logs, analytics, and storage; verify there is no background-location request or upload of SSIDs, BSSIDs, saved-network details, or scan results.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Saved Wi-Fi Networks controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Saved Wi-Fi Networks, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
