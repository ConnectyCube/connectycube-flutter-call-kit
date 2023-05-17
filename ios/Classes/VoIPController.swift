//
//  VoIPController.swift
//  connectycube_flutter_call_kit
//
//  Created by Tereha on 19.11.2021.
//

import Foundation
import PushKit
import Contacts

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
            // print("[VoIPController][pushRegistry] No device token!")
            return
        }
        
        // print("[VoIPController][pushRegistry] token: \(pushCredentials.token)")
        
        let deviceToken: String = pushCredentials.token.reduce("", {$0 + String(format: "%02X", $1) })
        // print("[VoIPController][pushRegistry] deviceToken: \(deviceToken)")
        
        self.voipToken = deviceToken
        
        if tokenListener != nil {
            // print("[VoIPController][pushRegistry] notify listener")
            tokenListener!(self.voipToken!)
        }
    }
    
    func pushRegistry(_ registry: PKPushRegistry, didReceiveIncomingPushWith payload: PKPushPayload, for type: PKPushType) {
        // print("[VoIPController][pushRegistry][didReceiveIncomingPushWith] payload: \(payload.dictionaryPayload)")
        
        if type == .voIP{
            let callData = self.parse(payload: payload)

            let callId = callData!["channelName"] as! String
            let callType = callData!["callType"] as! String == "sip" ? 0 : 1
            let callInitiatorId = callData!["callerNumber"] as! String
            let callInitiatorName = self.getContactName(callInitiatorId: callInitiatorId)
            
            self.callKitController.reportIncomingCall(uuid: callId.lowercased(), callType: callType, callInitiatorId: callInitiatorId, callInitiatorName: callInitiatorName, callData: callData!) { (error) in
                if(error == nil){
                    // print("[VoIPController][didReceiveIncomingPushWith] reportIncomingCall SUCCESS")
                } else {
                    // print("[VoIPController][didReceiveIncomingPushWith] reportIncomingCall ERROR: \(error?.localizedDescription ?? "none")")
                }
            }
        }
    }

    private func parse(payload: PKPushPayload) -> [String: Any]? {
        do {
            let data = try JSONSerialization.data(withJSONObject: payload.dictionaryPayload, options: .prettyPrinted)
            let json = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any]
            let aps = json?["aps"] as? [String: Any]
            if(aps?["alert"] != nil) {
                return aps?["alert"] as? [String: Any]
            }

            return aps
        } catch let error as NSError {
            return nil
        }
    }

    private func getContactName(callInitiatorId: String) -> String {
        var contacts = [CNContact]()

        if CNContactStore.authorizationStatus(for: .contacts) == .authorized {
            let keysToFetch = [CNContactGivenNameKey as CNKeyDescriptor, CNContactPhoneNumbersKey as CNKeyDescriptor]
            let fetchRequest = CNContactFetchRequest( keysToFetch: keysToFetch)
            fetchRequest.mutableObjects = false
            fetchRequest.unifyResults = true
            do {
                try CNContactStore().enumerateContacts(with: fetchRequest) { (contact, stop) -> Void in
                    var value : String?
                    
                    for phone in contact.phoneNumbers {
                        if (phone.value.stringValue.replacingOccurrences(of:" ", with: "").contains(callInitiatorId)){
                           contacts.append(contact)
                        }
                    }
                }
            }
            catch {}
        }

        func getNameFromContacts(number: String) -> String {
            var contactName : String?
            if contacts.count > 0 {
                contactName = contacts.first?.givenName
            }
            return contactName ?? number
        }

        return getNameFromContacts(number: callInitiatorId)
    }
}
