# Nearby Wi-Fi Network Scanning

## Feature: How will it work?

The Wi-Fi Analyzer uses Android's permitted scan results to calculate and present nearby Wi-Fi network scanning. Scans run asynchronously, clearly distinguish stale and current data, and explain when location, nearby-device, Wi-Fi, or platform restrictions prevent access.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify the scan action, last-scan time, progress, permission state, empty state, and retry action are visible without displacing the network list.
- [ ] Verify long SSIDs, hidden SSIDs, BSSIDs, security labels, band details, and weak-signal values remain readable and consistently aligned.

### 2. Functional Testing

- [ ] Feed controlled Android scan results containing visible, hidden, duplicate, 2.4 GHz, 5 GHz, and 6 GHz access points; verify normalization, deduplication, sorting, and displayed count.
- [ ] Test empty results, stale cached results, scan throttling, Wi-Fi disabled, location disabled, and permission denial; verify the list and recovery guidance remain accurate.

### 3. Compatibility Testing

- [ ] Verify Nearby Wi-Fi Network Scanning on Android versions before and after Wi-Fi scan permission changes, including representative Pixel and non-Pixel devices.
- [ ] Verify Wi-Fi enabled and disabled, location services enabled and disabled, permission granted and denied, scan throttling, and supported 2.4 GHz, 5 GHz, and 6 GHz networks.

### 4. Performance Testing

- [ ] Measure Nearby Wi-Fi Network Scanning with small, typical, and maximum supported workloads; verify progress remains responsive and completion respects the configured timeout or product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Feed malformed, oversized, duplicated, and rapidly changing scan-result fields; verify parsing is bounded and cannot crash or corrupt the network list.
- [ ] Verify least-privilege Wi-Fi and location permissions and confirm SSIDs, BSSIDs, saved-network details, and scan results are not written to production logs, analytics, or remote services.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Nearby Wi-Fi Network Scanning controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] While Nearby Wi-Fi Network Scanning is loading or running, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify no duplicate jobs, crashes, or corrupted results.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
