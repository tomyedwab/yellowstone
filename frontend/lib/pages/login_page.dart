import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_cookie_jar/webview_cookie_jar.dart';

class LoginPage extends StatefulWidget {
  final String loginUrl;
  final VoidCallback onLoginSuccess;

  const LoginPage({
    super.key,
    required this.loginUrl,
    required this.onLoginSuccess,
  });

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  late final WebViewController _controller;

  @override
  void initState() {
    super.initState();
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageFinished: (String url) async {
            final cookies = await WebViewCookieJar.cookieJar.loadForRequest(Uri.parse(url));
            final hasSessionToken = cookies.any((cookie) => cookie.name == 'session-token');
            if (hasSessionToken) {
              widget.onLoginSuccess();
            }
          },
        ),
      )
      ..loadRequest(Uri.parse(widget.loginUrl));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Login'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: WebViewWidget(controller: _controller),
    );
  }
}
