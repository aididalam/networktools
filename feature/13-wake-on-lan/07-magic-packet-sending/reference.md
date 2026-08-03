# Magic Packet Sending

## Feature: How will it work?

After exact destination review and an authorization checkbox, the tool builds a 102-byte packet containing six `FF` bytes and exactly 16 copies of the selected six-byte MAC. It sends one UDP datagram off the main thread, enforces a two-second rate limit, reports socket/routing errors, and states that transmission does not prove the device woke.

## Google Play Policy Requirements

- Send a magic packet only after an explicit user action to a displayed, user-saved or user-entered device that the user is authorized to manage. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Validate MAC, destination, broadcast scope, and UDP port; rate-limit repeated sends and never run scheduled, hidden, relayed, or background Wake-on-LAN jobs.
- Keep saved device identifiers in app-private local storage and transmit only the standard magic packet to the confirmed destination.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify the send action is disabled until the MAC address, destination, and UDP port are valid, then shows clear sending, success, and failure feedback.
- [ ] Verify the selected device and destination remain visible before confirmation and that repeated taps cannot enqueue duplicate packets accidentally.

### 2. Functional Testing

- [ ] Capture a sent packet and verify it contains six `FF` bytes followed by exactly 16 repetitions of the selected six-byte MAC address.
- [ ] Test malformed MAC addresses, invalid destinations and ports, unavailable routes, socket failures, and repeated sends; verify no packet is sent until validation succeeds.

- [ ] Test missing confirmation, malformed targets, public destinations, rapid repeated taps, app backgrounding, and cancellation; verify validation and rate limits prevent unintended packet sends.

### 3. Compatibility Testing

- [ ] Verify Magic Packet Sending on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure packet construction and UDP sending; verify the operation stays off the main thread and completes within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Magic Packet Sending validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify one explicit confirmation produces one 102-byte datagram with no retry, scheduler, worker, service, background continuation, telemetry, or result log; confirm errors expose no internal exception details.

- [ ] Capture traffic, logs, analytics, and storage; verify only a standard magic packet reaches the displayed destination and saved MAC/IP data is not uploaded or exposed.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Magic Packet Sending controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Magic Packet Sending, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
