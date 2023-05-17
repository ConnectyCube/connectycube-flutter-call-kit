import 'dart:convert';

import 'package:flutter/foundation.dart';

/// {@template call_event}
/// Information about the call events (e.g. CallAccepted / CallRejected)
/// {@endtemplate}
@immutable
class CallEvent {
  /// {@macro call_event}
  const CallEvent({
    required this.sessionId,
    required this.callType,
    required this.callerId,
    required this.callerName,
    required this.callData,
  });

  final String sessionId;
  final int callType;
  final String callerId;
  final String callerName;

  /// Used for exchanging additional data between the Call notification and your app,
  /// you will get this data in event callbacks (e.g. onCallAcceptedWhenTerminated,
  /// onCallAccepted, onCallRejectedWhenTerminated, or onCallRejected)
  /// after setting it in method showCallNotification
  final Map<String, dynamic>? callData;

  CallEvent copyWith({
    String? sessionId,
    int? callType,
    String? callerId,
    String? callerName,
    Map<String, String>? callData,
  }) {
    return CallEvent(
      sessionId: sessionId ?? this.sessionId,
      callType: callType ?? this.callType,
      callerId: callerId ?? this.callerId,
      callerName: callerName ?? this.callerName,
      callData: callData ?? this.callData,
    );
  }

  Map<String, Object?> toMap() {
    return {
      'session_id': sessionId,
      'call_type': callType,
      'caller_id': callerId,
      'caller_name': callerName,
      'call_data': callData ?? <String, dynamic>{},
    };
  }

  factory CallEvent.fromMap(Map<String, dynamic> map) {
    // print('[CallEvent.fromMap] map: $map');
    return CallEvent(
      sessionId: map['session_id'] as String,
      callType: map['call_type'] as int,
      callerId: map['caller_id'] as String,
      callerName: map['caller_name'] as String,
      callData: map['call_data'] != null ? Map<String, dynamic>.from(map['call_data']) : null,
    );

    // userInfo: map['user_info'] == null || map['user_info'].isEmpty
    //     ? null
    //     : Map<String, String>.from(jsonDecode(map['user_info'])),
  }

  String toJson() => json.encode(toMap());

  factory CallEvent.fromJson(String source) => CallEvent.fromMap(json.decode(source) as Map<String, dynamic>);

  @override
  String toString() {
    return 'CallEvent('
        'sessionId: $sessionId, '
        'callType: $callType, '
        'callerId: $callerId, '
        'callerName: $callerName, '
        'callData: $callData)';
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;

    return other is CallEvent &&
        other.sessionId == sessionId &&
        other.callType == callType &&
        other.callerId == callerId &&
        other.callerName == callerName &&
        mapEquals(other.callData, callData);
  }

  @override
  int get hashCode {
    return sessionId.hashCode ^ callType.hashCode ^ callerId.hashCode ^ callerName.hashCode ^ callData.hashCode;
  }
}
