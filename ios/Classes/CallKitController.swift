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
}

enum CallEndedReason : String {
    case failed = "failed"
    case unanswered = "unanswered"
    case remoteEnded = "remoteEnded"
}

class CallKitController : NSObject {
    private let provider : CXProvider
    var actionListener : ((CallEvent, UUID, Any?)->Void)?
    private let callController = CXCallController()
    
    override init() {
        provider = CXProvider(configuration: CallKitController.providerConfiguration)
        
        super.init()
        provider.setDelegate(self, queue: nil)
    }
    
    //TODO: construct configuration from flutter. pass into init over method channel
    static var providerConfiguration: CXProviderConfiguration = {
        var providerConfiguration: CXProviderConfiguration
        if #available(iOS 14.0, *) {
            providerConfiguration = CXProviderConfiguration.init()
        } else {
            providerConfiguration = CXProviderConfiguration(localizedName: "Flutter Voip Kit") //TODO:
        }
        
        providerConfiguration.supportsVideo = true
        providerConfiguration.maximumCallsPerCallGroup = 1
        providerConfiguration.supportedHandleTypes = [.phoneNumber]
        
        return providerConfiguration
    }()
    
    func reportIncomingCall(
        uuid: UUID,
        handle: String,
        hasVideo: Bool = false,
        completion: ((Error?) -> Void)?
    ) {
        print("report incoming call: \(handle) \(uuid)")
        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .phoneNumber, value: handle)
        update.hasVideo = hasVideo
        
        provider.reportNewIncomingCall(with: uuid, update: update) { error in
            completion?(error)
        }
    }
    
    func reportOutgoingCall(uuid : UUID, finishedConnecting: Bool){
        print("report outgoing call: \(uuid) connected:\(finishedConnecting)")
        if !finishedConnecting {
            self.provider.reportOutgoingCall(with: uuid, startedConnectingAt: nil)
        } else {
            self.provider.reportOutgoingCall(with: uuid, connectedAt: nil)
        }
    }
    
    func reportCallEnded(uuid : UUID, reason: CallEndedReason){
        print("report call ended: \(uuid)")
        var cxReason : CXCallEndedReason?
        switch reason {
        case .unanswered:
            cxReason = CXCallEndedReason.unanswered
        case .remoteEnded:
        cxReason = CXCallEndedReason.remoteEnded
        default:
            cxReason = CXCallEndedReason.failed
        }
        self.provider.reportCall(with: uuid, endedAt: Date.init(), reason: cxReason!)
    }
}

//MARK: user actions
extension CallKitController {
    
    func end(uuid: UUID) {
        print("CallController: user requested end call")
        let endCallAction = CXEndCallAction(call: uuid)
        let transaction = CXTransaction(action: endCallAction)
        
        requestTransaction(transaction)
    }
    
    private func requestTransaction(_ transaction: CXTransaction) {
        callController.request(transaction) { error in
            if let error = error {
                print("Error requesting transaction: \(error)")
            } else {
                print("Requested transaction successfully")
            }
        }
    }
    
    func setHeld(uuid: UUID, onHold: Bool) {
        print("CallController: user requested hold call")
        let setHeldCallAction = CXSetHeldCallAction(call: uuid, onHold: onHold)
        
        let transaction = CXTransaction()
        transaction.addAction(setHeldCallAction)
        
        requestTransaction(transaction)
    }
    
    func setMute(uuid: UUID, muted: Bool){
        let muteCallAction = CXSetMutedCallAction(call: uuid, muted: muted);
        let transaction = CXTransaction()
        transaction.addAction(muteCallAction)
        requestTransaction(transaction)
    }
    
    func startCall(handle: String, videoEnabled: Bool, uuid: String? = nil) {
        print("CallController: user requested start call \(handle)")
        let handle = CXHandle(type: .phoneNumber, value: handle)
        let callUUID = uuid == nil ? UUID() : UUID(uuidString: uuid!);
        let startCallAction = CXStartCallAction(call: callUUID!, handle: handle)
        startCallAction.isVideo = videoEnabled
        
        let transaction = CXTransaction(action: startCallAction)
        
        requestTransaction(transaction)
    }
}

//MARK: System notifications
extension CallKitController: CXProviderDelegate {
    func providerDidReset(_ provider: CXProvider) {
        
    }
    
    //action.callUUID
    func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        print("CallController: Answer Call")
        actionListener?(.answerCall,action.callUUID,nil)
        action.fulfill()
    }
    
    func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        //startAudio()
        
        print("CallController: Audio session activated")
        
    }
    
    func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        print("CallController: End Call")
        actionListener?(.endCall, action.callUUID,nil)
        action.fulfill()
    }
    
    func provider(_ provider: CXProvider, perform action: CXSetHeldCallAction) {
        print("CallController: Set Held")
        actionListener?(.setHeld, action.callUUID,action.isOnHold)
        action.fulfill()
    }
    
     func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        print("CallController: Set Held")
        actionListener?(.setMuted, action.callUUID,action.isMuted)
        action.fulfill()
    }
    
    func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        actionListener?(.startCall, action.callUUID, action.handle.value)
        print("CallController: Start Call")
    }
}


