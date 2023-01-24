import 'dart:async';
import 'dart:developer';
import 'package:flutter/services.dart';
import 'package:universal_io/io.dart';

import 'call_event.dart';

/// Function type for handling accepted and rejected call events
typedef CallEventHandler = Future<dynamic> Function(CallEvent event);

/// {@template connectycube_flutter_call_kit}
/// Plugin to manage call events and notifications
/// {@endtemplate}
class ConnectycubeFlutterCallKit {
  static const MethodChannel _methodChannel =
      const MethodChannel('connectycube_flutter_call_kit.methodChannel');
  static const EventChannel _eventChannel =
      const EventChannel('connectycube_flutter_call_kit.callEventChannel');

  /// {@macro connectycube_flutter_call_kit}
  factory ConnectycubeFlutterCallKit() => _getInstance();

  const ConnectycubeFlutterCallKit._internal();

  static ConnectycubeFlutterCallKit get instance => _getInstance();
  static ConnectycubeFlutterCallKit? _instance;
  static String TAG = "ConnectycubeFlutterCallKit";

  static ConnectycubeFlutterCallKit _getInstance() {
    if (_instance == null) {
      _instance = ConnectycubeFlutterCallKit._internal();
    }
    return _instance!;
  }

  static Function(String newToken)? onTokenRefreshed;

  /// iOS only callbacks
  static Function(bool isMuted, String sessionId)? onCallMuted;

  /// end iOS only callbacks

  static CallEventHandler? _onCallAccepted;
  static CallEventHandler? _onCallRejected;

  /// Initialize the plugin and provided user callbacks.
  ///
  /// ⚠️ This function should only be called once at the beginning of
  /// your application.
  /// [ringtone] - the name of the ringtone source (for Anfroid it should be placed by path 'res/raw', for iOS it is a name of ringtone)
  /// [icon] - the name of image in the `drawable` folder for Android and the name of Assests set for iOS
  /// [notificationIcon] - the name of the image in the `drawable` folder, uses as Notification Small Icon for Android, ignored for iOS
  /// [color] - the color in the format '#RRGGBB', uses as an Android Notification accent color, ignored for iOS
  void init({
    required CallEventHandler onCallAccepted,
    required CallEventHandler onCallRejected,
    String? ringtone,
    String? icon,
    String? notificationIcon,
    String? color,
  }) {
    _onCallAccepted = onCallAccepted;
    _onCallRejected = onCallRejected;

    _methodChannel.invokeMethod('updateConfig', {
      'ringtone': ringtone,
      'icon': icon,
      'notification_icon': notificationIcon,
      'color': color,
    });

    initEventsHandler();
  }

  static void initEventsHandler() {
    _eventChannel.receiveBroadcastStream().listen((rawData) {
      print('[initEventsHandler] rawData: $rawData');
      final eventData = Map<String, dynamic>.from(rawData);

      _processEvent(eventData);
    });
  }

  /// Returns VoIP token for iOS plaform.
  /// Returns FCM token for Android platform
  static Future<String?> getToken() {
    return _methodChannel.invokeMethod('getVoipToken', {}).then((result) {
      return result?.toString();
    });
  }

  /// Report that the current active call has been accepted by your application
  ///
  static Future<void> reportCallAccepted({required String sessionId}) async {
    return _methodChannel
        .invokeMethod("reportCallAccepted", {'session_id': sessionId});
  }

  /// Report that the current active call has been ended by your application
  static Future<void> reportCallEnded({required String sessionId}) async {
    return _methodChannel.invokeMethod("reportCallEnded", {
      'session_id': sessionId,
    });
  }

  /// Get the current call state
  ///
  /// Other platforms than Android and iOS will receive [CallState.unknown]
  static Future<String> getCallState({required String sessionId}) async {
    return _methodChannel.invokeMethod("getCallState", {
      'session_id': sessionId,
    }).then((state) {
      return state.toString();
    });
  }

  /// Updates the current call state
  static Future<void> setCallState({
    required String sessionId,
    required String callState,
  }) async {
    return _methodChannel.invokeMethod("setCallState", {
      'session_id': sessionId,
      'call_state': callState,
    });
  }

  /// Retrieves call information about the call
  static Future<Map<String, dynamic>?> getCallData({
    required String sessionId,
  }) async {
    return _methodChannel.invokeMethod("getCallData", {
      'session_id': sessionId,
    }).then((data) {
      if (data == null) {
        return Future.value(null);
      }
      return Future.value(Map<String, dynamic>.from(data));
    });
  }

  /// Cleans all data related to the call
  static Future<void> clearCallData() async {
    return _methodChannel.invokeMethod("clearCallData");
  }

  /// Returns the id of the last displayed call.
  /// It is useful on starting app step for navigation to the call screen if the call was accepted
  static Future<String?> getLastSessionId() async {
    return _methodChannel.invokeMethod("getLastSessionId");
  }

  /// Remove the last displayed call to avoid that this is is returned on the next app start
  static Future<void> removeLastSessionId() async {
    return _methodChannel.invokeMethod("removeLastSessionId");
  }

  static Future<void> setOnLockScreenVisibility({
    required bool? isVisible,
  }) async {
    if (!Platform.isAndroid) return;

    return _methodChannel.invokeMethod("setOnLockScreenVisibility", {
      'is_visible': isVisible,
    });
  }

  static void _processEvent(Map<String, dynamic> eventData) {
    log('[ConnectycubeFlutterCallKit][_processEvent] eventData: $eventData');

    var event = eventData["event"] as String;
    var arguments = Map<String, dynamic>.from(eventData['args']);

    switch (event) {
      case 'voipToken':
        onTokenRefreshed?.call(arguments['voipToken']);
        break;

      case 'answerCall':
        var callEvent = CallEvent.fromMap(arguments);
        _onCallAccepted?.call(callEvent);

        break;

      case 'endCall':
        var callEvent = CallEvent.fromMap(arguments);

        _onCallRejected?.call(callEvent);

        break;

      case 'startCall':
        break;

      case 'setMuted':
        onCallMuted?.call(true, arguments["session_id"]);
        break;

      case 'setUnMuted':
        onCallMuted?.call(false, arguments["session_id"]);
        break;

      case '':
        break;

      default:
        throw Exception("Unrecognized event");
    }
  }
}

class CallState {
  static const String PENDING = "pending";
  static const String ACCEPTED = "accepted";
  static const String REJECTED = "rejected";
  static const String UNKNOWN = "unknown";
}

class BackgroundCallbackName {
  static const String REJECTED_IN_BACKGROUND = "rejected_in_background";
  static const String ACCEPTED_IN_BACKGROUND = "accepted_in_background";
}
