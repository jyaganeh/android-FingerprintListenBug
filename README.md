# android-FingerprintListenBug
This sample demonstrates a strange interaction between the use of fingerprint authentication in this app and the Android system lock screen

## Steps to reproduce

1. Make sure you have a screen lock method set and have enrolled fingerprints in **Settings > Security**
1. Turn the screen off
2. Build and run this app from Android Studio
1. Attempt to wake and unlock the device via the fingerprint reader

### Expected Behavior

The screen should turn on and the device should unlock as usual, with the sample app in the foreground listening for fingerprints.

### Observed Behavior

The screen does not turn on unless the power button is pressed. Tapping on the sensor does nothing (no UI changes, no vibration) and the only way to unlock is via the fallback method. Once unlocked, the sample app is in the foreground listening for fingerprints.
