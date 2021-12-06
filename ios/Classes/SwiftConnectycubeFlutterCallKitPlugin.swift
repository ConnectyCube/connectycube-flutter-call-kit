import Flutter
import UIKit

class CallStreamHandler: NSObject, FlutterStreamHandler {
    
    public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        print("[CallStreamHandler][onListen]");
        SwiftConnectycubeFlutterCallKitPlugin.callController.actionListener = { event, uuid, args in
            print("[CallStreamHandler][onListen] actionListener: \(event)")
            var data = ["event" : event.rawValue, "uuid": uuid.uuidString] as [String: Any]
            if args != nil{
                data["args"] = args!
            }
            events(data)
        }
        
        SwiftConnectycubeFlutterCallKitPlugin.voipController.tokenListener = { token in
            print("[CallStreamHandler][onListen] tokenListener: \(token)")
            let data = ["event" : "voipToken", "voipToken": token]
            
            events(data)
        }
        
        return nil
    }
    
    public func onCancel(withArguments arguments: Any?) -> FlutterError? {
        print("[CallStreamHandler][onCancel]")
        SwiftConnectycubeFlutterCallKitPlugin.callController.actionListener = nil
        SwiftConnectycubeFlutterCallKitPlugin.voipController.tokenListener = nil
        return nil
    }
}

public class SwiftConnectycubeFlutterCallKitPlugin: NSObject, FlutterPlugin {
    static let _methodChannelName = "connectycube_flutter_call_kit";
    static let _callEventChannelName = "connectycube_flutter_call_kit.callEventChannel"
    static let callController = CallKitController()
    static let voipController = VoIPController(withCallKitController: callController)
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        print("[SwiftConnectycubeFlutterCallKitPlugin][register]")
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
                                          callInitiatorId: Int,
                                          callInitiatorName: String,
                                          opponents: [Int],
                                          userInfo: [String: String]?, result: FlutterResult?){
        SwiftConnectycubeFlutterCallKitPlugin.callController.reportIncomingCall(uuid: uuid, callType: callType, callInitiatorId: callInitiatorId, callInitiatorName: callInitiatorName, opponents: opponents, userInfo: userInfo) { (error) in
            print("[SwiftConnectycubeFlutterCallKitPlugin] reportIncomingCall ERROR: \(error?.localizedDescription ?? "none")")
            result?(error == nil)
        }
    }
    
    //TODO: remove these defaults and get as arguments
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        print("[SwiftConnectycubeFlutterCallKitPlugin][handle] method: \(call.method)");
        let arguments = call.arguments as! Dictionary<String, Any>
        if(call.method == "getVoipToken"){
            let voipToken = SwiftConnectycubeFlutterCallKitPlugin.voipController.getVoIPToken()
            result(voipToken)
        }
        else if(call.method == "showCallNotification"){
            let callId = arguments["session_id"] as! String
            let callType = arguments["call_type"] as! Int
            let callInitiatorId = arguments["caller_id"] as! Int
            let callInitiatorName = arguments["caller_name"] as! String
            let callOpponentsString = arguments["call_opponents"] as! String
            let callOpponents = callOpponentsString.components(separatedBy: ",")
                .map { Int($0) ?? 0 }
            let userInfoString = arguments["user_info"] as! String
            var userInfo: [String: String]?
            
            if let data = userInfoString.data(using: .utf8) {
                do {
                    userInfo = try JSONSerialization.jsonObject(with: data, options: .mutableContainers) as? [String:String]
                } catch {
                    print("[SwiftConnectycubeFlutterCallKitPlugin][handle] Can't parce JSON with userInfo")
                    userInfo = nil
                }
            }
            
            SwiftConnectycubeFlutterCallKitPlugin.callController.reportIncomingCall(uuid: callId, callType: callType, callInitiatorId: callInitiatorId, callInitiatorName: callInitiatorName, opponents: callOpponents, userInfo: userInfo) { (error) in
                print("[SwiftConnectycubeFlutterCallKitPlugin][handle] reportIncomingCall ERROR: \(error?.localizedDescription ?? "none")")
                result(error == nil)
            }
        }
        else if(call.method == "reportCallAccepted"){
            let callId = arguments["session_id"] as! String
            let callType = arguments["call_type"] as! Int
            let videoEnabled = callType == 1
            
            SwiftConnectycubeFlutterCallKitPlugin.callController.startCall(handle: callId, videoEnabled: videoEnabled, uuid: callId)
            result(true)
        }
        else if (call.method == "reportCallFinished"){
            let callId = arguments["session_id"] as! String
            let reason = arguments["reason"] as! String
            
            
            SwiftConnectycubeFlutterCallKitPlugin.callController.reportCallEnded(uuid: UUID(uuidString: callId)!, reason: CallEndedReason.init(rawValue: reason)!);
            result(true);
        }
        else if (call.method == "reportCallEnded"){
            let callId = arguments["session_id"] as! String
            SwiftConnectycubeFlutterCallKitPlugin.callController.end(uuid: UUID(uuidString: callId)!)
            result(true)
        }
        else if (call.method == "muteCall"){
            let callId = arguments["session_id"] as! String
            let muted = arguments["muted"] as! Bool
            
            SwiftConnectycubeFlutterCallKitPlugin.callController.setMute(uuid: UUID(uuidString: callId)!, muted: muted)
            result(true)
        }
        else if (call.method == "getCallState"){
            let callId = arguments["session_id"] as! String
            
            result(SwiftConnectycubeFlutterCallKitPlugin.callController.getCallState(uuid: callId).rawValue)
        }
        else if (call.method == "setCallState"){
            let callId = arguments["session_id"] as! String
            let callState = arguments["call_state"] as! String
            
            SwiftConnectycubeFlutterCallKitPlugin.callController.setCallState(uuid: callId, callState: callState)
            result(true)
        }
        
        else if (call.method == "getCallData"){
            let callId = arguments["session_id"] as! String
            
            result(SwiftConnectycubeFlutterCallKitPlugin.callController.getCallData(uuid: callId))
        }
        else if (call.method == "clearCallData"){
            let callId = arguments["session_id"] as! String
            
            SwiftConnectycubeFlutterCallKitPlugin.callController.clearCallData(uuid: callId)
            result(true)
        }
        else if (call.method == "getLastCallId"){
            result(SwiftConnectycubeFlutterCallKitPlugin.callController.currentCallData["session_id"])
        }
    }
}
