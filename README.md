# FraudShield Proto (v2 of the overlay probe)

Rule: GPay / PhonePe / Paytm in front + a live call -> full-screen 15-second warning,
shown right away on whatever screen is open (including amount and UPI PIN screens).

- Call detection: system audio mode (cellular + WhatsApp/Telegram-style calls). No permission needed.
- Foreground app: package name of the active window only. No screen text is ever read or logged.
- After 15 s the "I understand, continue" button unlocks; the warning then stays off for 30 s.
- Leaving the payment app removes the warning.

## Build (GitHub Actions, no Android Studio)
Push this folder to a GitHub repo (keep `.github` and `app/debug.keystore`),
open Actions > Build APK, download artifact `FraudShieldProto-debug-apk`.

## Install
    adb install -r app-debug.apk
(Uses a fixed debug key, so later builds install over this one.)
Remove the old OverlayProbe first if installed: `adb uninstall com.nullvariable.overlayprobe`

## Test
1. Enable the accessibility service (app > "Open Accessibility settings").
2. Turn on "Pretend a call is active" to test without calling, then open GPay:
   the warning should appear immediately, even if you switch in while on the PIN screen.
3. Turn the simulation off and test with a real call (cellular, then WhatsApp).
4. Repeat on PhonePe and Paytm. Use a secondary account and tiny amounts. Do not complete payments.
