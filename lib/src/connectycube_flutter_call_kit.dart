import 'dart:async';
import 'dart:io';

import 'package:connectycube_flutter_call_kit/src/call_event.dart';
import 'package:flutter/services.dart';

/// Function type for handling accepted and rejected call events
typedef CallEventHandler = Future<dynamic> Function(CallEvent event);

/// {@template connectycube_flutter_call_kit}
/// Plugin to manage android call events and notifications
/// {@endtemplate}
class ConnectycubeFlutterCallKit {
  /// {@macro connectycube_flutter_call_kit}
  factory ConnectycubeFlutterCallKit() => _getInstance();
  const ConnectycubeFlutterCallKit._internal();

  static ConnectycubeFlutterCallKit? _instance;
  static ConnectycubeFlutterCallKit get instance => _getInstance();
  static ConnectycubeFlutterCallKit _getInstance() {
    _instance ??= const ConnectycubeFlutterCallKit._internal();
    return _instance!;
  }

  static const MethodChannel _channel =
      MethodChannel('connectycube_flutter_call_kit');
  static const String tag = 'ConnectycubeFlutterCallKit';

  /// Callback to handle rejected calls in the background
  static void Function(CallEvent event)? onCallRejectedWhenTerminated;

  /// Callback to handle accepted calls in the background or terminated state
  static void Function(CallEvent event)? onCallAcceptedWhenTerminated;

  static CallEventHandler? _onCallAccepted;
  static CallEventHandler? _onCallRejected;

  /// Initialize the plugin and provided user callbacks.
  ///
  /// - This function should only be called once at the beginning of
  /// your application.
  void init({
    CallEventHandler? onCallAccepted,
    CallEventHandler? onCallRejected,
  }) {
    _onCallAccepted = onCallAccepted;
    _onCallRejected = onCallRejected;
    _initMessagesHandler();
  }

  static void _initMessagesHandler() {
    _channel.setMethodCallHandler(_handleMethod);
  }

  /// Show incoming call notification
  static Future<void> showCallNotification(CallEvent event) async {
    if (!Platform.isAndroid) return;

    return _channel.invokeMethod('showCallNotification', event.toMap());
  }

  /// Report that the current active call has been accepted by your application
  static Future<void> reportCallAccepted({
    required String sessionId,
  }) async {
    if (!Platform.isAndroid) return;

    return _channel.invokeMethod('reportCallAccepted', {
      'session_id': sessionId,
    });
  }

  /// Report that the current active call has been ended by your application
  static Future<void> reportCallEnded({
    required String sessionId,
  }) async {
    if (!Platform.isAndroid) return;

    return _channel.invokeMethod('reportCallEnded', {
      'session_id': sessionId,
    });
  }

  /// Get the current call state
  ///
  /// Other platforms than Android will receive [CallState.unknown]
  static Future<String> getCallState({required String sessionId}) async {
    if (!Platform.isAndroid) return Future.value(CallState.unknown);

    final state = await _channel.invokeMethod<String>('getCallState', {
      'session_id': sessionId,
    });
    return state.toString();
  }

  /// Updated the current call state
  static Future<void> setCallState({
    required String sessionId,
    required String callState,
  }) async {
    return _channel.invokeMethod<void>('setCallState', {
      'session_id': sessionId,
      'call_state': callState,
    });
  }

  /// Retrieves call information about the call
  static Future<CallEvent?> getCallData({
    required String sessionId,
  }) async {
    if (!Platform.isAndroid) return Future.value();

    final eventData =
        await _channel.invokeMethod<Map<String, dynamic>>('getCallData', {
      'session_id': sessionId,
    });
    return eventData != null ? CallEvent.fromMap(eventData) : null;
  }

  /// Cleans all data related to the call
  static Future<void> clearCallData({
    required String sessionId,
  }) async {
    if (!Platform.isAndroid) return Future.value();

    return _channel.invokeMethod<void>('clearCallData', {
      'session_id': sessionId,
    });
  }

  /// Returns the id of the last displayed call.
  /// It is useful on starting app step for navigation to the call screen if the call was accepted
  static Future<String?> getLastCallId() async {
    if (!Platform.isAndroid) return Future.value();

    return _channel.invokeMethod<String?>('getLastCallId');
  }

  static Future<void> setOnLockScreenVisibility({
    required bool isVisible,
  }) async {
    if (!Platform.isAndroid) return;

    return _channel.invokeMethod<void>('setOnLockScreenVisibility', {
      'is_visible': isVisible,
    });
  }

  static Future<dynamic> _handleMethod(MethodCall call) {
    final map = Map<String, dynamic>.from(call.arguments as Map);
    final event = CallEvent.fromMap(map);

    switch (call.method) {
      case 'onCallAccepted':
        onCallAcceptedWhenTerminated?.call(event);
        _onCallAccepted?.call(event);
        break;

      case 'onCallRejected':
        onCallRejectedWhenTerminated?.call(event);
        _onCallRejected?.call(event);
        break;
      default:
        throw UnsupportedError('Unrecognized JSON message');
    }

    return Future<void>.value();
  }
}

class CallState {
  static const String pending = 'pending';
  static const String accepted = 'accepted';
  static const String rejected = 'rejected';
  static const String unknown = 'unknown';
}
