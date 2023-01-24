import 'package:flutter/foundation.dart';

/// Information about the call events (e.g. CallAccepted / CallRejected)
@immutable
class CallEvent {
  const CallEvent({
    required this.sessionId,
    required this.callId,
    required this.callerName,
  });

  final String sessionId;
  final String callId;
  final String callerName;

  Map<String, Object?> toMap() {
    return {
      'session_id': sessionId,
      'call_id': callId,
      'caller_name': callerName,
    };
  }

  factory CallEvent.fromMap(Map<String, Object?> map) {
    return CallEvent(
      sessionId: map['session_id'] as String,
      callId: map['call_id'] as String,
      callerName: map['caller_name'] as String,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CallEvent &&
          runtimeType == other.runtimeType &&
          sessionId == other.sessionId &&
          callId == other.callId &&
          callerName == other.callerName;

  @override
  int get hashCode =>
      sessionId.hashCode ^ callId.hashCode ^ callerName.hashCode;

  @override
  String toString() {
    return 'CallEvent{sessionId: $sessionId, callId: $callId, callerName: $callerName}';
  }

  CallEvent copyWith({
    String? sessionId,
    String? callId,
    String? callerName,
  }) {
    return CallEvent(
      sessionId: sessionId ?? this.sessionId,
      callId: callId ?? this.callId,
      callerName: callerName ?? this.callerName,
    );
  }
}
