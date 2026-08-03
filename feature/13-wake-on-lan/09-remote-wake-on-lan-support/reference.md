# Remote Wake-on-LAN Support

## Feature: How will it work?

Remote Wake-on-LAN sends one magic packet directly to an explicitly configured public unicast host/IP and UDP port after explaining that the user's router forwarding or own compatible relay is required. The app provides no relay, infers no endpoint, rejects local/special-use DNS results, performs no retry, and never runs remotely or in the background.

## Google Play Policy Requirements

- Send a magic packet only after an explicit user action to a displayed, user-saved or user-entered device that the user is authorized to manage. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Validate MAC, destination, broadcast scope, and UDP port; rate-limit repeated sends and never run scheduled, hidden, relayed, or background Wake-on-LAN jobs.
- Keep saved device identifiers in app-private local storage and transmit only the standard magic packet to the confirmed destination.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify the remote mode clearly distinguishes the public target from the local broadcast address and shows the forwarding or relay requirement before sending.
- [ ] Verify hostname resolution, invalid destination, sending, success, timeout, and failure states are readable without implying that device wake-up was confirmed.

### 2. Functional Testing

- [ ] Send through a controlled public endpoint or relay and verify the received datagram, resolved destination, UDP port, and magic-packet bytes are correct.
- [ ] Test DNS failure, unreachable target, blocked port, IPv4 and IPv6 destinations, relay rejection, and timeout; verify the app reports packet-send status without claiming the device woke.

- [ ] Test missing confirmation, malformed targets, public destinations, rapid repeated taps, app backgrounding, and cancellation; verify validation and rate limits prevent unintended packet sends.

### 3. Compatibility Testing

- [ ] Verify Remote Wake-on-LAN Support on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure opening and using Remote Wake-on-LAN Support; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Remote Wake-on-LAN Support validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify only standard DNS and the confirmed UDP destination are contacted, at most eight DNS answers are considered, and no app-operated relay, HTTP API, API key, paid provider, location inference, analytics, log, scheduler, or background service exists.

- [ ] Capture traffic, logs, analytics, and storage; verify only a standard magic packet reaches the displayed destination and saved MAC/IP data is not uploaded or exposed.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Remote Wake-on-LAN Support controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Remote Wake-on-LAN Support, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
