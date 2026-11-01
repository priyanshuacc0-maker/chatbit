# Meshline — device test plan

## Environment

- Two physical Android phones, API 26+.
- Google Play services installed and updated on both.
- Same app build on both phones.
- Bluetooth and Wi-Fi enabled; app permissions granted.
- Start with mobile data/internet enabled only for installation/setup, then repeat with internet disconnected.

## Core connection tests

- [ ] Both phones start discovery/advertising and see the other phone.
- [ ] A user can request a connection from the discovered-device list.
- [ ] Both phones show the same four-digit authentication code.
- [ ] Rejecting on either phone prevents the session from opening.
- [ ] Accepting on both phones establishes the session and produces a secure-session status.
- [ ] A phone cannot connect to itself.
- [ ] Repeated taps on Connect do not leave duplicate or permanently stuck sessions.

## Messaging tests

- [ ] Send a short message A → B; B displays exactly one copy.
- [ ] Send a short message B → A.
- [ ] Send multiple messages quickly in both directions.
- [ ] Send a long message up to the 8,000-character / 20 KiB UTF-8 limit; verify larger input is blocked in the composer.
- [ ] Verify sender status changes from queued/sent to delivered only after the ACK arrives.
- [ ] Close and reopen the app; local conversation history remains readable.
- [ ] Inspect the SQLite messages table: message bodies should be encrypted/base64 ciphertext, not readable plain text.

## Offline and recovery tests

- [ ] Turn off mobile data and disconnect both phones from the internet; local discovery and chat still work.
- [ ] Keep Bluetooth and Wi-Fi on; test at several distances and orientations.
- [ ] Disable one radio or walk out of range while a message is pending; confirm it remains queued.
- [ ] Restore range/connectivity, pair again, and verify the queued message is delivered once.
- [ ] Drop a connection immediately after send; verify retry/deduplication does not create duplicate bubbles.
- [ ] Reboot one phone, open Meshline, reconnect and verify pending messages are resent.
- [ ] Deny each permission in turn and verify a useful status is shown instead of a crash.
- [ ] Test API 26–28, 29–32, 33–35 permission flows separately.

## Security checks

- [ ] Compare the four-digit code visually on both devices; reject mismatches.
- [ ] Change an authenticated packet ID in a test harness; AES-GCM decryption must fail.
- [ ] Replay a previously received message ID; receiver should not create another database row and should ACK the duplicate.
- [ ] Send malformed/oversized JSON; the app should discard it without crashing.
- [ ] Clear app data and reinstall; old local database content should not be recoverable from a new install's Keystore key.

## Release gates still outstanding

- [ ] Build and install the APK from Android Studio on real phones.
- [ ] Instrument the Nearby callback lifecycle and verify no race between hello/key exchange and secure payload delivery.
- [ ] Add a foreground service for background connectivity, user-visible notification and battery-aware controls.
- [ ] Add automated repository/database tests and UI tests.
- [ ] Run radio coexistence, battery-drain, soak, reconnect-storm and multi-peer stress tests.
- [ ] Complete an independent security review before public release.

## v0.2 additions

- [ ] Run unit tests (`./gradlew testDebugUnitTest`): `IdentityTest`, `PacketSizeTest`, `SessionCryptoTest` pass.
- [ ] First pairing shows the same code on both phones; afterwards close and reopen the chat on both, tap Find nearby devices: they reconnect with **no** dialog and show "Paired device".
- [ ] Forget the contact on **both** phones, then confirm the next connection asks for the code again.
- [ ] Forget on only one phone: the other (auto-accepting) side shows no code; the dialog on the first phone says to reject. Verify you understand this before trusting it.
- [ ] Send a 4,000-character message full of quotes and line breaks: it is delivered, not stuck on "Queued".
- [ ] Press Home with a chat open: the "Meshline is running" notification appears; the other phone sends a message and a notification arrives; tapping it opens that chat.
- [ ] Tap **Stop** in the notification: discovery and connections end, the notification disappears.
- [ ] Leave the app idle and unconnected for 10 minutes: search pauses with the battery message and the service stops.
- [ ] Lock screen notifications show "New message" only.
- [ ] Android 11 or older: with Location off, tapping Find nearby devices shows the Location hint instead of failing silently.
- [ ] Android 15: header and keyboard do not overlap the status/navigation bars.
- [ ] Test on at least one Samsung/Xiaomi phone with default battery settings for 30 minutes in the background.

## v0.3 additions

- [ ] Fresh install: permission prompt appears once at first launch; afterwards search starts without tapping anything.
- [ ] Bluetooth off / Location off (Android 11-): tapping Turn on opens the matching system screen; returning to the app starts search.
- [ ] Pause search from the menu, close and reopen the app: search stays paused until you turn it on.
- [ ] QR: phone A Menu → Show QR; phone B Scan QR. B connects with no code dialog; chat appears on both; the QR cannot be reused afterwards.
- [ ] QR expiry: wait over 5 minutes, scan again: pairing fails with a clear message and nothing is added.
- [ ] Scanning a non-Meshline QR shows "not a Meshline invite".
- [ ] A third phone that connects while a QR is showing (without scanning) is NOT auto-accepted; it gets the normal code dialog.
- [ ] Unread badge appears for a message received while the chat list is open and clears when the chat is opened.
- [ ] Rotate / background / return keeps the open chat and drafts sensible; back button closes the drawer, then the chat.
