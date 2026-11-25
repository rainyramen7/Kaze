# Kaze (風) — Android Companion

A minimalist Android companion app for local-Wi-Fi, manual-push clipboard & quick file drops with your Mac ([KazeMac](https://github.com/rainyramen7/KazeMac)), crafted in the **Suzuri & Persimmon (柿)** dark inkstone aesthetic.

## Features

- **Manual Push Only:** Push your Android clipboard via 1 tap in-app, the **Quick Settings Tile**, or send photos/files/links directly from Android's **Share Sheet** (`Drop via Kaze`).
- **Floating Incoming Breeze Overlay:** Slides a translucent Suzuri toast over any app when you press `⌘⇧K` on your Mac, with explicit **Copy** / **Open** actions (never silently overwrites your clipboard).
- **Zero-Trust QR + 6-Digit PIN Pairing:** Offline CameraX + ZXing QR scanner with 6-digit PIN fallback and automatic `_kaze._tcp.` LAN discovery.
- **Hardware-Backed P-256 Security:** Device signing key lives in **Android Keystore**; paired Mac fingerprint is pinned in Keystore-encrypted storage. Every transfer runs over an ephemeral ECDH (P-256) + mutual ECDSA + HKDF-SHA256 + streaming AES-256-GCM tunnel.
- **Privacy & Safe Drops:** Honors `ClipDescription.EXTRA_IS_SENSITIVE` (Android 13+), keeps history in ephemeral RAM only, sets `allowBackup="false"` and `usesCleartextTraffic="false"`, sanitizes filenames into `Downloads/Kaze`, and quarantines executable/oversized files for confirmation.

## Architecture

Kaze runs entirely over your local Wi-Fi network with zero cloud servers. mDNS (`_kaze._tcp.`) is used only to locate your Mac's current LAN IP/port; all trust comes from pinned P-256 public keys.

```mermaid
flowchart LR
    subgraph Android["Kaze (Android · Kotlin + Compose)"]
        UI["MainScreen & PairingScreen\n(CameraX + ZXing QR)"]
        Entry["ShareDropActivity (Share Sheet)\nPushClipboardTileService (QS Tile)"]
        Overlay["BreezeOverlayController\n(Floating Suzuri Toast)"]
        Net["KazeConnectionManager\n& KazeConnectionService"]
        Nsd["KazeNsdDiscovery\n(NsdManager _kaze._tcp.)"]
        SecA["KazeIdentity (Android Keystore)\nTrustedMacStore & FileSafety"]

        UI --> Net
        Entry --> Net
        Net --> Overlay
        Nsd -. "LAN IP/Port hint" .-> Net
        SecA <--> Net
    end

    subgraph LAN["Local Wi-Fi (Zero Cloud)"]
        Tunnel["Mutual P-256 Handshake\n+ AES-256-GCM Framed Stream\n(JSON Control + 64KB Chunks)"]
    end

    subgraph Mac["KazeMac (macOS · Swift + SwiftUI)"]
        Server["KazeServer\n(NWListener + Bonjour)"]
        SecM["KazeIdentity (Keychain)\nTrustedPeerStore & FileSafety"]
        MacUI["MenuBarPopoverView\nGlobalHotkeyManager (⌘⇧K)\nClipboardGuard & QRCodeView"]

        Server <--> SecM
        MacUI <--> Server
    end

    Net <==> Tunnel
    Tunnel <==> Server
```

### Pairing & Encrypted Transfer Flow

```mermaid
sequenceDiagram
    participant Phone as Kaze (Android)
    participant Mac as KazeMac (macOS)

    Note over Mac: User clicks "Pair Phone (QR)"<br/>60s single-use token + 6-digit PIN
    Mac-->>Phone: QR Code (LAN IP, Port, Mac P-256 Fingerprint, Token)

    Phone->>Mac: 1. ClientHello (Phone IdPub, EphPub, Nonce, HMAC Pairing Proof)
    Note over Mac: Verifies pinned peer OR consumes 60s HMAC proof
    Mac->>Phone: 2. ServerHello (Mac IdPub, EphPub, Nonce, ECDSA Signature)
    Note over Phone: Verifies pinned Mac Fingerprint + ECDSA Signature
    Phone->>Mac: 3. ClientAuth (ECDSA Signature over Transcript Hash)
    Note over Phone,Mac: Both derive c2s/s2c keys via ECDH P-256 + HKDF-SHA256<br/>and pin each other's P-256 SHA-256 fingerprint

    Mac->>Phone: AES-256-GCM Frame: Clipboard Push (⌘⇧K)
    Phone-->>Phone: Shows floating Suzuri toast (Copy / Open)

    Phone->>Mac: AES-256-GCM Frames: File Start + 64KB Chunks + SHA-256 End
    Mac-->>Mac: Verifies SHA-256 -> ~/Downloads/Kaze (or Quarantine)
```

### Key Components

| Component | Path | Responsibility |
| :--- | :--- | :--- |
| **Crypto & Wire Codec** | `crypto/KazeCrypto.kt`, `crypto/KazeProtocol.kt` | Ephemeral P-256 ECDH, ECDSA signatures, HKDF-SHA256, AES-256-GCM framing, QR URI parser |
| **Hardware Identity** | `crypto/KazeIdentity.kt`, `net/TrustedMacStore.kt` | Android Keystore P-256 signing key & Keystore-encrypted pinned Mac store |
| **LAN Session & NSD** | `net/KazeConnectionManager.kt`, `net/KazeConnectionService.kt`, `net/KazeNsdDiscovery.kt` | Bonjour/mDNS lookup, persistent encrypted TCP session, chunked file I/O, MediaStore saving |
| **Entry & Overlay** | `overlay/BreezeOverlayController.kt`, `share/ShareDropActivity.kt`, `tile/PushClipboardTileService.kt` | Floating incoming toast, Android Share Sheet drop UI, Quick Settings manual push tile |
| **Suzuri UI** | `ui/theme/SuzuriTheme.kt`, `ui/components/WindBridgeCanvas.kt`, `ui/components/KakiMotif.kt` | Dark inkstone palette, animated wind-bridge canvas, persimmon motif, offline QR viewfinder |

## Building & Testing

```bash
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
