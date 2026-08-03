# Saved Wi-Fi Networks

## Feature: How will it work?

The Wi-Fi Analyzer uses Android's permitted scan results to calculate and present saved Wi-Fi networks. Scans run asynchronously, clearly distinguish stale and current data, and explain when location, nearby-device, Wi-Fi, or platform restrictions prevent access.

## Google Play Policy Requirements

- Perform Wi-Fi scans only in a visible, user-initiated workflow, comply with platform scan throttling, and do not request background location. Follow the [Permissions and Sensitive APIs policy](https://support.google.com/googleplay/android-developer/answer/16909972).
- Present a just-in-time permission explanation, support denial and revocation, and never block unrelated tools because scan permission is unavailable.
- Keep SSIDs, BSSIDs, saved-network details, and scan results on-device; do not upload them, derive precise location, or send them to analytics or advertising. Follow the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).

## Test Cases

### 1. UI/UX Testing

- [ ] Verify Saved Wi-Fi Networks has a clear empty state, readable populated state, deterministic ordering, and visible timestamps or identifiers where relevant.
- [ ] Populate long and duplicate-looking entries; verify text wraps or truncates intentionally and the list remains easy to scan.

### 2. Functional Testing

- [ ] Create data for Saved Wi-Fi Networks, close and relaunch the app, and verify the expected records and ordering persist without duplication.
- [ ] Exercise empty storage, maximum supported records, update, and removal flows; verify storage failures do not corrupt existing data.

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

- [ ] Inspect manifest permissions, network calls, logs, analytics, and storage; verify there is no background-location request or upload of SSIDs, BSSIDs, saved-network details, or scan results.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Saved Wi-Fi Networks controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Saved Wi-Fi Networks, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
