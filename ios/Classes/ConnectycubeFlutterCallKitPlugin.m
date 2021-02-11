#import "ConnectycubeFlutterCallKitPlugin.h"
#if __has_include(<connectycube_flutter_call_kit/connectycube_flutter_call_kit-Swift.h>)
#import <connectycube_flutter_call_kit/connectycube_flutter_call_kit-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "connectycube_flutter_call_kit-Swift.h"
#endif

@implementation ConnectycubeFlutterCallKitPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftConnectycubeFlutterCallKitPlugin registerWithRegistrar:registrar];
}
@end
