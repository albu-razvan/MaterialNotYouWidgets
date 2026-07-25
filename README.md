# Material Not You Widgets

[![Latest release](https://img.shields.io/github/v/release/albu-razvan/MaterialNotYouWidgets)](https://github.com/albu-razvan/MaterialNotYouWidgets/releases/latest)

Yes, it says Material **NOT** You. No, these are not _your_ widgets. They're **MINE** 😈.

## Features

- **Zero Tolerance for Bad UI**  
  Supports Material You theming not because it cares about you, but because it respects itself.

- **Zero Permissions You Don't Need**  
  Internet. Foreground service. Boot completed. That's it.

- **Zero Information Shared**  
  Except the one you give it, duh...

- **Zero Bitches**  
  😔

## Widgets

### 1. Weather (Pill)

<img src="./public/weather_preview.gif" style="height: 200px">

A weather widget that looks good because it refuses not to.

#### Features

- **Dual Providers**  
  Choose between [MET Norway](https://api.met.no/weatherapi/) or [Open-Meteo](https://open-meteo.com/). Both are free. Neither will sell your data. I checked.

- **Morphing Animation Engine**  
  Tap to refresh and watch the pill morph into a 6-lobed clover, spin around like it's having a seizure, then morph back. Built with raw `Canvas`, `Choreographer`, and a concerning amount of parametric math. Animations run as a foreground service because Android doesn't let you do jack in the background anymore.

### 2. Quotes

<img src="./public/quotes_preview.png" style="height: 200px">

A quote widget to remember all the stupid crap your friends and random redditors have to say. You can also use it as your shopping list, if you must.

#### Features

- **Fresh Garbage, Hourly**  
  Every 60 minutes, a new quote is displayed. Because apparently one questionable thought at a time wasn't enough.

- **Multiple Quotes**  
  Load a single widget instance with as many questionable life lessons as you want. Tap to cycle through them.

- **Responsive**  
  The widget adapts to whatever nonsense you put inside. Font size adjusts dynamically to keep quotes readable across any widget size.</br><div align="right"><sub>Limits apply\*<sub></div>

## Compatibility

- **Android 12+** (SDK 31) — I'm not supporting your Galaxy S7
- Jailbroken Rabbit R1 — probably fine
- "Custom ROMs" — you can figure this shit out

## License

Do whatever you want, just don't blame me when it inevitably goes wrong (AKA [GNU General Public License v3](LICENSE)).
