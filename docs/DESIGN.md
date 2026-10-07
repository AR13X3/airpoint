# Design

How Airpoint looks, moves and sounds, and why. Everything here is implemented in code.
Each section links to its source.

## The mark

<p>
  <img src="../assets/brand/airpoint-mark.svg" width="120" alt="The Airpoint mark">
</p>

**The Loop** is a handwritten gesture through the air that lifts off into a point:
*air*, then *point*. It is also literally what you do with Airpoint. A flick of the S Pen
travels through the air and lands as a cursor on screen.

### Construction

The stroke is not drawn by hand. It is a **prolate trochoid**, the path traced by a point
on a rolling circle, extended past the rim so it loops exactly once:

```
x(t) = t − d·sin t        t ∈ [−2.35, 3.2]
y(t) = 1 − d·cos t        d = 1.9
```

The curve is rotated −12° so it rises into the point, then stroked with a **taper** that
swells from 1.8 to 6 units (eased with an exponent of 1.2), like a nib gaining pressure.
After a gap of air comes the **point**, a circle placed along the stroke's final tangent.
Everything is normalized to a 48-unit grid.

Because the mark is generated, every asset comes from the same parameters, and nothing
drifts out of sync:

```
tools/brand/build_brand.py
 ├─ assets/brand/*.svg               artwork
 ├─ android …/res/drawable/*.xml     launcher, themed icon, notification, splash
 ├─ android …/ui/brand/LoopGeometry  centerline for the write-on animation
 └─ desktop/airpoint/_mark.py        tray icon paths
```

### Optical cuts

| | Display | Small |
| --- | --- | --- |
| Use | 32 px and up | 24 px and below (tray, notifications) |
| Stroke | 1.8 → 6.0 | 3.6 → 7.6 |
| Loop | d = 1.9 | d = 2.05 (more open counter) |
| Point | r = 4.4 | r = 5.2 |

At 16 px the display cut's hairline start disappears and its counter closes up, so the
small cut is heavier and more open, the way type families ship separate optical sizes.

### The point carries status

In the Windows tray the stroke stays neutral and only the point changes color: blue while
listening, green when a phone is connected, grey when paused. The mark becomes the status
indicator, so no extra badge is needed.

## Color

One accent and two neutrals. Every text color clears **WCAG AA (4.5:1)** on every surface
it appears on, and status colors differ in **lightness as well as hue**, so they stay
distinguishable with color-vision deficiency.

| Token | Dark | Light | Role |
| --- | --- | --- | --- |
| Ink | `#0D0F14` | (text) | Brand neutral; dark background |
| Paper | (text) | `#F4F4F1` | Brand neutral; light background |
| Electric | `#3D6BFF` | `#3D6BFF` | The brand accent: the mark and app icon |
| Electric (UI) | `#3B69FF` | `#3B69FF` | Buttons and fills. A hair deeper, so white labels reach 4.5:1 |
| Accent text | `#8DA8FF` | `#2B55E6` | Accent used as text |
| Live | `#3DD68C` | `#0B7B49` | Connected, clicks |
| Warn | `#FFB547` | `#985D00` | Recoverable problems |
| Danger | `#FF6B5E` | `#C13528` | Destructive actions, wrong PIN |

Source: [`ui/theme/Theme.kt`](../android/app/src/main/kotlin/io/github/ar13x3/airpoint/ui/theme/Theme.kt)

## Type

- **Sora** SemiBold sets the wordmark, headlines, buttons and numbers. Its geometric,
  slightly wide forms echo the Loop's round terminals. Numbers use tabular figures so
  changing values don't jitter.
- **Body copy uses the platform font** (Roboto, or SamsungOne on Galaxy devices), so long
  text reads like the rest of the phone.

Source: [`ui/theme/Type.kt`](../android/app/src/main/kotlin/io/github/ar13x3/airpoint/ui/theme/Type.kt)

## Motion

Motion explains state. Nothing animates just to decorate.

| Principle | In practice |
| --- | --- |
| **Springs, not timers** | UI moves on springs, so an interrupted animation (tapping Start, then Stop) continues smoothly from where it is instead of restarting. |
| **Two temperaments** | *Calm* (no overshoot) for layout and color. *Lively* (slight overshoot) only for things the user directly caused: presses, toggles, success. |
| **Real data drives the visuals** | Particles on the link stage spawn from actual S Pen air-motion, and the live pad mirrors the pen in real time. When you see movement, you made it. |
| **Text rises in place** | Changing headlines lift the old line away as the new one rises, so the eye follows the change instead of being hit by a cut. |
| **Respect the setting** | With *Remove animations* on, ambient loops, particles and the write-on reveal switch off. Information is never only in motion. |

Signature moments:

- **The write-on.** The Loop draws itself from its centerline as round-capped segments of
  growing width, then the point lands with a small bounce (onboarding).
- **The link stage.** S Pen, then phone, then PC. Wires are dotted when off, march while
  connecting, glow when live and break on failure. A click sends a green packet down the
  wire that rings when it reaches the PC.
- **The live pad.** The cursor's trail tapers like the Loop's stroke in reverse. Presses
  ripple, and double-press springs the cursor home.
- **The PIN.** Digits pop in, a wave runs through the cells while checking, a wrong PIN
  shakes with a reject haptic, and success settles green and morphs into a check.

Source: [`ui/theme/Motion.kt`](../android/app/src/main/kotlin/io/github/ar13x3/airpoint/ui/theme/Motion.kt),
[`ui/components/`](../android/app/src/main/kotlin/io/github/ar13x3/airpoint/ui/components)

## Haptics

Touch feedback comes from the same vocabulary as the visuals: a toggle tick on Start and
Stop, a segment tick every 10 steps on a slider, a keyboard tap per PIN digit, confirm on
pairing and reject on a wrong PIN.

## Words

- Say what happened and what to do next: "Couldn't reach your S Pen. Check that it's
  charged and nearby, Bluetooth is on, and no other app is using it."
- No error codes on screen. The old app showed "Connect failed (error -2)".
- Name things the way people do: "Speed" rather than "sensitivity multiplier", "Your
  computer" rather than "WebSocket server".

## Icons

A small custom set ([`ui/icons/AirIcons.kt`](../android/app/src/main/kotlin/io/github/ar13x3/airpoint/ui/icons/AirIcons.kt)):
24 dp, 1.8 dp strokes, round caps and joins to match the Loop. Drawn in code, so there's no
icon-font dependency.
