# Remote Wake-on-LAN Support

## Feature: How will it work?

Remote Wake-on-LAN sends a magic packet to a user-configured public host or relay and UDP port after explaining that router forwarding or a compatible relay is required. Remote destinations are explicit, validated, and never inferred from approximate device-location data.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify the remote mode clearly distinguishes the public target from the local broadcast address and shows the forwarding or relay requirement before sending.
- [ ] Verify hostname resolution, invalid destination, sending, success, timeout, and failure states are readable without implying that device wake-up was confirmed.

### 2. Functional Testing

- [ ] Send through a controlled public endpoint or relay and verify the received datagram, resolved destination, UDP port, and magic-packet bytes are correct.
- [ ] Test DNS failure, unreachable target, blocked port, IPv4 and IPv6 destinations, relay rejection, and timeout; verify the app reports packet-send status without claiming the device woke.

### 3. Compatibility Testing

- [ ] Verify Remote Wake-on-LAN Support on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure opening and using Remote Wake-on-LAN Support; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Remote Wake-on-LAN Support validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Remote Wake-on-LAN Support controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Remote Wake-on-LAN Support, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
