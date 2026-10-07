# Airpoint protocol (v1)

How the Android app and the desktop app talk. Everything is JSON over the local network.
The desktop listens on **port 8765**, on both UDP (discovery) and TCP (WebSocket).

```
 Phone                                          PC
   │  UDP broadcast  {"type":"airpoint.discover"}  │
   │ ─────────────────────────────────────────────▶│
   │  {"type":"airpoint.announce", id, name, port} │
   │◀───────────────────────────────────────────── │
   │                                               │
   │  WebSocket  ws://<pc>:8765                    │
   │  hello {token | pin}                          │
   │ ─────────────────────────────────────────────▶│  authenticate
   │  welcome {pc_id, name, token?}                │
   │◀───────────────────────────────────────────── │
   │  motion / button / center / config  ...       │
   │ ─────────────────────────────────────────────▶│  move the cursor
```

## 1. Discovery (UDP)

The phone broadcasts a datagram to `255.255.255.255:8765` and to each interface's subnet
broadcast address:

```json
{"type": "airpoint.discover", "v": 1}
```

Every Airpoint desktop on the network replies to the sender:

```json
{"type": "airpoint.announce", "v": 1, "id": "9f37a0d1…", "name": "DESKTOP-JOY", "port": 8765, "version": "1.0.0"}
```

`id` is a random identifier created on the PC's first run. It stays the same when the PC's
IP address changes. The phone uses the reply's **source address** as the PC's host, not a
self-reported IP: a PC can have several network adapters (VPNs, virtual machines), and the
address that answered is the one that can be reached.

The phone repeats the broadcast every 1.6 s while the pairing screen is open. If a paired
PC goes quiet during a session, the phone also uses discovery to find it again at a new IP.

## 2. Session (WebSocket)

### Handshake

The first frame from the phone must be `hello`, within 5 seconds:

```json
{"type": "hello", "v": 1, "device": "Galaxy S23 Ultra", "device_id": "<uuid>", "token": "<token>"}
```

or, when pairing for the first time:

```json
{"type": "hello", "v": 1, "device": "Galaxy S23 Ultra", "device_id": "<uuid>", "pin": "482913"}
```

On success, the PC answers:

```json
{"type": "welcome", "v": 1, "pc_id": "9f37a0d1…", "name": "DESKTOP-JOY", "version": "1.0.0", "token": "<only after PIN pairing>"}
```

On failure, the PC sends an error and then closes the socket:

| `code` | Close code | Meaning |
| --- | --- | --- |
| `pairing_required` | 4401 | No token, or the token isn't recognised (never paired, or forgotten on the PC) |
| `bad_pin` | 4403 | Wrong PIN |
| `locked_out` | 4429 | Too many wrong PINs; `retry_after` gives the seconds to wait |
| `unsupported_version` | 4426 | `v` isn't a version this PC speaks |
| `bad_request` | 4400 | The first frame wasn't a valid `hello` |

```json
{"type": "error", "code": "locked_out", "retry_after": 60}
```

### Events (phone → PC)

Events are ignored until `welcome` has been sent.

| Message | Effect |
| --- | --- |
| `{"type":"motion","dx":12.5,"dy":-3.0}` | Move the cursor by (dx, dy) pixels, eased (see below) |
| `{"type":"button","action":"down"}` / `"up"` | Press or release the left mouse button |
| `{"type":"center"}` | Jump the cursor to the centre of the primary display |
| `{"type":"config","smooth_alpha":0.42,"edge_scroll":true}` | Set easing, 0.05 (silkiest) to 1.0 (instant), and whether pushing past a screen edge scrolls. Either key may be omitted. |

Numbers are sanitised on the PC (non-finite values are dropped and large ones clamped).
Unknown message types are ignored, so newer phones can add events without breaking older
PCs.

### Motion pipeline

1. **Phone.** S Pen air-motion deltas are multiplied by the user's speed setting and
   accumulated. A timer flushes the sum every 12 ms (about 83 frames/s), so a burst of pen
   events becomes one frame instead of flooding the socket. Nagle's algorithm is disabled
   so each frame leaves immediately.
2. **PC.** Incoming deltas are queued. A 200 Hz loop moves the cursor by `alpha` × the
   remaining distance per tick and carries sub-pixel remainders forward, which turns
   bursty Wi-Fi delivery into smooth motion. The loop sleeps when there's nothing queued.

**Edge scrolling.** Each tick, the PC adds the requested move to the cursor's position and
asks Windows whether that point lies on any monitor. If it doesn't, the overshoot past the
nearest monitor's edge is a push, and 40 px of push equals one wheel notch. (Comparing
requested with actual movement is unreliable, because Windows drops about 1% of cursor
moves, and those would read as phantom edge hits.) Three details make it usable:

- **Aimed at the content.** At an edge, the window under the cursor is the taskbar or a
  title bar, which ignore the wheel. Each notch is injected 200 logical px inside the edge
  (scaled for the monitor's DPI) as one atomic input batch: move there, wheel, move back.
  The cursor stays at the edge.
- **The edge absorbs a flick.** Hitting an edge drops the rest of that flick's queued motion,
  so reaching for a tab doesn't lurch the page. Only continued pushing scrolls.
- **Not while dragging.** Apps already auto-scroll a drag that reaches an edge.

If a phone disconnects while holding the button, the PC releases it, so the mouse can't
get stuck down.

## 3. Security model

Airpoint assumes a home or office LAN and protects against other people on the same
network moving your cursor:

- **Pairing needs physical access.** The 6-digit PIN appears only in the PC's tray. It is
  single-use: it rotates after every successful pairing.
- **Guessing is impractical.** Five wrong PINs within five minutes rotate the PIN and lock
  pairing. Consecutive lockouts double (1 min, 2, 4 … capped at an hour) until a phone
  pairs successfully, and the tray warns you each time. A persistent guesser gets roughly
  120 tries a day against a PIN that keeps changing, about a 1-in-8,000 daily chance.
- **Tokens, not passwords.** A paired phone gets a 256-bit random token. The PC stores only
  its SHA-256 hash, and comparisons are constant-time. "Forget" in the tray revokes a
  phone immediately.
- **Browsers are refused.** Any WebSocket request carrying an `Origin` header gets a 403,
  so a web page can't script the local server (cross-site WebSocket hijacking).
- **Bounded input.** Frames are capped at 4 KB, and `hello` must arrive within 5 s.

Traffic is not encrypted (`ws://`, not `wss://`). Someone who can already sniff your LAN
could read motion events and capture a token. Use Airpoint on networks you trust.
