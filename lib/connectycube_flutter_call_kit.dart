import 'dart:async';
import 'dart:convert';
import 'dart:developer';
import 'package:universal_io/io.dart';

import 'package:flutter/services.dart';

typedef Future<dynamic> CallEventHandler(
  String sessionId,
  int callType,
  int callerId,
  String callerName,
  Set<int> opponentsIds,
  Map<String, String>? userInfo,
);

class ConnectycubeFlutterCallKit {
  static const MethodChannel _methodChannel =
      const MethodChannel('connectycube_flutter_call_kit');
  static const EventChannel _eventChannel =
      const EventChannel('connectycube_flutter_call_kit.event_channel');

  static ConnectycubeFlutterCallKit get instance => _getInstance();
  static ConnectycubeFlutterCallKit? _instance;
  static String TAG = "ConnectycubeFlutterCallKit";

  static ConnectycubeFlutterCallKit _getInstance() {
    if (_instance == null) {
      _instance = ConnectycubeFlutterCallKit._internal();
    }
    return _instance!;
  }

  factory ConnectycubeFlutterCallKit() => _getInstance();

  ConnectycubeFlutterCallKit._internal();

  static Function(String voipToken)? onVoipTokenReceived;

  static Function(
    String sessionId,
    int callType,
    int callerId,
    String callerName,
    Set<int> opponentsIds,
    Map<String, String>? userInfo,
  )? onCallRejectedWhenTerminated;

  static Function(
    String sessionId,
    int callType,
    int callerId,
    String callerName,
    Set<int> opponentsIds,
    Map<String, String>? userInfo,
  )? onCallAcceptedWhenTerminated;

  static CallEventHandler? _onCallAccepted;
  static CallEventHandler? _onCallRejected;

  void init({
    CallEventHandler? onCallAccepted,
    CallEventHandler? onCallRejected,
  }) {
    _onCallAccepted = onCallAccepted;
    _onCallRejected = onCallRejected;
    initMessagesHandler();
    initEventsHandler();
  }

  static void initMessagesHandler() {
    _methodChannel.setMethodCallHandler(_handleMethod);
  }

  static void initEventsHandler() {
    _eventChannel.receiveBroadcastStream().listen((rawData) {
      final eventData = Map<String, dynamic>.from(rawData);

      _processEvent(eventData);
    });
  }

  /// Returns VoIP token for iOS plaform.
  /// Returns null for the Amndroid platform
  static Future<String?> getVoipToken() {
    return _methodChannel.invokeMethod('getVoipToken').then((result) {
      return result?.toString();
    });
  }

  static Future<void> showCallNotification({
    required String? sessionId,
    required int? callType,
    required int? callerId,
    required String? callerName,
    required Set<int>? opponentsIds,
    Map<String, String>? userInfo,
  }) async {
    if (!Platform.isAndroid) return;

    return _methodChannel.invokeMethod("showCallNotification", {
      'session_id': sessionId,
      'call_type': callType,
      'caller_id': callerId,
      'caller_name': callerName,
      'call_opponents': opponentsIds!.join(','),
      'user_info': userInfo != null ? jsonEncode(userInfo) : jsonEncode(Map()),
    });
  }

  static Future<void> reportCallAccepted({
    required String? sessionId,
  }) async {
    if (!Platform.isAndroid) return;

    return _methodChannel.invokeMethod("reportCallAccepted", {
      'session_id': sessionId,
    });
  }

  static Future<void> reportCallEnded({
    required String? sessionId,
  }) async {
    if (!Platform.isAndroid) return;

    return _methodChannel.invokeMethod("reportCallEnded", {
      'session_id': sessionId,
    });
  }

  static Future<String> getCallState({
    required String? sessionId,
  }) async {
    if (!Platform.isAndroid) return Future.value(CallState.UNKNOWN);

    return _methodChannel.invokeMethod("getCallState", {
      'session_id': sessionId,
    }).then((state) {
      return state.toString();
    });
  }

  static Future<void> setCallState({
    required String? sessionId,
    required String? callState,
  }) async {
    return _methodChannel.invokeMethod("setCallState", {
      'session_id': sessionId,
      'call_state': callState,
    });
  }

  static Future<Map<String, dynamic>?> getCallData({
    required String? sessionId,
  }) async {
    if (!Platform.isAndroid) return Future.value();

    return _methodChannel.invokeMethod("getCallData", {
      'session_id': sessionId,
    }).then((data) {
      if (data == null) {
        return Future.value(null);
      }
      return Future.value(Map<String, dynamic>.from(data));
    });
  }

  static Future<void> clearCallData({
    required String? sessionId,
  }) async {
    if (!Platform.isAndroid) return Future.value();

    return _methodChannel.invokeMethod("clearCallData", {
      'session_id': sessionId,
    });
  }

  static Future<String?> getLastCallId() async {
    if (!Platform.isAndroid) return Future.value();

    return _methodChannel.invokeMethod("getLastCallId");
  }

  static Future<void> setOnLockScreenVisibility({
    required bool? isVisible,
  }) async {
    if (!Platform.isAndroid) return;

    return _methodChannel.invokeMethod("setOnLockScreenVisibility", {
      'is_visible': isVisible,
    });
  }

  static Future<dynamic> _handleMethod(MethodCall call) {
    final Map map = call.arguments.cast<String, dynamic>();
    switch (call.method) {
      case "onCallAccepted":
        var userInfo = map['user_info'];
        var userInfoParsed;
        if (userInfo != null) {
          userInfoParsed = Map<String, String>.from(jsonDecode(userInfo));
        }
        if (onCallAcceptedWhenTerminated != null) {
          onCallAcceptedWhenTerminated!.call(
            map["session_id"],
            map["call_type"],
            map["caller_id"],
            map["caller_name"],
            (map["call_opponents"] as String)
                .split(',')
                .map((stringUserId) => int.parse(stringUserId))
                .toSet(),
            userInfoParsed,
          );
        }

        if (_onCallAccepted != null) {
          return _onCallAccepted!(
            map["session_id"],
            map["call_type"],
            map["caller_id"],
            map["caller_name"],
            (map["call_opponents"] as String)
                .split(',')
                .map((stringUserId) => int.parse(stringUserId))
                .toSet(),
            userInfoParsed,
          );
        }

        break;
      case "onCallRejected":
        var userInfo = map['user_info'];
        var userInfoParsed;
        if (userInfo != null) {
          userInfoParsed = Map<String, String>.from(jsonDecode(userInfo));
        }

        if (onCallRejectedWhenTerminated != null) {
          onCallRejectedWhenTerminated!.call(
            map["session_id"],
            map["call_type"],
            map["caller_id"],
            map["caller_name"],
            (map["call_opponents"] as String)
                .split(',')
                .map((stringUserId) => int.parse(stringUserId))
                .toSet(),
            userInfoParsed,
          );
        }

        if (_onCallRejected != null) {
          return _onCallRejected!(
            map["session_id"],
            map["call_type"],
            map["caller_id"],
            map["caller_name"],
            (map["call_opponents"] as String)
                .split(',')
                .map((stringUserId) => int.parse(stringUserId))
                .toSet(),
            userInfoParsed,
          );
        }

        break;
      default:
        throw UnsupportedError("Unrecognized JSON message");
    }

    return Future.value();
  }

  static void _processEvent(Map<String, dynamic> eventData) {
    log('[ConnectycubeFlutterCallKit][_processEvent] eventData: $eventData');

    final event = eventData["event"] as String?;

    switch (event) {
      case 'voipToken':
        onVoipTokenReceived?.call(eventData['voipToken']);
        break;

      case '':
        break;

      case '':
        break;

      case '':
        break;

      case '':
        break;

      case '':
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
