import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

typedef Future<dynamic> CallEventHandler(String sessionId);

class ConnectycubeFlutterCallKit {
  static const MethodChannel _channel =
      const MethodChannel('connectycube_flutter_call_kit');

  CallEventHandler _onCallAccepted;
  CallEventHandler _onCallRejected;

  /// Sets up [MessageHandler] for incoming messages.
  void init({
    CallEventHandler onCallAccepted,
    CallEventHandler onCallRejected,
  }) {
    _onCallAccepted = onCallAccepted;
    _onCallRejected = onCallRejected;
    _channel.setMethodCallHandler(_handleMethod);
  }

  static Future<void> showCallNotification({
    @required String sessionId,
    @required int callType,
    @required int callerId,
    @required String callerName,
  }) async {
    if (!Platform.isAndroid) return;

    return _channel.invokeMethod("showCallNotification", {
      'session_id': sessionId,
      'call_type': callType,
      'caller_id': callerId,
      'caller_name': callerName,
    });
  }

  static Future<void> reportCallAccepted({
    @required String sessionId,
  }) async {
    if (!Platform.isAndroid) return;

    return _channel.invokeMethod("reportCallAccepted", {
      'session_id': sessionId,
    });
  }

  static Future<void> reportCallEnded({
    @required String sessionId,
  }) async {
    if (!Platform.isAndroid) return;

    return _channel.invokeMethod("reportCallEnded", {
      'session_id': sessionId,
    });
  }

  Future<void> _handleMethod(MethodCall call) {
    final Map map = call.arguments.cast<String, dynamic>();
    switch (call.method) {
      case "onCallAccepted":
        return _onCallAccepted(map["session_id"]);
      case "onCallRejected":
        return _onCallRejected(map["session_id"]);
      default:
        throw UnsupportedError("Unrecognized JSON message");
    }
  }
}
