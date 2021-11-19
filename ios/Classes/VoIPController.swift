//
//  VoIPController.swift
//  connectycube_flutter_call_kit
//
//  Created by Tereha on 19.11.2021.
//

import Foundation
import PushKit

class VoIPController : NSObject{
    let callKitController: CallKitController
    var tokenListener : ((String)->Void)?
    var voipToken: String?
    
    public required init(withCallKitController callKitController: CallKitController) {
        self.callKitController = callKitController
        super.init()
        
        //http://stackoverflow.com/questions/27245808/implement-pushkit-and-test-in-development-behavior/28562124#28562124
        let pushRegistry = PKPushRegistry(queue: DispatchQueue.main)
        pushRegistry.delegate = self
        pushRegistry.desiredPushTypes = Set<PKPushType>([.voIP])
    }
    
    func getVoIPToken() -> String? {
        return voipToken
    }
}

//MARK: VoIP Token notifications
extension VoIPController: PKPushRegistryDelegate {
    func pushRegistry(_ registry: PKPushRegistry, didUpdate pushCredentials: PKPushCredentials, for type: PKPushType) {
        if pushCredentials.token.count == 0 {
                    print("[SwiftConnectycubeFlutterCallKitPlugin][pushRegistry] No device token!")
                    return
                }
                
                print("[SwiftConnectycubeFlutterCallKitPlugin][pushRegistry] token: \(pushCredentials.token)")

                let deviceToken: String = pushCredentials.token.reduce("", {$0 + String(format: "%02X", $1) })
                print("[SwiftConnectycubeFlutterCallKitPlugin][pushRegistry] deviceToken: \(deviceToken)")
        
        self.voipToken = deviceToken
        
        if tokenListener != nil {
            tokenListener?(self.voipToken!)
        }
    }
    
    func pushRegistry(_ registry: PKPushRegistry, didReceiveIncomingPushWith payload: PKPushPayload, for type: PKPushType) {
        
    }
}
