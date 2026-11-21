import SimplioKit
import SwiftUI

@main
struct SimplioApp: App {
  var body: some Scene {
    WindowGroup {
      SimplioContent()
    }
  }
}

private struct SimplioContent: UIViewControllerRepresentable {
  func makeUIViewController(context: Context) -> UIViewController {
    AppControllerKt.makeAppController(
      apiKey: Bundle.main.object(forInfoDictionaryKey: "FirebaseAPIKey") as? String ?? "",
      projectId: Bundle.main.object(forInfoDictionaryKey: "FirebaseProjectID") as? String ?? ""
    )
  }

  func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
