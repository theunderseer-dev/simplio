import CryptoKit
import Foundation
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
      projectId: Bundle.main.object(forInfoDictionaryKey: "FirebaseProjectID") as? String ?? "",
      databasePath: attendanceDatabasePath
    )
  }

  func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

private func attendanceDatabasePath(projectId: String, userId: String) -> String? {
  let files = FileManager.default
  let account = SHA256.hash(data: Data("\(projectId)/\(userId)".utf8))
    .map { String(format: "%02x", $0) }.joined()
  do {
    let support = try files.url(
      for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
    let directory = support.appending(
      path: "Simplio/accounts/\(account)", directoryHint: .isDirectory)
    try files.createDirectory(at: directory, withIntermediateDirectories: true)
    return directory.appending(path: "simplio.db").path
  } catch {
    return nil
  }
}
