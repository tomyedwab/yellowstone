import 'dart:convert';
import 'dart:math';
import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import './auth.dart';

typedef InFlightRequest = (String clientId, Map<String, Object?> event);

class YesterdayApi extends ChangeNotifier {
  static const String _appApi = String.fromEnvironment("APP_API");
  static const String _baseUrl = '$_appApi/api';

  final YesterdayAuth _auth;
  int _currentEventId = 0;
  String _currentServerVersion = '';
  bool _isPolling = false;
  bool get isPolling => _isPolling;

  // Chronologically ordered list of <clientId, event> tuples
  final List<InFlightRequest> _inFlightRequests = [];
  List<InFlightRequest> get inFlightRequests => _inFlightRequests;

  // Cache storage
  final Map<String, String> _responseCache = {};
  int _lastCacheEventId = 0;

  YesterdayApi(this._auth) {
    _random = Random();
    // Start polling automatically
    startPolling();
  }

  void _clearCacheIfEventChanged() {
    if (_lastCacheEventId != _currentEventId) {
      _responseCache.clear();
      _lastCacheEventId = _currentEventId;
    }
  }

  Future<http.Response> getCachedResponse(String uri) async {
    _clearCacheIfEventChanged();

    final cachedResponse = _responseCache[uri];
    if (cachedResponse != null) {
      return http.Response(cachedResponse, 200);
    }

    final streamedResponse =
        await http.Client().send(await createGetRequest('$_baseUrl$uri'));
    final response = await http.Response.fromStream(streamedResponse);
    _auth.handleResponse(response);

    if (response.statusCode == 200) {
      _responseCache[uri] = response.body;
    }

    return response;
  }

  // Utility function to create a request for a url without following redirects
  Future<http.Request> createGetRequest(String url) async {
    final request = http.Request('GET', Uri.parse(url))
      ..followRedirects = false
      ..maxRedirects = 0;

    if (_auth.accessToken != null) {
      request.headers['Authorization'] = 'Bearer ${_auth.accessToken}';
    }
    return request;
  }

  Future<http.StreamedResponse> doPublishRequest(
      Map<String, Object?> event) async {
    final clientId = _generateClientId();
    _inFlightRequests.add((clientId, event));
    notifyListeners();

    final request =
        http.Request('POST', Uri.parse('$_baseUrl/publish?cid=$clientId'))
          ..followRedirects = false
          ..maxRedirects = 0;

    if (_auth.accessToken != null) {
      request.headers['Authorization'] = 'Bearer ${_auth.accessToken}';
    }
    request.headers['Content-Type'] = 'application/json';
    event['timestamp'] = DateTime.now().toUtc().toIso8601String();
    request.body = json.encode(event);
    final response = await http.Client().send(request);
    _inFlightRequests.removeWhere((request) => request.$1 == clientId);
    if (response.statusCode != 200) {
      throw Exception('Failed to publish event');
    }
    return response;
  }

  late final Random _random;

  String _generateClientId() {
    return List.generate(16, (_) => _random.nextInt(16).toRadixString(16))
        .join();
  }

  Future<void> startPolling() async {
    if (_isPolling) return;
    _isPolling = true;
    _pollForEvents();
  }

  void stopPolling() {
    _isPolling = false;
  }

  Future<void> _pollForEvents() async {
    while (_isPolling) {
      try {
        final streamedResponse = await http.Client().send(
            await createGetRequest('$_baseUrl/poll?e=${_currentEventId + 1}'));
        final response = await http.Response.fromStream(streamedResponse);
        await _auth.handleResponse(response);

        if (response.statusCode == 200) {
          final data = json.decode(response.body);
          _currentEventId = data['id'];
          _currentServerVersion = data['version'];
          notifyListeners();
        } else if (response.statusCode != 304) {
          // If not a "Not Modified" response, wait a bit before retrying
          await Future.delayed(const Duration(seconds: 1));
        }
      } catch (e) {
        // On error, wait a bit before retrying
        await Future.delayed(const Duration(seconds: 1));
      }
    }
  }

  String get serverVersion => _currentServerVersion;
}
