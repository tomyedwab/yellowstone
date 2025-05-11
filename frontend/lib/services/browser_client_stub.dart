// frontend/lib/services/browser_client_stub.dart
import 'package:http/http.dart' as http;
import 'dart:async'; // For Future

// Stub for BrowserClient for non-web platforms
class BrowserClient extends http.BaseClient {
  bool _withCredentials = false;

  bool get withCredentials => _withCredentials;
  set withCredentials(bool value) {
    _withCredentials = value;
  }

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    // This should not be called on non-web platforms if kIsWeb guards are correct.
    throw UnsupportedError("BrowserClient.send() called on a non-web platform. "
        "This indicates a kIsWeb logic error or an attempt to use BrowserClient features directly.");
  }

  @override
  void close() {
    // No-op for the stub. The actual BrowserClient might have resources to release.
  }
}
