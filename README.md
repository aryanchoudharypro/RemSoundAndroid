# RemSoundAndroid

RemSoundAndroid is the official Android receiver client for [RemSound](https://github.com/Ednun/RemSound) - a high-performance, natively-encrypted UDP audio streaming program. It allows you to seamlessly receive and listen to low-latency audio from your desktop computer directly on your Android phone.

This project was built from the ground up to provide a native, lightweight, and highly accessible audio receiver experience for Android users.

## Features

* Low-Latency Streaming: Native UDP socket implementation perfectly matched with the desktop client's byte framing for near-zero latency audio playback.
* Rock-Solid Connection: Includes full support for RemSound's Ping/Pong heartbeat handshake to keep the connection alive indefinitely.
* Background Playback: Audio streaming is handled via a resilient Foreground Service, ensuring playback never drops even when the app is minimized or your screen is locked.
* First-Class Accessibility: Built specifically with screen readers in mind. The UI uses semantic merging to ensure volume and buffer sliders are just 1 item each to talkback.

## Installation

You can download the latest compiled `app-release.apk` directly from the [Releases](../../releases) page. Just download the file to your Android phone, tap to install, and you're ready to go!

## How to Use

1. Start  RemSound  on your PC, make sure you setup audio sending, like configuring the audio source and Enabling  the send audio checkbox.
2. Open the RemSoundAndroid app on your phone.
3. Your phone should automatically be discovered on your local network by your PC.
4. If auto-discovery fails, simply type your PC's IP address and the connection password into the app.
5. Hit Start! The foreground service will launch, and your phone will begin receiving audio.

## Building from Source

If you prefer to compile the project yourself:
1. Clone the repository.
2. cd to the repo. 
3. Run `.\gradlew assembleDebug` to build.

## Credits

A massive thank you to the original author of the RemSound desktop application for designing such an incredible and robust program. This Android client was built to interoperate flawlessly with their hard work.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
