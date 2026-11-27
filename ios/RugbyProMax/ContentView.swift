import SwiftUI
import WebKit

private let rugbyPortalURL = URL(string: "https://porotoo017.github.io/rugby-intelligence-engine/")!

struct ContentView: View {
    @State private var showSplash = true
    @State private var isLoading = true
    @State private var hasError = false
    @State private var reloadTrigger = UUID()

    var body: some View {
        ZStack {
            Color(red: 11.0 / 255.0, green: 15.0 / 255.0, blue: 20.0 / 255.0)
                .ignoresSafeArea()

            RugbyWebContainer(
                url: rugbyPortalURL,
                reloadTrigger: reloadTrigger,
                isLoading: $isLoading,
                hasError: $hasError
            )
            .ignoresSafeArea(edges: .bottom)

            if hasError {
                VStack(spacing: 16) {
                    Image(systemName: "wifi.slash")
                        .font(.system(size: 42, weight: .semibold))
                        .foregroundColor(Color(red: 1.0, green: 215.0 / 255.0, blue: 0.0))
                    Text("Sin conexión")
                        .font(.title2.bold())
                        .foregroundColor(.white)
                    Text("No se pudo cargar la transmisión en vivo. Verificá tu conexión e intentá nuevamente.")
                        .font(.subheadline)
                        .foregroundColor(Color.white.opacity(0.76))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 28)
                    Button(action: {
                        hasError = false
                        reloadTrigger = UUID()
                    }) {
                        Text("Reintentar")
                            .font(.headline)
                            .foregroundColor(.black)
                            .padding(.horizontal, 28)
                            .padding(.vertical, 12)
                            .background(Color(red: 0.0, green: 200.0 / 255.0, blue: 83.0 / 255.0))
                            .cornerRadius(12)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(red: 11.0 / 255.0, green: 15.0 / 255.0, blue: 20.0 / 255.0).opacity(0.95))
            }

            if showSplash {
                ZStack {
                    Color(red: 11.0 / 255.0, green: 15.0 / 255.0, blue: 20.0 / 255.0)
                        .ignoresSafeArea()
                    Image("LaunchImage")
                        .resizable()
                        .scaledToFill()
                        .ignoresSafeArea()
                        .opacity(0.88)
                    LinearGradient(
                        colors: [
                            Color.black.opacity(0.45),
                            Color.black.opacity(0.75),
                            Color(red: 11.0 / 255.0, green: 15.0 / 255.0, blue: 20.0 / 255.0).opacity(0.95)
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .ignoresSafeArea()
                    VStack(spacing: 12) {
                        Text("RUGBY PRO")
                            .font(.system(size: 34, weight: .heavy, design: .rounded))
                            .foregroundColor(.white)
                        Text("RUGBY DE ÉLITE · EN VIVO")
                            .font(.caption.bold())
                            .tracking(1.6)
                            .foregroundColor(Color(red: 1.0, green: 215.0 / 255.0, blue: 0.0))
                        ProgressView()
                            .tint(Color(red: 0.0, green: 200.0 / 255.0, blue: 83.0 / 255.0))
                            .padding(.top, 8)
                    }
                }
                .transition(.opacity)
                .zIndex(2)
            }
        }
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) {
                withAnimation(.easeOut(duration: 0.35)) {
                    showSplash = false
                }
            }
        }
    }
}

struct RugbyWebContainer: UIViewRepresentable {
    let url: URL
    let reloadTrigger: UUID
    @Binding var isLoading: Bool
    @Binding var hasError: Bool

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.defaultWebpagePreferences.allowsContentJavaScript = true

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.isOpaque = false
        webView.backgroundColor = UIColor(red: 11.0 / 255.0, green: 15.0 / 255.0, blue: 20.0 / 255.0, alpha: 1.0)
        webView.scrollView.backgroundColor = webView.backgroundColor
        webView.allowsBackForwardNavigationGestures = true

        let refreshControl = UIRefreshControl()
        refreshControl.tintColor = UIColor(red: 0.0, green: 200.0 / 255.0, blue: 83.0 / 255.0, alpha: 1.0)
        refreshControl.addTarget(context.coordinator, action: #selector(Coordinator.handleRefresh(_:)), for: .valueChanged)
        webView.scrollView.refreshControl = refreshControl

        context.coordinator.webView = webView
        context.coordinator.lastReloadTrigger = reloadTrigger
        webView.load(URLRequest(url: url, cachePolicy: .reloadRevalidatingCacheData, timeoutInterval: 20))
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        if context.coordinator.lastReloadTrigger != reloadTrigger {
            context.coordinator.lastReloadTrigger = reloadTrigger
            uiView.load(URLRequest(url: url, cachePolicy: .reloadRevalidatingCacheData, timeoutInterval: 20))
        }
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        var parent: RugbyWebContainer
        weak var webView: WKWebView?
        var lastReloadTrigger = UUID()

        init(_ parent: RugbyWebContainer) {
            self.parent = parent
        }

        @objc func handleRefresh(_ sender: UIRefreshControl) {
            webView?.reload()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                sender.endRefreshing()
            }
        }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            parent.isLoading = true
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            parent.isLoading = false
            parent.hasError = false
            webView.scrollView.refreshControl?.endRefreshing()
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            parent.isLoading = false
            parent.hasError = true
            webView.scrollView.refreshControl?.endRefreshing()
        }

        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            if let reqUrl = navigationAction.request.url,
               navigationAction.navigationType == .linkActivated,
               reqUrl.host != "porotoo017.github.io" {
                UIApplication.shared.open(reqUrl)
                decisionHandler(.cancel)
                return
            }
            decisionHandler(.allow)
        }
    }
}
