import Flutter
import UIKit

class CallStreamHandler: NSObject, FlutterStreamHandler {

    public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        print("CallStreamHandler: on listen");
        SwiftConnectycubeFlutterCallKitPlugin.callController.actionListener = { event, uuid, args in
            print("Action listener: \(event)")
            var data = ["event" : event.rawValue, "uuid": uuid.uuidString] as [String: Any]
            if args != nil{
                data["args"] = args!
            }
            events(data)
        }
        
        SwiftConnectycubeFlutterCallKitPlugin.voipController.tokenListener = { token in
            print("Action listener: \(token)")
            let data = ["event" : "voipToken", "voipToken": token]

            events(data)
        }
        
        return nil
    }

    public func onCancel(withArguments arguments: Any?) -> FlutterError? {
        print("CallStreamHanlder: on cancel")
        SwiftConnectycubeFlutterCallKitPlugin.callController.actionListener = nil
        return nil
    }

}

public class SwiftConnectycubeFlutterCallKitPlugin: NSObject, FlutterPlugin {
    static let _methodChannelName = "connectycube_flutter_call_kit";
    static let _callEventChannelName = "connectycube_flutter_call_kit.callEventChannel"
    static let callController = CallKitController()
    static let voipController = VoIPController(withCallKitController: callController)


    //methods
    static let _methodChannelGetVoipToken = "connectycube_flutter_call_kit.getVoipToken"
    static let _methodChannelStartCall = "connectycube_flutter_call_kit.startCall"
    static let _methodChannelReportIncomingCall = "connectycube_flutter_call_kit.reportIncomingCall"
    static let _methodChannelReportOutgoingCall = "connectycube_flutter_call_kit.reportOutgoingCall"
    static let _methodChannelReportCallEnded =
        "connectycube_flutter_call_kit.reportCallEnded";
    static let _methodChannelEndCall = "connectycube_flutter_call_kit.endCall";
    static let _methodChannelHoldCall = "connectycube_flutter_call_kit.holdCall";
    static let _methodChannelCheckPermissions = "connectycube_flutter_call_kit.checkPermissions";
    static let _methodChannelMuteCall = "connectycube_flutter_call_kit.muteCall";

    public static func register(with registrar: FlutterPluginRegistrar) {

        //setup method channels
        let methodChannel = FlutterMethodChannel(name: _methodChannelName, binaryMessenger: registrar.messenger())

        //setup event channels
        let callEventChannel = FlutterEventChannel(name: _callEventChannelName, binaryMessenger: registrar.messenger())
        callEventChannel.setStreamHandler(CallStreamHandler())

        let instance = SwiftConnectycubeFlutterCallKitPlugin()
        registrar.addMethodCallDelegate(instance, channel: methodChannel)


        
    }

    ///useful for integrating with VIOP notifications
    static public func reportIncomingCall(handle: String, uuid: String, result: FlutterResult?){
        SwiftConnectycubeFlutterCallKitPlugin.callController.reportIncomingCall(uuid: UUID(uuidString: uuid)!, handle: handle) { (error) in
            print("ERROR: \(error?.localizedDescription ?? "none")")
            result?(error == nil)
        }
    }

    //TODO: remove these defaults and get as arguments
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        print("Method call \(call.method)");
        let args = call.arguments as? Dictionary<String, Any>
        if(call.method == SwiftConnectycubeFlutterCallKitPlugin._methodChannelGetVoipToken){
            let voipToken = SwiftConnectycubeFlutterCallKitPlugin.voipController.getVoIPToken()
            result(voipToken)
        }else if(call.method == SwiftConnectycubeFlutterCallKitPlugin._methodChannelStartCall){
            if let handle = args?["handle"] as? String{
                let uuidString = args?["uuid"] as? String;
                SwiftConnectycubeFlutterCallKitPlugin.callController.startCall(handle: handle, videoEnabled: false, uuid: uuidString)
                result(true)
            }else{
                result(FlutterError.init(code: "bad args", message: nil, details: nil))
            }
        }else if call.method == SwiftConnectycubeFlutterCallKitPlugin._methodChannelReportIncomingCall{
            if let handle = args?["handle"] as? String, let uuid = args?["uuid"] as? String{
                SwiftConnectycubeFlutterCallKitPlugin.reportIncomingCall(handle: handle, uuid: uuid, result: result)
            }else{
                result(FlutterError.init(code: "bad args", message: nil, details: nil))
            }
        }else if call.method == SwiftConnectycubeFlutterCallKitPlugin._methodChannelReportOutgoingCall{
            if let finishedConnecting = args?["finishedConnecting"] as? Bool, let uuid = args?["uuid"] as? String{
                SwiftConnectycubeFlutterCallKitPlugin.callController.reportOutgoingCall(uuid: UUID(uuidString: uuid)!, finishedConnecting: finishedConnecting);
                result(true);
            }else{
                result(FlutterError.init(code: "bad args", message: nil, details: nil))
            }
        }
        else if call.method == SwiftConnectycubeFlutterCallKitPlugin._methodChannelReportCallEnded{
            if let reason = args?["reason"] as? String, let uuid = args?["uuid"] as? String{
                SwiftConnectycubeFlutterCallKitPlugin.callController.reportCallEnded(uuid: UUID(uuidString: uuid)!, reason: CallEndedReason.init(rawValue: reason)!);
                result(true);
            }else{
                result(FlutterError.init(code: "bad args", message: nil, details: nil))
            }
        }else if call.method == SwiftConnectycubeFlutterCallKitPlugin._methodChannelEndCall{
            if let uuid = args?["uuid"] as? String{
                SwiftConnectycubeFlutterCallKitPlugin.callController.end(uuid: UUID(uuidString: uuid)!)
                result(true)
            }else{
                result(FlutterError.init(code: "bad args", message: nil, details: nil))
            }
        }else if call.method == SwiftConnectycubeFlutterCallKitPlugin._methodChannelHoldCall{
            if let uuid = args?["uuid"] as? String, let hold = args?["hold"] as? Bool{
                SwiftConnectycubeFlutterCallKitPlugin.callController.setHeld(uuid: UUID(uuidString: uuid)!, onHold: hold)
                result(true)
            }else{
                result(FlutterError.init(code: "bad args", message: nil, details: nil))
            }
        }else if call.method == SwiftConnectycubeFlutterCallKitPlugin._methodChannelCheckPermissions{
            result(true) //no permissions needed on ios
        }else if call.method == SwiftConnectycubeFlutterCallKitPlugin._methodChannelMuteCall{
            if let uuid = args?["uuid"] as? String, let muted = args?["muted"] as? Bool{
                SwiftConnectycubeFlutterCallKitPlugin.callController.setMute(uuid: UUID(uuidString: uuid)!, muted: muted)
                result(true)
            }else{
                result(FlutterError.init(code: "bad args", message: nil, details: nil))
            }
        }
    }
}
