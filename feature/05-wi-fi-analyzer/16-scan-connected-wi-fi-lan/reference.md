# Scan Connected Wi-Fi LAN

## Feature: How will it work?

Expanding the currently connected access point shows a Scan LAN button when Android exposes a private IPv4 address. The button passes the normalized active subnet to LAN Scanner, selects Deep mode, and leaves scanning stopped until the user confirms authorization and explicitly starts it. Back returns to Wi-Fi Analyzer.

## Google Play Policy Requirements

- Never start scanning from an access-point tap alone; preserve the LAN Scanner authorization acknowledgement and explicit Start action.
- Accept only the active private Wi-Fi subnet, reject public ranges and VPN-derived automatic targets, and perform no background scan.
- Keep the transition and discovered identifiers on-device and follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).

## Test Cases

### 1. UI/UX Testing
- [ ] Verify only the expanded connected access point shows Scan LAN and that the local address fits at 200% font scale.
- [ ] Verify back navigation returns to Wi-Fi Analyzer without starting a scan.

### 2. Functional Testing
- [ ] Verify the action passes the normalized active subnet, selects Deep mode, and still requires authorization plus Start.
- [ ] Verify non-connected access points, public addresses, VPN-active detection, and missing IPv4 state never produce an unsafe target.

### 3. Compatibility Testing
- [ ] Verify the flow on supported Pixel and Moto devices across Android versions, Wi-Fi bands, DHCP prefixes, and permission states.

### 4. Performance Testing
- [ ] Verify expanding cards and navigating remain responsive with the maximum bounded access-point result set.

### 5. Security Testing
- [ ] Verify crafted SSIDs/BSSIDs cannot alter the subnet, URI, route, authorization, or scan configuration.
- [ ] Verify no scan starts until authorization is checked and Start is tapped.

### 6. Accessibility Testing
- [ ] Verify TalkBack announces the connected state, expansion control, Scan LAN action, and destination logically.

### 7. Interruption Testing
- [ ] Rotate, background, disconnect Wi-Fi, switch networks, or enable VPN during the transition and verify stale targets cannot scan outside the active LAN.
