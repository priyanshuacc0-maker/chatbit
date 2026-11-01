# Meshline — offline-first Android chat (v0.2)

Meshline is a native Android peer-to-peer text-chat prototype. Its transport is Google Nearby Connections, which discovers nearby app instances and chooses an available short-range transport (Bluetooth, BLE and/or Wi-Fi). Message traffic does not use a Meshline server or require mobile data/a router once the devices can see each other.

> **Honest build status:** this is an initial source prototype for local text chat, not a verified production messenger. The container used to create it has no Android SDK or Gradle installation, so a full Android APK build and two-physical-phone radio test could not be run here. The platform-neutral session-crypto code was compiled independently and passed a smoke test for matching ECDH keys, Unicode AES-GCM round-trip, nonce uniqueness and tamper rejection. Kotlin source parsing and manifest/resource XML parsing also passed, but this is not a full Android compile. Use the two-device checklist below before relying on it.

## What is new in v0.2

- **Works in the background.** A foreground service (`NearbyService`, type `connectedDevice`) keeps the connection and retry queue alive; the app now uses a process-wide repository instead of an Activity-scoped one. A notification with a **Stop** button is shown while it runs, and the service stops itself when nothing is active.
- **Impersonation fixed.** A device ID is now the SHA-256 of a long-term P-256 key held in the Android Keystore. Each session key exchange is signed with it and bound to the specific peer, so a contact cannot pretend to be another one.
- **Reconnect without re-pairing.** First contact still needs both users to accept and compare the code. Paired phones recognise each other, reconnect automatically, and are dropped if their identity does not match. The phone with the lower ID initiates, so the two sides never collide.
- **Oversized-message bug fixed.** Limits are now 4,000 characters / 8 KiB, control characters are stripped, and a message that can never fit is marked *Not sent* instead of retrying forever. A unit test measures the real wire size of worst-case messages.
- **Faster and lighter.** The Keystore handle is cached, retry loops stop when the peer leaves (and restart on reconnect), and discovery auto-pauses (10 min idle, 2 min after connecting) to save battery.
- **New features:** message notifications (private on the lock screen, tap opens the chat), rename your device, forget a device (deletes the chat and revokes the pairing), custom launcher icon, edge-to-edge insets for Android 15.
- Incoming messages use the local receive time, so a peer's clock cannot reorder your history.

## Included in this version

- Nearby advertising and discovery on both phones using the `P2P_CLUSTER` strategy.
- User-selected connection requests and a four-digit verification code. Both devices must accept before data can flow.
- Per-connection P-256 ECDH key agreement, HKDF-SHA256 key derivation, and AES-256-GCM encrypted message envelopes.
- Android Keystore AES-GCM encryption for message bodies saved in the local SQLite database.
- Persistent outgoing message queue, delivery acknowledgements, duplicate-ID protection, reconnect resend, and exponential-backoff retry until acknowledgement.
- Per-peer conversation history, online indicator, send state and a Compose chat interface.
- No account, phone number, cloud inbox, or app-owned relay server.

## Requirements

- Android 8.0 (API 26) or newer.
- Google Play services installed and working on both phones (Nearby Connections SDK 19.5.0). Nearby is a Google Play services component; review Google's current service privacy/diagnostics disclosures before deployment. The SDK may collect usage diagnostics and device/service information under Google Play services' policies.
- Bluetooth and Wi-Fi enabled. Grant the nearby-device permissions when prompted. Older Android versions may also ask for location permission because of the platform's discovery API requirements.
- Install the same Meshline package on both phones. A phone with no working Google Play services is not supported by this build.

## Open and build

1. Open the `Meshline` folder in Android Studio.
2. Use JDK 17 and allow Gradle to sync. The project is pinned to Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile SDK 35, and target SDK 35.
3. Android Studio will download Gradle 8.9 from `gradle/wrapper/gradle-wrapper.properties`. Install the app on two real Android phones. An emulator-only test cannot validate Bluetooth/Wi-Fi radio behaviour.
4. Launch Meshline on both phones and tap **Find nearby devices** on both.
5. On one phone, tap **Connect** next to the other. Compare the four-digit verification code shown on both screens. Accept only when they match, on both devices.
6. Open the new conversation and exchange messages in both directions.

To validate offline operation, first ensure both phones have working Google Play services and the app installed. Then turn off mobile data and disconnect both phones from the internet while leaving Bluetooth and Wi-Fi enabled. Discover, pair and exchange messages locally.

## Architecture

```text
Jetpack Compose UI
       │
       ▼
   ChatViewModel
       │
       ▼
 ChatRepository ───────── Nearby Connections
       │                 (Bluetooth / BLE / Wi-Fi)
       ├── SQLite outbox/history
       └── SessionCrypto
             ├── P-256 ECDH
             ├── HKDF-SHA256
             └── AES-GCM

SQLite message bodies are separately encrypted with an Android Keystore key.
```

### Message flow

1. Both phones advertise and discover the same service ID.
2. A user requests a connection. Each phone displays the same four-digit authentication code; both users accept.
3. The peers exchange their long-term identity keys (ID = hash of the key) and then ephemeral P-256 keys signed by those identities, over the already encrypted Nearby connection.
4. Each side independently derives the same AES session key. Messages and ACKs are then encrypted with AES-GCM and packet-ID associated data.
5. The sender persists the message before attempting transmission. The receiver inserts by unique message ID and sends an ACK. Duplicate deliveries are not inserted twice, but are ACKed again so a lost ACK can be repaired.
6. Messages without an ACK are retried with increasing delays. Undelivered messages remain in SQLite and are resent after a later secure reconnection.

## Important limitations — not hidden

- **No literal zero latency:** radio scheduling, discovery, device wake state and operating-system scheduling always add delay. The design minimises hops by sending directly between nearby devices.
- **Nearby Connections needs Google Play services.** The API selects among supported transports; this build does not expose a manual Wi-Fi-only/Bluetooth-only switch and does not include a second raw RFCOMM/Wi-Fi Direct engine for devices without Play services. Google Play services also has its own diagnostics/telemetry and privacy disclosures; this is not a zero-third-party-metadata design.
- **Background operation is new and untested.** The foreground service helps, but some phone makers (Xiaomi, Oppo, Samsung power saving, etc.) still kill background apps; exempt Meshline from battery optimisation if connections drop.
- No file/photo sharing, voice/video calls, group-chat UI, read receipts, typing indicators, contact sync, desktop/iOS client, multi-hop mesh relay, cloud relay or push notifications yet.
- No long-distance messaging: two phones must be within the range of the available local radio path. WhatsApp's internet/cloud delivery model solves a different problem; this app is designed for nearby offline communication.
- This build has not received a professional cryptographic review, penetration test, Play policy review, or hardware compatibility test. The manual four-digit comparison must be followed; do not accept a code that differs.

## Files

- `app/src/main/java/com/meshline/chat/network/ChatRepository.kt` — nearby lifecycle, protocol, outbox and retry logic.
- `app/src/main/java/com/meshline/chat/security/SessionCrypto.kt` — session key agreement and authenticated encryption.
- `app/src/main/java/com/meshline/chat/security/AtRestCrypto.kt` — local message-body encryption.
- `app/src/main/java/com/meshline/chat/data/ChatDatabase.kt` — durable peers and messages.
- `app/src/main/java/com/meshline/chat/ui/MeshlineApp.kt` — Compose screens.
- `app/src/main/java/com/meshline/chat/security/Identity.kt` — long-term identity key, signatures, self-certifying IDs.
- `app/src/main/java/com/meshline/chat/NearbyService.kt` — foreground service.
- `app/src/main/java/com/meshline/chat/notify/MessageNotifier.kt` — message notifications.
- `app/src/test/...` — crypto, identity and packet-size unit tests.
- `TEST_PLAN.md` — two-device and failure-recovery checks.
