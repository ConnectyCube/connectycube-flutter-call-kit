// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "connectycube_flutter_call_kit",
    platforms: [
        .iOS("13.0")
    ],
    products: [
        .library(name: "connectycube-flutter-call-kit", targets: ["connectycube_flutter_call_kit"])
    ],
    dependencies: [
        .package(name: "FlutterFramework", path: "../FlutterFramework")
    ],
    targets: [
        .target(
            name: "connectycube_flutter_call_kit",
            dependencies: [
                .product(name: "FlutterFramework", package: "FlutterFramework")
            ]
        )
    ]
)
