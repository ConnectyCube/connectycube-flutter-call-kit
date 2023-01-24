//
//  CallKitController.swift
//  connectycube_flutter_call_kit
//
//  Created by Tereha on 19.11.2021.
//

import Foundation
import AVFoundation
import CallKit



enum CallEvent : String {
    case answerCall = "answerCall"
    case endCall = "endCall"
    case setHeld = "setHeld"
    case reset = "reset"
    case startCall = "startCall"
    case setMuted = "setMuted"
    case setUnMuted = "setUnMuted"
}

enum CallEndedReason : String {
    case failed = "failed"
    case unanswered = "unanswered"
    case remoteEnded = "remoteEnded"
}

enum CallState : String {
    case pending = "pending"
    case accepted = "accepted"
    case rejected = "rejected"
    case unknown = "unknown"
}

class CallKitController : NSObject {
    private let provider : CXProvider
    private let callController : CXCallController
    var actionListener : ((CallEvent, UUID, [String:Any]?)->Void)?
    var currentCallData: [String: Any] = [:]
    private var callStates: [String:CallState] = [:]
    private var callsData: [String:[String:Any]] = [:]
    
    override init() {
        self.provider = CXProvider(configuration: CallKitController.providerConfiguration)
        self.callController = CXCallController()
        
        super.init()
        self.provider.setDelegate(self, queue: nil)
    }
    
    //TODO: construct configuration from flutter. pass into init over method channel
    static var providerConfiguration: CXProviderConfiguration = {
        let appName = Bundle.main.infoDictionary?[kCFBundleNameKey as String] as! String
        var providerConfiguration: CXProviderConfiguration
        if #available(iOS 14.0, *) {
            providerConfiguration = CXProviderConfiguration.init()
        } else {
            providerConfiguration = CXProviderConfiguration(localizedName: appName)
        }
        
        providerConfiguration.supportsVideo = true
        providerConfiguration.maximumCallsPerCallGroup = 1
        providerConfiguration.maximumCallGroups = 1;
        providerConfiguration.supportedHandleTypes = [.generic]
        
        if #available(iOS 11.0, *) {
            providerConfiguration.includesCallsInRecents = false
        }
        
        return providerConfiguration
    }()
    
    static func updateConfig(
        ringtone: String?,
        icon: String?
        
    ) {
        if(ringtone != nil){
            providerConfiguration.ringtoneSound = ringtone
        }
        
        if(icon != nil){
            let iconImage = UIImage(named: icon!)
            let iconData = iconImage?.pngData()
            providerConfiguration.iconTemplateImageData = iconData
        }
    }
    
    func reportIncomingCall(
        uuid: String,
        callType: String,
        callInitiatorId: String,
        callInitiatorName: String,
        opponents: [Int],
        userInfo: String?,
        completion: ((Error?) -> Void)?
    ) {
        print("[CallKitController][reportIncomingCall] call data: \(uuid), \(callType), \(callInitiatorId), \(callInitiatorName), \(opponents), \(userInfo ?? ""), ")
        let update = CXCallUpdate()
        update.localizedCallerName = callInitiatorName
        update.remoteHandle = CXHandle(type: .generic, value: uuid)
        update.hasVideo = callType == "1"
        update.supportsGrouping = false
        update.supportsUngrouping = false
        update.supportsHolding = false
        update.supportsDTMF = false
        
        if (self.currentCallData["session_id"] == nil || self.currentCallData["session_id"] as! String != uuid) {
            print("[CallKitController][reportIncomingCall] report new call: \(uuid)")
            provider.reportNewIncomingCall(with: UUID(uuidString: uuid)!, update: update) { error in
                completion?(error)
                if(error == nil){
                    self.configureAudioSession()
                    
                    self.currentCallData["session_id"] = uuid
                    self.currentCallData["call_type"] = callType
                    self.currentCallData["caller_id"] = callInitiatorId
                    self.currentCallData["caller_name"] = callInitiatorName
                    self.currentCallData["call_opponents"] = opponents.map { String($0) }.joined(separator: ",")
                    self.currentCallData["user_info"] = userInfo
                    
                    self.callStates[uuid] = .pending
                    self.callsData[uuid] = self.currentCallData
                }
            }
        } else if (self.currentCallData["session_id"] as! String == uuid) {
            print("[CallKitController][reportIncomingCall] update existing call: \(uuid)")
            provider.reportCall(with: UUID(uuidString: uuid)!, updated: update)
        }
    }
    
    func reportOutgoingCall(uuid : UUID, finishedConnecting: Bool){
        print("CallKitController: report outgoing call: \(uuid) connected:\(finishedConnecting)")
        if !finishedConnecting {
            self.provider.reportOutgoingCall(with: uuid, startedConnectingAt: nil)
        } else {
            self.provider.reportOutgoingCall(with: uuid, connectedAt: nil)
        }
    }
    
    func reportCallEnded(uuid : UUID, reason: CallEndedReason){
        print("CallKitController: report call ended: \(uuid)")
        var cxReason : CXCallEndedReason
        switch reason {
        case .unanswered:
            cxReason = CXCallEndedReason.unanswered
        case .remoteEnded:
            cxReason = CXCallEndedReason.remoteEnded
        default:
            cxReason = CXCallEndedReason.failed
        }
        self.callStates[uuid.uuidString.lowercased()] = .rejected
        self.provider.reportCall(with: uuid, endedAt: Date.init(), reason: cxReason)
    }
    
    func getCallState(uuid: String) -> CallState {
        print("CallKitController: getCallState: \(self.callStates[uuid.lowercased()] ?? .unknown)")
        return self.callStates[uuid.lowercased()] ?? .unknown
    }
    
    func setCallState(uuid: String, callState: String){
        self.callStates[uuid.lowercased()] = CallState(rawValue: callState)
    }
    
    func getCallData(uuid: String) -> [String: Any]{
        return self.callsData[uuid.lowercased()] ?? [:]
    }
    
    func clearCallData(uuid: String){
        self.callStates.removeAll()
        self.callsData.removeAll()
    }
    
    func configureAudioSession(){
        let audioSession = AVAudioSession.sharedInstance()
        
        do {
            try audioSession.setCategory(AVAudioSession.Category.playback, options: AVAudioSession.CategoryOptions.allowBluetooth)
            try audioSession.setMode(AVAudioSession.Mode.voiceChat)
            try audioSession.setPreferredSampleRate(44100.0)
            try audioSession.setPreferredIOBufferDuration(0.005)
            try audioSession.setActive(true)
        } catch {
            print(error)
        }
    }
}

//MARK: user actions
extension CallKitController {
    
    func end(uuid: UUID) {
        print("CallKitController: user requested end call")
        let endCallAction = CXEndCallAction(call: uuid)
        let transaction = CXTransaction(action: endCallAction)
        
        self.callStates[uuid.uuidString.lowercased()] = .rejected
        requestTransaction(transaction)
    }
    
    private func requestTransaction(_ transaction: CXTransaction) {
        callController.request(transaction) { error in
            if let error = error {
                print("CallKitController: Error requesting transaction: \(error.localizedDescription)")
            } else {
                print("CallKitController: Requested transaction successfully")
            }
        }
    }
    
    func setHeld(uuid: UUID, onHold: Bool) {
        print("CallKitController: user requested hold call")
        let setHeldCallAction = CXSetHeldCallAction(call: uuid, onHold: onHold)
        
        let transaction = CXTransaction()
        transaction.addAction(setHeldCallAction)
        
        requestTransaction(transaction)
    }
    
    func setMute(uuid: UUID, muted: Bool){
        print("CallKitController: user requested mute call: muted - \(muted)")
        let muteCallAction = CXSetMutedCallAction(call: uuid, muted: muted);
        let transaction = CXTransaction()
        transaction.addAction(muteCallAction)
        requestTransaction(transaction)
    }
    
    func startCall(handle: String, videoEnabled: Bool, uuid: String? = nil) {
        print("CallKitController: user requested start call handle:\(handle), videoEnabled: \(videoEnabled) uuid: \(uuid ?? "")")
        let handle = CXHandle(type: .generic, value: handle)
        let callUUID = uuid == nil ? UUID() : UUID(uuidString: uuid!)
        let startCallAction = CXStartCallAction(call: callUUID!, handle: handle)
        startCallAction.isVideo = videoEnabled
        
        let transaction = CXTransaction(action: startCallAction)
        
        self.callStates[uuid!.lowercased()] = .accepted
        
        requestTransaction(transaction);
    }
    
    func answerCall(uuid: String) {
        print("CallKitController: user requested answer call, uuid: \(uuid)")
        let callUUID = UUID(uuidString: uuid)
        let answerCallAction = CXAnswerCallAction(call: callUUID!)
        let transaction = CXTransaction(action: answerCallAction)
        
        self.callStates[uuid.lowercased()] = .accepted
        
        requestTransaction(transaction);
    }
}

//MARK: System notifications
extension CallKitController: CXProviderDelegate {
    func providerDidReset(_ provider: CXProvider) {
        
    }
    
    func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        print("CallKitController: Answer Call \(action.callUUID.uuidString)")
        actionListener?(.answerCall, action.callUUID, currentCallData)
        self.callStates[action.callUUID.uuidString.lowercased()] = .accepted
        action.fulfill()
    }
    
    func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        print("CallKitController: Audio session activated")
        self.configureAudioSession()
    }
    
    func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {
        print("CallKitController: Audio session deactivated")
    }
    
    func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        print("CallKitController: End Call")
        actionListener?(.endCall, action.callUUID, currentCallData)
        self.callStates[action.callUUID.uuidString.lowercased()] = .rejected
        action.fulfill()
    }
    
    func provider(_ provider: CXProvider, perform action: CXSetHeldCallAction) {
        print("CallKitController: Set Held")
        actionListener?(.setHeld, action.callUUID, ["isOnHold": action.isOnHold])
        action.fulfill()
    }
    
    func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        print("CallKitController: Mute call")
        if (action.isMuted){
            actionListener?(.setMuted, action.callUUID, currentCallData)
        } else {
            actionListener?(.setUnMuted, action.callUUID, currentCallData)
        }
        
        action.fulfill()
    }
    
    func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        print("CallKitController: Start Call")
        actionListener?(.startCall, action.callUUID, currentCallData)
        self.callStates[action.callUUID.uuidString.lowercased()] = .accepted
        action.fulfill()
    }
}


