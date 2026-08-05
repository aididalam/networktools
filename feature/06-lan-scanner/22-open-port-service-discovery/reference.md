# Open Port and Service Discovery

## Feature: How will it work?

Deep mode checks a fixed, bounded list of common TCP ports on devices already discovered inside the active private subnet. Open ports are stored with a likely service derived from the IANA-style conventional port mapping. HTTP and HTTPS ports can be opened in the user's external browser; other protocols are informational only.

## Google Play Policy Requirements

- Require private-subnet validation, active-network membership, explicit authorization, and a foreground Start action.
- Bound hosts, ports, concurrency, and connection timeouts; close sockets and stop immediately on cancellation.
- Do not probe public targets, guess credentials, collect banners, exploit services, assess vulnerabilities, evade detection, or run in the background.
- Validate browser URLs from discovered private IPv4 addresses and allow only HTTP/HTTPS schemes after a user tap. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).

## Test Cases

### 1. UI/UX Testing
- [ ] Verify expanded devices show sorted `port/tcp` values, likely-service labels, and Open icons only for supported web ports.
- [ ] Verify long names, many ports, light/dark themes, phone/tablet layouts, and 200% scaling remain readable.

### 2. Functional Testing
- [ ] Use controlled servers on each configured common port and verify open/closed/filtered classification and service mapping.
- [ ] Verify HTTP/HTTPS actions preserve the device address and non-default port and launch through an external browser.

### 3. Compatibility Testing
- [ ] Verify common-port discovery on supported Pixel and Moto devices, multiple routers, client-isolated Wi-Fi, VPN, and Android local-network permission versions.

### 4. Performance Testing
- [ ] Scan 2, 32, and 64 discovered devices; verify concurrency and timeouts remain bounded and sockets return to baseline.

### 5. Security Testing
- [ ] Verify public/malformed addresses, injected host text, unsupported schemes, undiscovered ports, and missing authorization cannot launch or scan.
- [ ] Capture traffic and verify only TCP connect attempts to the fixed common-port list occur inside the displayed subnet.

### 6. Accessibility Testing
- [ ] Verify TalkBack reads the port, likely service, and Open action distinctly with logical focus order and touch targets.

### 7. Interruption Testing
- [ ] Stop, rotate, background, revoke permission, disconnect Wi-Fi, or change networks during port checks; verify sockets close and partial results remain consistent.
