# Terminal

## Feature: How will it work?

Terminal opens an in-app diagnostic console backed by packaged, allowlisted network operations such as DNS lookup, bounded Ping, and interface inspection. It does not expose Android's system shell; each command and argument is parsed by NetworkTools, executed with app-level privileges, and can be cancelled.

## Google Play Policy Requirements

- Provide only packaged, allowlisted diagnostic commands inside the app sandbox; do not expose root, arbitrary system shell, exploitation, credential attacks, package installation, or security bypass. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Do not download scripts, binaries, DEX, JAR, native libraries, command definitions, or executable updates from outside Google Play.
- Require explicit execution, validate and bound every argument, show command scope, and stop commands when the user cancels or leaves the active session.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify the command chooser, bounded argument input, run and stop actions, progress, output, and error states are clear and remain stable while output changes.
- [ ] Verify long output, multiple result lines, invalid arguments, disabled commands, cancellation, and empty history remain readable without resembling unrestricted shell access.

### 2. Functional Testing

- [ ] Execute every packaged command with controlled inputs and verify it invokes the corresponding internal NetworkTools operation with identical validation, limits, cancellation, and output.
- [ ] Submit unknown commands, unsupported flags, malformed arguments, and unavailable operations; verify execution is rejected with a recoverable message and no process is spawned.

- [ ] Try unlisted commands, shell metacharacters, pipes, redirection, command substitution, oversized arguments, package operations, root commands, and rapid execution; verify all are rejected.

### 3. Compatibility Testing

- [ ] Verify Terminal on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on phone and tablet window sizes, portrait and landscape, light and dark themes, and with supported locale changes.

### 4. Performance Testing

- [ ] Measure opening and using Terminal; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Terminal validates and bounds all host, address, port, subnet, resolver, URI, and provider data before network use or intent launch; malformed data must not cause injection or unsafe routing.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Inspect the APK, runtime downloads, process creation, files, and network calls; verify only packaged allowlisted diagnostics execute with app privileges and no dynamic executable code is loaded.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Terminal controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Terminal, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
