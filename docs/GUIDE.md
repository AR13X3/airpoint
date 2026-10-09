# Airpoint user guide

## What you need

- A **Galaxy phone or tablet whose S Pen has Bluetooth**, meaning it supports *Air actions*,
  running Android 12 or later. Examples are the Galaxy S22, S23 and S24 Ultra, the Note 10
  and Note 20, and the Galaxy Tab S6 and later. Pens without Bluetooth, such as the one in the
  Galaxy S25 Ultra or the S Pen Fold Edition, won't work.
- A **Windows 10 or 11 PC** on the same Wi-Fi or LAN as the phone.

## Set up the computer

1. Download **Airpoint.exe** from the [latest release](https://github.com/AR13X3/airpoint/releases/latest)
   and run it. There's no installer, so keep it wherever you like. The app isn't code-signed
   yet, so Windows SmartScreen may show *Windows protected your PC*. Choose **More info ›
   Run anyway**. (All of its source code is in this repository.)
2. The first time, Windows Firewall asks whether to allow Airpoint. Choose **Private
   networks** and allow it. Without this, your phone can't find or reach the PC.
3. A loop icon appears in the system tray (you may need to click **^** to see it). Its
   point shows the state:

   | Icon | Meaning |
   | --- | --- |
   | Grey | Paused |
   | Blue point | Listening for a phone |
   | Green point | A phone is connected |

### The tray menu

Right-click the icon (or left-click to see the PIN):

- **Pairing PIN**: the 6-digit code a new phone needs. It changes after each pairing.
- **New PIN**: makes a fresh PIN.
- **Paired phones**: forget one phone, or all of them. A forgotten phone has to pair
  again.
- **Pause / Resume**: stops listening without quitting.
- **Start with Windows**: launches Airpoint when you sign in.
- **Open log folder**: logs and settings live in `%APPDATA%\Airpoint`.

## Set up the phone

1. On the phone, download **Airpoint-1.0.0.apk** from the [latest release](https://github.com/AR13X3/airpoint/releases/latest)
   and open it to install. If Android refuses:
   - Turn off **Auto Blocker** (Settings › Security and privacy › Auto Blocker). It blocks
     every app that doesn't come from the Galaxy Store or Play Store.
   - When asked, allow your browser (or Files app) to install unknown apps.
2. Open Airpoint. The welcome screens explain what it does and ask for two
   permissions:
   - **Nearby devices** is required. It's how the app talks to your S Pen over Bluetooth.
   - **Notifications** is recommended. It keeps Airpoint running while you use other apps
     and puts Center and Stop in your notification shade.
3. Tap **Start pointing**. The first time, this opens **Connect a computer**.
4. Your PC appears on the radar within a few seconds. Tap it.
5. Type the PIN shown in the PC's tray. Airpoint pairs, comes back to the home screen and
   starts pointing.

Next time, open Airpoint and tap **Start pointing**. It reconnects to the last computer
automatically.

## Pointing

| Do this with the S Pen | To |
| --- | --- |
| Wave it | Move the cursor |
| Press the button | Click |
| Hold the button and wave | Drag |
| Press twice quickly | Bring the cursor to the middle of the screen |
| Keep pushing past an edge | Scroll: up or down at the top and bottom, sideways at the sides |

- While Airpoint is pointing, pressing **Back** sends it to the background instead of
  closing it, because closing the app would release the S Pen. To stop, tap **Stop**, or
  use **Stop** in the notification.
- The **live pad** on the home screen mirrors your pen's motion, which is handy for
  checking the pen is being read.
- Edge scrolling is proportional: push harder to scroll faster. It pauses while you hold the
  button to drag. Turn it off in **Settings › Scroll at screen edges**.
- Double-press to center can be turned off in **Settings › Double-press to center**.

### Tuning

- **Speed** sets how far the cursor travels for a flick of the pen. Raise it for big
  screens.
- **Smoothing** sets how much the cursor's motion is eased. Higher is silkier, lower is
  snappier and more direct.

Both apply immediately while you point.

## Troubleshooting

**The PC doesn't show up on the radar**
- Check that Airpoint is running on the PC (look for the loop in the tray).
- Make sure both devices are on the same network. Guest networks and some office networks
  block devices from seeing each other.
- Allow Airpoint through Windows Firewall on private networks. If Windows didn't ask, open
  *Windows Security › Firewall & network protection › Allow an app through firewall*.
- As a fallback, open **Don't see your computer?** and enter the PC's IP address (shown
  in the tray tooltip).

**"Couldn't reach your S Pen"**
- Put the pen in its slot for a few seconds to wake and charge it, then try again.
- Make sure Bluetooth is on, and that **Air actions** is enabled in the phone's S Pen
  settings.
- Only one app can use the pen at a time. Close other apps that use Air actions.

**"No S Pen remote here"**: this device or pen has no Bluetooth S Pen (see
[What you need](#what-you-need)).

**"Doesn't know this phone"**: the phone was forgotten on the PC. Tap **Pair again** and
use the current PIN.

**"Too many tries"**: after several wrong PINs, pairing pauses for a while and the PIN
changes. Wait for the countdown, then use the new PIN from the tray.

**The cursor feels laggy or jumpy**
- 5 GHz Wi-Fi is noticeably smoother than 2.4 GHz.
- Lower **Smoothing** for a more direct feel, or raise it if motion looks shaky.

## Privacy

Airpoint has no accounts, analytics or internet connection. The phone and PC talk only to
each other over your local network. Pairing tokens and settings stay on your devices: in
the app's private storage on the phone, and in `%APPDATA%\Airpoint` on the PC.
