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
    required this.opponentsIds,
    this.callPhoto,
    this.userInfo,
  });

  final String sessionId;
  final int callType;
  final int callerId;
  final String callerName;
  final Set<int> opponentsIds;
  final String? callPhoto;

  /// Used for exchanging additional data between the Call notification and your app,
  /// you will get this data in event callbacks (e.g. onCallAcceptedWhenTerminated,
  /// onCallAccepted, onCallRejectedWhenTerminated, or onCallRejected)
  /// after setting it in method showCallNotification
  final Map<String, dynamic>? userInfo;

  CallEvent copyWith({
    String? sessionId,
    int? callType,
    int? callerId,
    String? callerName,
    Set<int>? opponentsIds,
    String? callPhoto,
    Map<String, String>? userInfo,
  }) {
    return CallEvent(
      sessionId: sessionId ?? this.sessionId,
      callType: callType ?? this.callType,
      callerId: callerId ?? this.callerId,
      callerName: callerName ?? this.callerName,
      opponentsIds: opponentsIds ?? this.opponentsIds,
      callPhoto: callPhoto ?? this.callPhoto,
      userInfo: userInfo ?? this.userInfo,
    );
  }

  Map<String, dynamic > toMap() {
    return {
      'session_id': sessionId,
      'call_type': callType,
      'caller_id': callerId,
      'caller_name': callerName,
      'call_opponents': opponentsIds.join(','),
      'photo_url': callPhoto,
      'user_info': jsonEncode(userInfo ?? <String, dynamic>{}),
    };
  }

  factory CallEvent.fromMap(Map<String, dynamic> map) {
    print('[CallEvent.fromMap] map: $map');
    return CallEvent(
      sessionId: map['session_id'] as String,
      callType: map['call_type'] as int,
      callerId: map['caller_id'] as int,
      callerName: map['caller_name'] as String,
      opponentsIds:
          (map['call_opponents'] as String).split(',').map(int.parse).toSet(),
      callPhoto: map['photo_url'],
      userInfo: map['user_info'] != null
          ? Map<String, dynamic>.from(jsonDecode(map['user_info']))
          : null,
    );
  }

  String toJson() => json.encode(toMap());

  factory CallEvent.fromJson(String source) =>
      CallEvent.fromMap(json.decode(source) as Map<String, dynamic>);

  @override
  String toString() {
    return 'CallEvent('
        'sessionId: $sessionId, '
        'callType: $callType, '
        'callerId: $callerId, '
        'callerName: $callerName, '
        'opponentsIds: $opponentsIds, '
        'callPhoto: $callPhoto, '
        'userInfo: $userInfo)';
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;

    return other is CallEvent &&
        other.sessionId == sessionId &&
        other.callType == callType &&
        other.callerId == callerId &&
        other.callerName == callerName &&
        setEquals(other.opponentsIds, opponentsIds) &&
        other.callPhoto == callPhoto &&
        mapEquals(other.userInfo, userInfo);
  }

  @override
  int get hashCode {
    return sessionId.hashCode ^
        callType.hashCode ^
        callerId.hashCode ^
        callerName.hashCode ^
        opponentsIds.hashCode ^
        userInfo.hashCode;
  }
}
