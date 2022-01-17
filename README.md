# connectycube_flutter_call_kit

A Flutter plugin for displaying call screen when the app is in the background or terminated.
It provides a complex solution for implementation the background calls feature in your app including getting token and displaying the Incoming call screen.

At that moment plugin supports next platforms:
- Android;
- iOS;

The list of main features of our plugin:
- providing the token (FCM for Android and VoIP for iOS);
- notifying the app about token refreshing via callback;
- displaying the Incoming call screen when push notification was delivered on the device;
- notifying the app about user action performed on the Incoming call screen (accept, reject, mute (for iOS));
- providing the methods for manual managing of the Incoming screen including the manual showing the Incoming call screen;
- getting the data about the current call during the call session;
- some customizations according to your needs (ringtone, icon, accent color(for Android));


<kbd><img alt="Flutter P2P Calls code sample, incoming call in background Android" src="https://developers.connectycube.com/docs/_images/code_samples/flutter/background_call_android.png" height="440" /></kbd> <kbd><img alt="Flutter P2P Calls code sample, incoming call locked Android" src="https://developers.connectycube.com/docs/_images/code_samples/flutter/background_call_android_locked.png" height="440" /></kbd> <kbd><img alt="Flutter P2P Calls code sample, incoming call in background iOS" src="https://developers.connectycube.com/docs/_images/code_samples/flutter/background_call_ios.PNG" height="440" /></kbd>
<kbd><img alt="Flutter P2P Calls code sample, incoming call locked iOS" src="https://developers.connectycube.com/docs/_images/code_samples/flutter/background_call_ios_locked.PNG" height="440" /></kbd>

## Configure your project

This plugin doesn't require complicated configs, just [connect it](https://pub.dev/packages/connectycube_flutter_call_kit/install) as usual flutter plugin to your app and do the next simple actions:

### Prepare Android

- add the Google services config file `google-services.json` by path `your_app/android/app/`
- add next string at the end of your **build.gradle** file by path `your_app/android/app/build.gradle`:
```groovy
apply plugin: 'com.google.gms.google-services'
```

### Prepare iOS

- add next strings to your **Info.plist** file by path `your_app/ios/Runner/Info.plist`:
```
<key>UIBackgroundModes</key>
<array>
    <string>remote-notification</string>
    <string>voip</string>
</array>
```

## API and callbacks
### Get token
The plugin returns the VoIP token for the iOS platform and the FCM token for the Android platform.

Get token from the system:

```dart
ConnectycubeFlutterCallKit.getToken().then((token) {
    // use received token for subscription on push notifications on your server
});
```

Listen to the refresh token event:
```dart
ConnectycubeFlutterCallKit.onTokenReceived = (token) {
    // use refreshed token for resubscription on your server
};
```
### Customize the plugin
We added a helpful method for customization the plugin according to your needs. At this moment you can customize the ringtone, icon, and color accent. Use the next method for it:

```dart
ConnectycubeFlutterCallKit.instance.updateConfig(ringtone: 'custom_ringtone', icon: 'app_icon', color: '#07711e');
```

### Show Incoming call notification

```dart
P2PCallSession incomingCall; // the call received somewhere

CallEvent callEvent = CallEvent(
    sessionId: incomingCall.sessionId,
    callType: incomingCall.callType,
    callerId: incomingCall.callerId,
    callerName: 'Caller Name',
    opponentsIds: incomingCall.opponentsIds,
    userInfo: {'customParameter1': 'value1'});
ConnectycubeFlutterCallKit.showCallNotification(callEvent);
```

### Listen to the user action from the Incoming call screen:

#### Listen in the foreground

Add the listeners during initialization of the plugin:

```dart
ConnectycubeFlutterCallKit.instance.init(
    onCallAccepted: _onCallAccepted,
    onCallRejected: _onCallRejected,
);

Future<void> _onCallAccepted(CallEvent callEvent) async {
    // the call was accepted
}

Future<void> _onCallRejected(CallEvent callEvent) async {
    // the call was rejected
}
```

#### Listen in the background or terminated state (Android only):

```dart
ConnectycubeFlutterCallKit.onCallRejectedWhenTerminated = onCallRejectedWhenTerminated;
ConnectycubeFlutterCallKit.onCallAcceptedWhenTerminated = onCallAcceptedWhenTerminated;
```

!> Attention: the functions `onCallRejectedWhenTerminated` and `onCallAcceptedWhenTerminated` must be a top-level function and cannot be anonymous

### Get the call state

```dart
var callState = await ConnectycubeFlutterCallKit.getCallState(sessionId: sessionId);
```

### Get the call data
```dart
ConnectycubeFlutterCallKit.getCallData(sessionId: sessionId).then((callData) {
      
});
```

### Get the id of the latest call

It is helpful for some cases to know the id of the last received call. You can get it via:

```dart
var sessionId = await ConnectycubeFlutterCallKit.getLastCallId();
```
Then you can get the state of this call using `getCallState`.

### Notify the plugin about processing the call on the Flutter app side

For dismissing the Incoming call screen (or the Call Kit for iOS) you should notify the plugin about these events.
Use next functions for it:

```dart
ConnectycubeFlutterCallKit.reportCallAccepted(sessionId: uuid, callType: callType);
ConnectycubeFlutterCallKit.reportCallEnded(sessionId: uuid);
```

### Clear call data
After finishing the call you can clear all data on the plugin side related to this call, call the next code for it

```dart
await ConnectycubeFlutterCallKit.clearCallData(sessionId: sessionId);
```

### Manage the app visibility on the lock screen (Android only)

In case you need to show your app after accepting the call from the lock screen you can do it using the method
```dart
ConnectycubeFlutterCallKit.setOnLockScreenVisibility(isVisible: true);
```

After finishing that call you should hide your app under the lock screen, do it via
```dart
ConnectycubeFlutterCallKit.setOnLockScreenVisibility(isVisible: false);
```

## Show Incoming call screen by push notification
In case you want to display the Incoming call screen automatically by push notification you can do it easily. For it, the caller should send the push notification to all call members. This push notification should contain some required parameters. If you use the [Connectycube Flutter SDK](https://pub.dev/packages/connectycube_sdk), you can do it using the next code:

```dart
CreateEventParams params = CreateEventParams();
params.parameters = {
    'message': "Incoming ${currentCall.callType == CallType.VIDEO_CALL ? "Video" : "Audio"} call",
    'call_type': currentCall.callType,
    'session_id': currentCall.sessionId,
    'caller_id': currentCall.callerId,
    'caller_name': callerName,
    'call_opponents': currentCall.opponentsIds.join(','),
    'signal_type': 'startCall',
    'ios_voip': 1,
 };

params.notificationType = NotificationType.PUSH;
params.environment = CubeEnvironment.DEVELOPMENT; // not important
params.usersIds = currentCall.opponentsIds.toList();

createEvent(params.getEventForRequest()).then((cubeEvent) {
      // event was created
}).catchError((error) {
      // something went wrong during event creation
});
```

For hiding the Incoming call screen via push notification use a similar request but with a different `signal_type`, it can be `'endCall'` or `'rejectCall'`.

You can check how this plugin works in our [P2P Calls code sample](https://github.com/ConnectyCube/connectycube-flutter-samples/tree/master/p2p_call_sample).