import Flutter
import UIKit

class CallStreamHandler: NSObject, FlutterStreamHandler {
    
    public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        // print("[CallStreamHandler][onListen]");
        SwiftConnectycubeFlutterCallKitPlugin.callController.actionListener = { event, uuid, args in
            // print("[CallStreamHandler][onListen] actionListener: \(event)")
            var data = ["event" : event.rawValue, "uuid": uuid.uuidString.lowercased()] as [String: Any]
            if args != nil{
                data["args"] = args!
            }
            events(data)
        }
        
        SwiftConnectycubeFlutterCallKitPlugin.voipController.tokenListener = { token in
            // print("[CallStreamHandler][onListen] tokenListener: \(token)")
            let data: [String: Any] = ["event" : "voipToken", "args": ["voipToken" : token]]
            
            events(data)
        }
        
        return nil
    }
    
    public func onCancel(withArguments arguments: Any?) -> FlutterError? {
        // print("[CallStreamHandler][onCancel]")
        SwiftConnectycubeFlutterCallKitPlugin.callController.actionListener = nil
        SwiftConnectycubeFlutterCallKitPlugin.voipController.tokenListener = nil
        return nil
    }
}

public class SwiftConnectycubeFlutterCallKitPlugin: NSObject, FlutterPlugin {
    static let _methodChannelName = "connectycube_flutter_call_kit.methodChannel";
    static let _callEventChannelName = "connectycube_flutter_call_kit.callEventChannel"
    static let callController = CallKitController()
    static let voipController = VoIPController(withCallKitController: callController)
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        // print("[SwiftConnectycubeFlutterCallKitPlugin][register]")
        //setup method channels
        let methodChannel = FlutterMethodChannel(name: _methodChannelName, binaryMessenger: registrar.messenger())
        
        //setup event channels
        let callEventChannel = FlutterEventChannel(name: _callEventChannelName, binaryMessenger: registrar.messenger())
        callEventChannel.setStreamHandler(CallStreamHandler())
        
        let instance = SwiftConnectycubeFlutterCallKitPlugin()
        registrar.addMethodCallDelegate(instance, channel: methodChannel)
    }
    
    ///useful for integrating with VIOP notifications
    static public func reportIncomingCall(uuid: String,
                                          callType: Int,
                                          callInitiatorId: String,
                                          callInitiatorName: String,
                                          callData: [String: Any], result: FlutterResult?){
        SwiftConnectycubeFlutterCallKitPlugin.callController.reportIncomingCall(uuid: uuid.lowercased(), callType: callType, callInitiatorId: callInitiatorId, callInitiatorName: callInitiatorName, callData: callData) { (error) in
            // print("[SwiftConnectycubeFlutterCallKitPlugin] reportIncomingCall ERROR: \(error?.localizedDescription ?? "none")")
            result?(error == nil)
        }
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        // print("[SwiftConnectycubeFlutterCallKitPlugin][handle] method: \(call.method)");
        let arguments = call.arguments as? Dictionary<String, Any>
        if call.method == "getVoipToken" {
            let voipToken = SwiftConnectycubeFlutterCallKitPlugin.voipController.getVoIPToken()
            result(voipToken)
        }
        else if call.method == "updateConfig" {
            guard let arguments = arguments else {
                result(FlutterError(code: "invalid_argument", message: "No data was provided.", details: nil))
                return
            }
            let ringtone = arguments["ringtone"] as? String
            let icon = arguments["icon"] as? String
            CallKitController.updateConfig(ringtone: ringtone, icon: icon)
            
            result(true)
        }
        else if call.method == "showCallNotification" {
            guard let arguments = arguments else {
                result(FlutterError(code: "invalid_argument", message: "No data was provided.", details: nil))
                return
            }
            let callId = arguments["session_id"] as! String
            let callType = arguments["call_type"] as! Int
            let callInitiatorId = arguments["caller_id"] as! String
            let callInitiatorName = arguments["caller_name"] as! String
            let callData = arguments["call_data"] as! [String: Any]
            
            SwiftConnectycubeFlutterCallKitPlugin.callController.reportIncomingCall(uuid: callId.lowercased(), callType: callType, callInitiatorId: callInitiatorId, callInitiatorName: callInitiatorName, callData: callData) { (error) in
                // print("[SwiftConnectycubeFlutterCallKitPlugin][handle] reportIncomingCall ERROR: \(error?.localizedDescription ?? "none")")
                result(error == nil)
            }
        }
        else if call.method == "startCall" {
            guard let arguments = arguments else {
                result(FlutterError(code: "invalid_argument", message: "No data was provided.", details: nil))
                return
            }
            let callId = arguments["session_id"] as! String
            let callType = arguments["call_type"] as! Int
            let callInitiatorId = arguments["caller_id"] as! String
            let callInitiatorName = arguments["caller_name"] as! String
            let callData = arguments["call_data"] as! [String: Any]

            SwiftConnectycubeFlutterCallKitPlugin.callController.newOutgoingCall(uuid: callId.lowercased(), callType: callType, callInitiatorId: callInitiatorId, callInitiatorName: callInitiatorName, callData: callData) { (error) in 
                result(error == nil)
            }
        }
        else if call.method == "reportCallAccepted" {
            guard let arguments = arguments, let callId = arguments["session_id"] as? String else {
                result(FlutterError(code: "invalid_argument", message: "session_id was not provided.", details: nil))
                return
            }
            
            SwiftConnectycubeFlutterCallKitPlugin.callController.answerCall(uuid: callId)
            result(true)
        }
        else if call.method == "reportCallFinished" {
            guard let arguments = arguments else {
                result(FlutterError(code: "invalid_argument", message: "No data was provided.", details: nil))
                return
            }
            let callId = arguments["session_id"] as! String
            let reason = arguments["reason"] as! String
            
            
            SwiftConnectycubeFlutterCallKitPlugin.callController.reportCallEnded(uuid: UUID(uuidString: callId)!, reason: CallEndedReason.init(rawValue: reason)!);
            result(true);
        }
        else if call.method == "reportCallEnded" {
            guard let arguments = arguments else {
                result(FlutterError(code: "invalid_argument", message: "No data was provided.", details: nil))
                return
            }
            let callId = arguments["session_id"] as! String
            SwiftConnectycubeFlutterCallKitPlugin.callController.end(uuid: UUID(uuidString: callId)!)
            result(true)
        }
        else if call.method == "muteCall" {
            guard let arguments = arguments else {
                result(FlutterError(code: "invalid_argument", message: "No data was provided.", details: nil))
                return
            }
            let callId = arguments["session_id"] as! String
            let muted = arguments["muted"] as! Bool
            
            SwiftConnectycubeFlutterCallKitPlugin.callController.setMute(uuid: UUID(uuidString: callId)!, muted: muted)
            result(true)
        }
        else if call.method == "getCallState" {
            guard let arguments = arguments, let callId = arguments["session_id"] as? String else {
                result(FlutterError(code: "invalid_argument", message: "session_id was not provided.", details: nil))
                return
            }
            
            result(SwiftConnectycubeFlutterCallKitPlugin.callController.getCallState(uuid: callId).rawValue)
        }
        else if call.method == "setCallState" {
            guard let arguments = arguments else {
                result(FlutterError(code: "invalid_argument", message: "No data was provided.", details: nil))
                return
            }
            let callId = arguments["session_id"] as! String
            let callState = arguments["call_state"] as! String
            
            SwiftConnectycubeFlutterCallKitPlugin.callController.setCallState(uuid: callId, callState: callState)
            result(true)
        }
        
        else if call.method == "getCallData" {
            guard let arguments = arguments, let callId = arguments["session_id"] as? String else {
                result(FlutterError(code: "invalid_argument", message: "session_id was not provided.", details: nil))
                return
            }
            
            result(SwiftConnectycubeFlutterCallKitPlugin.callController.getCallData(uuid: callId))
        }
        else if call.method == "clearCallData" {
            guard let arguments = arguments, let callId = arguments["session_id"] as? String else {
                result(FlutterError(code: "invalid_argument", message: "session_id was not provided.", details: nil))
                return
            }
            
            SwiftConnectycubeFlutterCallKitPlugin.callController.clearCallData(uuid: callId)
            result(true)
        }
        else if call.method == "getLastCallId" {
            result(SwiftConnectycubeFlutterCallKitPlugin.callController.currentCallData["session_id"])
        }
        else {
            result(FlutterMethodNotImplemented)
        }
    }
}
