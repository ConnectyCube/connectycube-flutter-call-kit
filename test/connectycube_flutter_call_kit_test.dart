import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:connectycube_flutter_call_kit/connectycube_flutter_call_kit.dart';

void main() {
  const MethodChannel channel = MethodChannel('connectycube_flutter_call_kit');

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });


}
