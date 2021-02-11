import Flutter
import UIKit

public class SwiftConnectycubeFlutterCallKitPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "connectycube_flutter_call_kit", binaryMessenger: registrar.messenger())
    let instance = SwiftConnectycubeFlutterCallKitPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    result("iOS " + UIDevice.current.systemVersion)
  }
}
