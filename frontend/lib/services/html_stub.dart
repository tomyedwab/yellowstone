// frontend/lib/services/html_stub.dart
// Stub for dart:html when not on web

// Mock enough of the API for html.window.location.href
class _Location {
  String? href;
  void assign(String? url) {
    // In a stub, this might log or do nothing
    print('Stub html.window.location.assign called with: $url');
    href = url;
  }
}

class _Window {
  final _Location location = _Location();
}

final _Window window = _Window();

// If other dart:html members are needed, they can be stubbed here.
// For example, if html.window.open is used:
// void open(String? url, String? name, [String? options]) {
//   print('Stub html.window.open called with: $url, $name, $options');
// }
