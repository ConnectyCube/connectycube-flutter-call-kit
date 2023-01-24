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
    private var callStates: [UUID:CallState] = [:]
    private var callsData: [UUID:[String:Any]] = [:]
    
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
    
    static func updateConfig(ringtone: String?, icon: String?) {
        if(ringtone != nil){
            providerConfiguration.ringtoneSound = ringtone
        }
        
        if(icon != nil) {
            let iconImage = UIImage(named: icon!)
            let iconData = iconImage?.pngData()
            providerConfiguration.iconTemplateImageData = iconData
        }
    }
    
    func reportIncomingCall(
        sessionId: UUID,
        callId: String,
        callerName: String,
        completion: ((Error?) -> Void)?
    ) {
        print("[CallKitController][reportIncomingCall] call data: \(sessionId), \(callId), \(callerName)")
        let update = CXCallUpdate()
        update.localizedCallerName = callerName
        update.remoteHandle = CXHandle(type: .generic, value: sessionId.uuidString)
        update.hasVideo = true
        update.supportsGrouping = false
        update.supportsUngrouping = false
        update.supportsHolding = false
        update.supportsDTMF = false
        
        if (self.currentCallData["session_id"] == nil || self.currentCallData["session_id"] as! String != sessionId.uuidString) {
            print("[CallKitController][reportIncomingCall] report new call: \(sessionId)")
            provider.reportNewIncomingCall(with: sessionId, update: update) { error in
                completion?(error)
                if(error == nil){
                    self.configureAudioSession()
                    
                    self.currentCallData["session_id"] = sessionId
                    self.currentCallData["call_id"] = callId
                    self.currentCallData["caller_name"] = callerName
                    
                    
                    self.callStates[sessionId] = .pending
                    self.callsData[sessionId] = self.currentCallData
                }
            }
        } else if (self.currentCallData["session_id"] as! String == callId) {
            print("[CallKitController][reportIncomingCall] update existing call: \(callId)")
            provider.reportCall(with: sessionId, updated: update)
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
    
    func reportCallEnded(sessionId : UUID, reason: CallEndedReason){
        print("CallKitController: report call ended: \(sessionId)")
        var cxReason : CXCallEndedReason
        switch reason {
        case .unanswered:
            cxReason = CXCallEndedReason.unanswered
        case .remoteEnded:
            cxReason = CXCallEndedReason.remoteEnded
        default:
            cxReason = CXCallEndedReason.failed
        }
        self.callStates[sessionId] = .rejected
        self.provider.reportCall(with: sessionId, endedAt: Date.init(), reason: cxReason)
    }
    
    func getCallState(sessionId: UUID) -> CallState {
        print("CallKitController: getCallState: \(self.callStates[sessionId] ?? .unknown)")
        return self.callStates[sessionId] ?? .unknown
    }
    
    func setCallState(sessionId: UUID, callState: String){
        self.callStates[sessionId] = CallState(rawValue: callState)
    }
    
    func getCallData(sessionId: UUID) -> [String: Any]{
        return self.callsData[sessionId] ?? [:]
    }
    
    func clearCallData(){
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
    
    func end(sessionId: UUID) {
        print("CallKitController: user requested end call")
        let endCallAction = CXEndCallAction(call: sessionId)
        let transaction = CXTransaction(action: endCallAction)
        
        self.callStates[sessionId] = .rejected
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
    
    func startCall(handle: String, videoEnabled: Bool, sessionId: UUID) {
        print("CallKitController: user requested start call handle:\(handle), videoEnabled: \(videoEnabled) uuid: \(sessionId)")
        let handle = CXHandle(type: .generic, value: handle)
        let startCallAction = CXStartCallAction(call: sessionId, handle: handle)
        startCallAction.isVideo = videoEnabled
        
        let transaction = CXTransaction(action: startCallAction)
        
        self.callStates[sessionId] = .accepted
        
        requestTransaction(transaction);
    }
    
    func answerCall(sessionId: UUID) {
        print("CallKitController: user requested answer call, uuid: \(sessionId)")
        let answerCallAction = CXAnswerCallAction(call: sessionId)
        let transaction = CXTransaction(action: answerCallAction)
        
        self.callStates[sessionId] = .accepted
        
        requestTransaction(transaction);
    }
}

//MARK: System notifications
extension CallKitController: CXProviderDelegate {
    func providerDidReset(_ provider: CXProvider) {
        
    }
    
    func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        print("CallKitController: Answer Call \(action.callUUID)")
        actionListener?(.answerCall, action.callUUID, currentCallData)
        self.callStates[action.callUUID] = .accepted
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
        self.callStates[action.callUUID] = .rejected
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
        self.callStates[action.callUUID] = .accepted
        action.fulfill()
    }
}


