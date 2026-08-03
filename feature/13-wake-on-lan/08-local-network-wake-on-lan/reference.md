# Local Network Wake-on-LAN

## Feature: How will it work?

Local Wake-on-LAN derives the private IPv4 subnet and broadcast from Android `LinkProperties`, verifies both saved device and broadcast against that active Wi-Fi/Ethernet network, binds one UDP socket to the active network, and sends only after exact confirmation. Android 16 local-network permission is requested only when required for that send.

## Google Play Policy Requirements

- Send a magic packet only after an explicit user action to a displayed, user-saved or user-entered device that the user is authorized to manage. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Validate MAC, destination, broadcast scope, and UDP port; rate-limit repeated sends and never run scheduled, hidden, relayed, or background Wake-on-LAN jobs.
- Keep saved device identifiers in app-private local storage and transmit only the standard magic packet to the confirmed destination.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify the local send action shows the selected device, active local network, broadcast address, and UDP port before sending.
- [ ] Verify unavailable Wi-Fi or Ethernet, invalid broadcast addresses, sending, success, and failure states are visually distinct and actionable.

### 2. Functional Testing

- [ ] On a controlled LAN, capture the UDP datagram and verify it is sent through the active local interface to the configured broadcast address and port.
- [ ] Test no local network, mobile-only connectivity, invalid subnet data, socket failure, and route changes; verify sending is blocked or fails with an accurate reason.

- [ ] Test missing confirmation, malformed targets, public destinations, rapid repeated taps, app backgrounding, and cancellation; verify validation and rate limits prevent unintended packet sends.

### 3. Compatibility Testing

- [ ] Verify Local Network Wake-on-LAN on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure opening and using Local Network Wake-on-LAN; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Local Network Wake-on-LAN validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify permission is requested only on applicable Android versions at send time, the socket is bound to the active local network and closed immediately, and no discovery probe, internet API, log, analytics event, or background send occurs.

- [ ] Capture traffic, logs, analytics, and storage; verify only a standard magic packet reaches the displayed destination and saved MAC/IP data is not uploaded or exposed.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Local Network Wake-on-LAN controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Local Network Wake-on-LAN, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
