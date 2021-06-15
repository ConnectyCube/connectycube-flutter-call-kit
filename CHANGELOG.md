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
