## 2.8.1
- (Android) Fix decline/reject in terminated state;

## 2.8.0
- Update AGP to 8.9.0 and fix related issues;

## 2.7.0
- Implemented closing of the iOS CallKit by VoIP push notification where `signal_type` is 'endCall' or 'rejectCall';

## 2.6.0
- Implemented the Notify for Incoming Calls feature (thanks for [tungs0ul](https://github.com/tungs0ul));

## 2.5.0
- (Android) Add API to manage the `Manifest.permission.USE_FULL_SCREEN_INTENT` permission;

## 2.4.0
- (Android) Add the Call photo to the Call notification and the Incoming call screen;
- (Android) Add different icons to the Accept buttons depending on the call type;
- (Android) Add the possibility for setting the Notification icon depending on the call type;

## 2.3.0
- (iOS) Add a method for notifying the CallKit about muting/unmuting the call;
- (iOS) Improvements for audio after accepting the call from the background or killed state;
- (Dart) Add ignoring of not supported platforms;

## 2.2.4
- (iOS) Improve the audio after accepting from the background or killed state;

## 2.2.3
- (Android) Fix calling of the `onCallRejectedWhenTerminated` and the `onCallAcceptedWhenTerminated` callbacks in the release build;

## 2.2.2
- (iOS) Fix the crash on the `getLastCallId` method calling;

## 2.2.1

- (Android) Fix receiving the FCM if plugin connected together with `firebase_messaging` plugin;

## 2.2.0

- (iOS) Fix notifying plugin about call accepting;

Broken API:
  - changed the signature of method `reportCallAccepted` by deleting the parameter `callType`;

## 2.1.0

- (Android) Add the possibility of setting a Notification icon;

## 2.0.9

- (iOS) Fix getting call state;

## 2.0.8

- (iOS) Fix second and next calls issue;

## 2.0.7

- (iOS) Fix audio after accepting from killed state;

## 2.0.6

- (iOS) Fix crashing on startup ([#44](https://github.com/ConnectyCube/connectycube-flutter-call-kit/issues/44))

## 2.0.5

- (Android) fix the compatibility with `Flutter` **3.x.x**;

## 2.0.4

- (Android) update `Gradle` version to the **6.5**;
- (Android) update `Kotlin` version to the **1.6.21**;
- (Android) megrate from the `jcenter()` to the `mavenCentral()` dependesies repository;

## 2.0.3

- (Android) fixed the working with apps targeted to the `targetSdkVersion 31` and above;

## 2.0.2

- (iOS, Android) fixed the `user_info` data transporting;
- (Android) fixed launching the app by `acceptCall` event;

## 2.0.1

* Minor updates

## 2.0.0
Completely reworked version. Reworked the way of interaction between the flutter app and native platforms.

Since this version you don't need any third-party plugins for working with push notifications anymore, cause all required functionality has already been integrated into the plugin.

**New**
- Added iOS support
- Added getting the subscription tokens (VoIP for the iOS and FCM for the Android)
- Added customisation for ringtone, app icon, color accent (for Android)

**Fixes and improvements**
- reworked callbacks `onCallRejectedWhenTerminated` and `onCallAcceptedWhenTerminated` now they will be fired even if the app is terminated or in the background
- migrated to `EventChannel` for sending events from native platforms to the Flutter app

## 0.1.0-dev.2

* Improved compatibility with projects which support Web platform.

## 0.1.0-dev.1

* New:
    - Implemented Dart [null-safety](https://dart.dev/null-safety) feature;
    - Added method `getCallData(String? sessionId)` for getting all provided data about the call;
    - Added method `clearCallData(String? sessionId)` which cleans all data related to the call;
    - Added method `getLastCallId()` which returns the id of the last displayed call. It is useful on starting app step for navigation to the call screen if the call was accepted;
    - Added static callback `onCallAcceptedWhenTerminated` which can be useful if need listen to events from the Call notification in the background or terminated state;

* Improvements:
    - Added new field `userInfo`, which can be used for exchanging with additional data between the Call notification and your app, you will get this data in callbacks `onCallAcceptedWhenTerminated`, `onCallAccepted`, `onCallRejectedWhenTerminated`, `onCallRejected` after setting it in method `showCallNotification`;

* Fixes:
    - Fixed the wrong calback naming `onCallAcceptedWhenTerminated` -> `onCallRejectedWhenTerminated`;

## 0.0.1-dev.1

* Initial release.
