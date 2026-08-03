# Target IP Address Storage

## Feature: How will it work?

Local profiles require a numeric private IPv4 device address and revalidate that it belongs to the active Wi-Fi/Ethernet subnet before sending. Remote profiles require a bounded hostname or IP that resolves to public unicast; URLs, paths, credentials, whitespace, local, multicast, and special-use results are rejected.

## Google Play Policy Requirements

- Send a magic packet only after an explicit user action to a displayed, user-saved or user-entered device that the user is authorized to manage. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Validate MAC, destination, broadcast scope, and UDP port; rate-limit repeated sends and never run scheduled, hidden, relayed, or background Wake-on-LAN jobs.
- Keep saved device identifiers in app-private local storage and transmit only the standard magic packet to the confirmed destination.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify Target IP Address Storage has a clear empty state, readable populated state, deterministic ordering, and visible timestamps or identifiers where relevant.
- [ ] Populate long and duplicate-looking entries; verify text wraps or truncates intentionally and the list remains easy to scan.

### 2. Functional Testing

- [ ] Create data for Target IP Address Storage, close and relaunch the app, and verify the expected records and ordering persist without duplication.
- [ ] Exercise empty storage, maximum supported records, update, and removal flows; verify storage failures do not corrupt existing data.

- [ ] Test missing confirmation, malformed targets, public destinations, rapid repeated taps, app backgrounding, and cancellation; verify validation and rate limits prevent unintended packet sends.

### 3. Compatibility Testing

- [ ] Verify Target IP Address Storage on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure opening and using Target IP Address Storage; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Target IP Address Storage validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify targets are excluded from backup/device transfer, logs, analytics, and crash messages; remote DNS resolution is bounded to eight answers and only the confirmed destination receives a datagram.

- [ ] Capture traffic, logs, analytics, and storage; verify only a standard magic packet reaches the displayed destination and saved MAC/IP data is not uploaded or exposed.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Target IP Address Storage controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Target IP Address Storage, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
