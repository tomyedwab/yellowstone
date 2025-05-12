import 'dart:async';
import 'dart:convert';
import 'dart:io'; // For Cookie parsing
import 'package:http/http.dart' as http;
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart'; // Import flutter_secure_storage
// Conditional import for dart:html
import 'html_stub.dart' if (dart.library.html) 'dart:html' as html;
// Conditional import for BrowserClient
import 'browser_client_stub.dart'
    if (dart.library.html) 'package:http/browser_client.dart'
    show BrowserClient;

typedef LoginRedirectHandler = void Function();

/// Exception thrown when a request should be retried after a successful token refresh.
class RetryRequestException implements Exception {
  final String message;
  RetryRequestException(
      [this.message = "Request should be retried after token refresh."]);

  @override
  String toString() => "RetryRequestException: $message";
}

class YesterdayAuth {
  final String _appID;
  final LoginRedirectHandler? _navigateToLoginHandler;
  bool _isRefreshingToken = false;
  Completer<String?>? _refreshTokenCompleter;
  String? _accessToken;
  static const String _loginApi = String.fromEnvironment("LOGIN_API");
  static const String _refreshUrl = '$_loginApi/api/refresh';
  static const String _loginUrl = '$_loginApi/';

  YesterdayAuth(this._appID, this._navigateToLoginHandler);

  String? get accessToken => _accessToken;

  // Create storage
  final _storage = const FlutterSecureStorage(
    aOptions: AndroidOptions(
      encryptedSharedPreferences: true,
    ),
  );

  Future<bool> _storeYRTToken(Map<String, String> cookies) async {
    if (kIsWeb) {
      // Stored automatically in browser cookies.
      return true;
    }

    // Android: Parse the Set-Cookie header and move the YRT cookie to secure
    // storage where we can access it for refresh requests.
    final setCookieHeader = cookies['set-cookie'];
    if (setCookieHeader != null) {
      // Split the header string in case multiple cookies are present
      // This regex splits on commas that are likely delimiters between cookies
      final cookieStrings = setCookieHeader.split(RegExp(r',(?=[^ ;]+=)'));
      String? yrtCookieValue;

      for (final cookieStr in cookieStrings) {
        try {
          final cookie = Cookie.fromSetCookieValue(cookieStr.trim());
          if (cookie.name == 'YRT') {
            // Assuming the cookie name is YRT
            yrtCookieValue = cookie.value;
            break;
          }
        } catch (e) {
          // Log parsing error or handle as needed
          if (kDebugMode) {
            print('Error parsing cookie string: $cookieStr. Error: $e');
          }
        }
      }

      if (yrtCookieValue != null) {
        if (kDebugMode) {
          print('YRT Cookie Value: $yrtCookieValue');
        }
        await _storage.write(key: 'yrt_cookie', value: yrtCookieValue);
        if (kDebugMode) {
          print('YRT Cookie saved to secure storage');
        }
        return true;
      } else {
        if (kDebugMode) {
          print('YRT cookie not found in Set-Cookie header');
        }
        return false;
      }
    }
    return false;
  }

  Future<String?> _refreshAccessToken() async {
    if (_isRefreshingToken) {
      if (kDebugMode) {
        print('Token refresh already in progress. Waiting for completion...');
      }
      // Wait for the other refresh to complete
      return await _refreshTokenCompleter!.future;
    }
    _isRefreshingToken = true;
    _refreshTokenCompleter = Completer<String?>();

    http.Client client;
    String? yrtCookieValue;

    if (kIsWeb) {
      // Make sure normal cookies are sent with the request
      client = BrowserClient()..withCredentials = true;
    } else {
      client = http.Client();
      // For mobile, try to load YRT cookie from secure storage
      const storage = FlutterSecureStorage(
        aOptions: AndroidOptions(
          encryptedSharedPreferences: true,
        ),
      );
      try {
        yrtCookieValue = await storage.read(key: 'yrt_cookie');
        if (kDebugMode) {
          if (yrtCookieValue != null) {
            print('YRT cookie found in secure storage for refresh.');
          } else {
            print('YRT cookie not found in secure storage for refresh.');
            _refreshTokenCompleter!.complete(null);
            return null;
          }
        }
      } catch (e) {
        if (kDebugMode) {
          print('Error reading YRT cookie from secure storage: $e');
        }
        _refreshTokenCompleter!.complete(null);
        return null;
      }
    }

    try {
      final refreshUri = Uri.parse(_refreshUrl).replace(queryParameters: {
        'app': _appID,
      });
      final request = http.Request('POST', refreshUri)
        ..headers['Content-Type'] = 'application/json'
        ..followRedirects = false
        ..maxRedirects = 0;

      if (!kIsWeb && yrtCookieValue != null) {
        // yrtCookieValue should be just the token value, not the full "YRT=value;..." string.
        request.headers['Cookie'] = 'YRT=$yrtCookieValue';
      }

      final streamedResponse = await client.send(request);
      final response = await http.Response.fromStream(streamedResponse);

      if (response.statusCode == 200) {
        await _storeYRTToken(response.headers);
        final respData = json.decode(response.body);
        final accessToken = respData['access_token'] as String?;
        if (accessToken != null) {
          if (kDebugMode) {
            print('Access token refreshed successfully.');
          }
          _refreshTokenCompleter!.complete(accessToken);
          return accessToken;
        }
        if (kDebugMode) {
          print('Access token not found in refresh response.');
        }
        _refreshTokenCompleter!.complete(null);
        return null;
      } else {
        if (kDebugMode) {
          print(
              'Access token refresh failed: ${response.statusCode} ${response.body}');
        }
        _refreshTokenCompleter!.complete(null);
        return null;
      }
    } catch (e) {
      if (kDebugMode) {
        print('Error during access token refresh: $e');
      }
      _refreshTokenCompleter!.complete(null);
      return null;
    } finally {
      client.close();
      _isRefreshingToken = false;
    }
  }

  // Returns true if a retry should be attempted, otherwise throws or returns false.
  Future<void> handleResponse(http.Response response) async {
    if (response.statusCode == 401) {
      if (kDebugMode) {
        print('Received 401, attempting token refresh...');
      }
      String? newToken = await _refreshAccessToken();
      if (newToken != null) {
        _accessToken = newToken;
        //notifyListeners(); // Token changed, might affect UI or other requests
        // Signal to the caller that the request should be retried.
        throw RetryRequestException();
      } else {
        if (kIsWeb) {
          html.window.location.href = _loginUrl + "?app=$_appID";
          return;
        }
        // Refresh failed, redirect to login.
        if (_navigateToLoginHandler != null) {
          if (kDebugMode) {
            print('Navigating to login page.');
          }
          _navigateToLoginHandler!();
          throw Exception('Failed to refresh token. Redirecting to login.');
        } else {
          if (kDebugMode) {
            print('No navigation to login page handler set.');
          }
          throw Exception(
              'Failed to refresh token and no login redirect handler set.');
        }
      }
    }
    // For other status codes, this handler currently does nothing.
    // Original logic only handled 401 for immediate redirect.
    // If other general status code handling is needed here, it can be added.
  }

  Future<bool> attemptLogin(String username, String password) async {
    if (kDebugMode) {
      print('Logging in with username: $username to $_loginUrl');
    }
    final response = await http.post(
      Uri.parse('$_loginUrl/api/login?app=$_appID'),
      headers: <String, String>{
        'Content-Type': 'application/json; charset=UTF-8',
      },
      body: jsonEncode(<String, String>{
        'username': username,
        'password': password,
      }),
    );

    if (response.statusCode == 200) {
      // If the server returns a 200 OK response,
      // then parse the returned cookie.
      final success = await _storeYRTToken(response.headers);
      return success;
    }
    return false;
  }
}
