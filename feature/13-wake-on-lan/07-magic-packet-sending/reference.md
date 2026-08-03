# Magic Packet Sending

## Feature: How will it work?

The Wake-on-LAN tool builds a standards-compliant magic packet from the selected device's MAC address and sends it to the configured broadcast or target address and UDP port. Sending occurs only after validation and reports local socket or routing errors.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify the send action is disabled until the MAC address, destination, and UDP port are valid, then shows clear sending, success, and failure feedback.
- [ ] Verify the selected device and destination remain visible before confirmation and that repeated taps cannot enqueue duplicate packets accidentally.

### 2. Functional Testing

- [ ] Capture a sent packet and verify it contains six `FF` bytes followed by exactly 16 repetitions of the selected six-byte MAC address.
- [ ] Test malformed MAC addresses, invalid destinations and ports, unavailable routes, socket failures, and repeated sends; verify no packet is sent until validation succeeds.

### 3. Compatibility Testing

- [ ] Verify Magic Packet Sending on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure packet construction and UDP sending; verify the operation stays off the main thread and completes within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Magic Packet Sending validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Magic Packet Sending controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Magic Packet Sending, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
