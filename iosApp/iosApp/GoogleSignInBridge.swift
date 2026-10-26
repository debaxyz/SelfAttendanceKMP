import UIKit
import ComposeApp
import FirebaseCore
import GoogleSignIn

/// iOS side of the shared `GoogleIdTokenProvider` interface (Android uses Credential Manager).
final class GoogleSignInBridge: NSObject, GoogleIdTokenProvider {

    func requestToken(silent: Bool, callback: GoogleTokenCallback) {
        DispatchQueue.main.async {
            guard let clientID = FirebaseApp.app()?.options.clientID else {
                callback.onResult(result: Self.failure("Missing CLIENT_ID in GoogleService-Info.plist"))
                return
            }
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)

            let finish: (GIDGoogleUser?, Error?) -> Void = { user, error in
                if let error = error as NSError? {
                    let cancelled = error.domain == kGIDSignInErrorDomain
                        && error.code == GIDSignInError.canceled.rawValue
                    callback.onResult(result: GoogleTokenResult(
                        idToken: nil, accessToken: nil, cancelled: cancelled,
                        error: cancelled ? nil : error.localizedDescription))
                    return
                }
                guard let user = user, let idToken = user.idToken?.tokenString else {
                    callback.onResult(result: Self.failure("Google did not return an ID token"))
                    return
                }
                callback.onResult(result: GoogleTokenResult(
                    idToken: idToken, accessToken: user.accessToken.tokenString,
                    cancelled: false, error: nil))
            }

            if silent {
                GIDSignIn.sharedInstance.restorePreviousSignIn { user, error in finish(user, error) }
            } else {
                guard let vc = Self.topViewController() else {
                    callback.onResult(result: Self.failure("No view controller to present Google sign-in"))
                    return
                }
                GIDSignIn.sharedInstance.signIn(withPresenting: vc) { result, error in
                    finish(result?.user, error)
                }
            }
        }
    }

    func clearState(callback: GoogleDoneCallback) {
        DispatchQueue.main.async {
            GIDSignIn.sharedInstance.signOut()
            callback.onDone()
        }
    }

    private static func failure(_ message: String) -> GoogleTokenResult {
        GoogleTokenResult(idToken: nil, accessToken: nil, cancelled: false, error: message)
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var vc = scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController
        while let presented = vc?.presentedViewController { vc = presented }
        return vc
    }
}
