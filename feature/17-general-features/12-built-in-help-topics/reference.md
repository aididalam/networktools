# Built In Help Topics

## Feature: How will it work?

Built-in help provides locally available, tool-specific guidance, input requirements, permission explanations, and common error recovery steps. Help opens from the relevant tool and returns the user to the same state.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify Built In Help Topics is clearly labeled, aligned with related General Features values, and distinguishes loading, available, unavailable, and error states.
- [ ] Verify long values, multiple values, and localized labels wrap or truncate intentionally without hiding copy, refresh, or navigation actions.

### 2. Functional Testing

- [ ] Open help from each supported tool and verify the correct topic, prerequisites, examples, permissions, and error guidance are shown.
- [ ] Verify help works offline, internal links reach valid topics, and Back returns to the originating tool with its state intact.

### 3. Compatibility Testing

- [ ] Verify Built In Help Topics on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on phone and tablet window sizes, portrait and landscape, light and dark themes, and with supported locale changes.

### 4. Performance Testing

- [ ] Measure opening and using Built In Help Topics; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Built In Help Topics validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Built In Help Topics controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Built In Help Topics, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
