import 'dart:convert';
import 'dart:math';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:webview_cookie_jar/webview_cookie_jar.dart';
import '../models/task.dart';
import '../models/task_list.dart';
import '../models/task_history.dart';

typedef LoginRedirectHandler = void Function(String loginUrl);

typedef InFlightRequest = (String clientId, Map<String, Object?> event);

class RestDataService extends ChangeNotifier {
  static String get baseUrl {
    if (kReleaseMode) {
      return 'https://yellowstone.tomyedwab.com/api';
    } else {
      if (kIsWeb) {
        return 'http://localhost:8334/api';
      } else {
        // Android emulator needs special localhost address
        return 'http://10.0.2.2:8334/api';
      }
    }
  }
  static bool get needsWebCookies {
    if (!kReleaseMode) {
      return false;
    }
    if (kIsWeb) {
      return false;
    }
    // On android in production, we need to use login cookies we get from the
    // webview
    return true;
  }
  static final RestDataService _instance = RestDataService._internal();
  
  factory RestDataService() {
    return _instance;
  }
  
  int _currentEventId = 0;
  bool _isPolling = false;
  bool get isPolling => _isPolling;

  // Chronologically ordered list of <clientId, event> tuples
  final List<InFlightRequest> _inFlightRequests = [];

  // Cache storage
  final Map<String, String> _responseCache = {};
  int _lastCacheEventId = 0;

  void _clearCacheIfEventChanged() {
    if (_lastCacheEventId != _currentEventId) {
      _responseCache.clear();
      _lastCacheEventId = _currentEventId;
    }
  }

  Future<http.Response> _getCachedResponse(String url) async {
    _clearCacheIfEventChanged();
    
    final cachedResponse = _responseCache[url];
    if (cachedResponse != null) {
      return http.Response(cachedResponse, 200);
    }

    final streamedResponse = await http.Client().send(await createGetRequest(url));
    final response = await http.Response.fromStream(streamedResponse);
    _handleResponse(response);

    if (response.statusCode == 200) {
      _responseCache[url] = response.body;
    }
    
    return response;
  }

  RestDataService._internal() {
    _random = Random();
    // Start polling automatically
    startPolling();
  }

  // Utility function to create a request for a url without following redirects
  Future<http.Request> createGetRequest(String url) async {
    final request = http.Request('GET', Uri.parse(url))
      ..followRedirects = false
      ..maxRedirects = 0;
    if (needsWebCookies) {
      final cookies = await WebViewCookieJar.cookieJar.loadForRequest(request.url);
      request.headers['Cookie'] = cookies.map((c) => '${c.name}=${c.value}').join('; ');
    } else {
      // Use dev secret
      request.headers['X-CloudFront-Secret'] = '123';
    }
    return request;
  }

  Future<http.StreamedResponse> doPublishRequest(Map<String, Object?> event) async {
    final clientId = _generateClientId();
    _inFlightRequests.add((clientId, event));
    notifyListeners();

    final request = http.Request('POST', Uri.parse('$baseUrl/publish?cid=$clientId'))
      ..followRedirects = false
      ..maxRedirects = 0;
    if (needsWebCookies) {
      final cookies = await WebViewCookieJar.cookieJar.loadForRequest(request.url);
      request.headers['Cookie'] = cookies.map((c) => '${c.name}=${c.value}').join('; ');
    } else {
      // Use dev secret
      request.headers['X-CloudFront-Secret'] = '123';
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

  void setLoginRedirectHandler(LoginRedirectHandler handler) {
    _loginRedirectHandler = handler;
  }

  void _handleResponse(http.Response response) {
    if (response.statusCode == 302) {
      final location = response.headers['location'];
      if (location != null && _loginRedirectHandler != null) {
        _loginRedirectHandler!(location);
        throw Exception('Redirecting to login');
      }
    }
  }
  
  late final Random _random;
  final Map<int, Task> _tasks = {};
  LoginRedirectHandler? _loginRedirectHandler;
  
  String _generateClientId() {
    return List.generate(16, (_) => _random.nextInt(16).toRadixString(16)).join();
  }

  Future<List<TaskList>> getTaskLists({bool includeArchived = false}) async {
    final response = await _getCachedResponse('$baseUrl/tasklist/all');
    
    if (response.statusCode != 200) {
      throw Exception('Failed to load task lists');
    }
    
    final Map<String, dynamic> data = json.decode(response.body);
    final List<dynamic> taskLists = data['TaskLists'];

    final inFlightReorderedTaskLists = _inFlightRequests.where((request) => request.$2['type'] == 'yellowstone:reorderTaskList');
    for (final inFlightRequest in inFlightReorderedTaskLists) {
      final oldTaskListId = inFlightRequest.$2['listId'];
      final afterTaskListId = inFlightRequest.$2['afterListId'];
      final oldTaskListIndex = taskLists.indexWhere((taskList) => taskList['Id'] == oldTaskListId);
      final afterTaskListIndex = taskLists.indexWhere((taskList) => taskList['Id'] == afterTaskListId);
      if (oldTaskListIndex != -1) {
        final taskList = taskLists[oldTaskListIndex];
        taskLists.removeAt(oldTaskListIndex);
        taskLists.insert(afterTaskListIndex+1, taskList);
      }
    }
      
    return taskLists.map((json) => TaskList(
      id: json['Id'],
      title: json['Title'],
      category: _categoryFromString(json['Category']),
      archived: json['Archived'],
    )).toList();
  }

  Future<List<TaskListMetadata>> getTaskListMetadata() async {
    final response = await _getCachedResponse('$baseUrl/tasklist/metadata');
    
    if (response.statusCode != 200) {
      throw Exception('Failed to load task list metadata');
    }
    final List<dynamic> data = json.decode(response.body);
    return data.map((json) => TaskListMetadata(
      id: json['ListId'],
      total: json['Total'],
      completed: json['Completed'],
    )).toList();
  }

  TaskListCategory _categoryFromString(String category) {
    switch (category.toLowerCase()) {
      case 'todolist':
        return TaskListCategory.toDoList;
      case 'template':
        return TaskListCategory.template;
      default:
        throw Exception('Unknown category: $category');
    }
    }

  Future<TaskList> getTaskListById(int taskListId) async {
    // Get the task list details
    final listResponse = await _getCachedResponse('$baseUrl/tasklist/get?id=$taskListId');
    
    if (listResponse.statusCode != 200) {
      throw Exception('Failed to load task list: ${listResponse.body}');
    }

    // Get the tasks for this list
    final tasksResponse = await _getCachedResponse('$baseUrl/task/list?listId=$taskListId');
    
    if (tasksResponse.statusCode != 200) {
      throw Exception('Failed to load tasks: ${tasksResponse.body}');
    }

    final Map<String, dynamic> listJson = jsonDecode(listResponse.body);
    final Map<String, dynamic> tasksJson = jsonDecode(tasksResponse.body);
    final List<dynamic> tasksData = tasksJson['Tasks'];

    // Convert task JSON data to Task objects and store them
    for (var taskData in tasksData) {
      final task = Task(
        id: taskData['Id'],
        title: taskData['Title'],
        taskListId: taskListId,
        dueDate: taskData['DueDate'] != null ? DateTime.parse(taskData['DueDate']) : null,
        isCompleted: taskData['CompletedAt'] != null,
        completedAt: taskData['CompletedAt'] != null ? DateTime.parse(taskData['CompletedAt']) : null,
      );
      _tasks[task.id] = task;
    }

    return TaskList(
      id: listJson['Id'],
      title: listJson['Title'],
      category: _categoryFromString(listJson['Category']),
      archived: listJson['Archived'],
      taskIds: tasksData.map<int>((task) => task['Id'] as int).toList(),
    );
  }

  Task getTaskById(int taskId) {
    final task = _tasks[taskId];
    if (task == null) {
      throw Exception('Task not found');
    }
    return task;
  }

  Future<List<Task>> getTasksForList(int taskListId) async {
    final response = await _getCachedResponse('$baseUrl/task/list?listId=$taskListId');
    
    if (response.statusCode == 200) {
      final Map<String, dynamic> data = jsonDecode(response.body);
      final List<dynamic> tasksData = data['Tasks'];

      final inFlightCompletedTasks = _inFlightRequests.where((request) => request.$2['type'] == 'yellowstone:updateTaskCompleted');
      final inFlightRenamedTasks = _inFlightRequests.where((request) => request.$2['type'] == 'yellowstone:updateTaskTitle');
      final inFlightReorderedTasks = _inFlightRequests.where((request) => request.$2['type'] == 'yellowstone:reorderTasks' && request.$2['taskListId'] == taskListId);

      for (final inFlightRequest in inFlightReorderedTasks) {
        final oldTaskId = inFlightRequest.$2['oldTaskId'];
        final afterTaskId = inFlightRequest.$2['afterTaskId'];
        // Reorder the tasks in tasksData
        final oldTaskIndex = tasksData.indexWhere((task) => task['Id'] == oldTaskId);
        final afterTaskIndex = tasksData.indexWhere((task) => task['Id'] == afterTaskId);
        if (oldTaskIndex != -1) {
          final taskData = tasksData[oldTaskIndex];
          tasksData.removeAt(oldTaskIndex);
          tasksData.insert(afterTaskIndex+1, taskData);
        }
      }

      final tasks = tasksData.map((taskData) {
        var title = taskData['Title'];
        for (final inFlightRequest in inFlightRenamedTasks) {
          if (inFlightRequest.$2['taskId'] == taskData['Id']) {
            title = inFlightRequest.$2['title'];
          }
        }
        var completedAt = taskData['CompletedAt'];
        for (final inFlightRequest in inFlightCompletedTasks) {
          if (inFlightRequest.$2['taskId'] == taskData['Id']) {
            completedAt = inFlightRequest.$2['completedAt'];
          }
        }
        final task = Task(
          id: taskData['Id'],
          title: title,
          taskListId: taskListId,
          dueDate: taskData['DueDate'] != null ? DateTime.parse(taskData['DueDate']) : null,
          isCompleted: completedAt != null,
          completedAt: completedAt != null ? DateTime.parse(completedAt) : null,
        );
        _tasks[task.id] = task;
        return task;
      }).toList();
      
      return tasks;
    } else {
      throw Exception('Failed to load tasks: ${response.body}');
    }
  }

  Future<List<TaskRecentComment>> getTaskListRecentComments(int taskListId) async {
    final response = await _getCachedResponse('$baseUrl/tasklist/recent_comments?listId=$taskListId');
    if (response.statusCode != 200) {
      throw Exception('Failed to load task list recent comments');
    }
    final List<dynamic> data = jsonDecode(response.body);
    return data.map((json) => TaskRecentComment(
      taskId: json['TaskId'],
      userComment: json['UserComment'],
      createdAt: DateTime.parse(json['CreatedAt']),
    )).toList();
  }

  Future<void> updateTaskTitle(int taskId, String title) async {
    await doPublishRequest({
      'type': 'yellowstone:updateTaskTitle',
      'taskId': taskId,
      'title': title,
    });
  }

  Future<void> updateTaskDueDate(int taskId, DateTime? dueDate) async {
    await doPublishRequest({
      'type': 'yellowstone:updateTaskDueDate',
      'taskId': taskId,
      'dueDate': dueDate?.toUtc().toIso8601String(),
    });
  }

  Future<void> deleteTask(int taskListId, int taskId) async {
    await doPublishRequest({
      'type': 'yellowstone:deleteTask',
      'taskId': taskId,
    });
  }

  Future<void> createTask(int taskListId, String title) async {
    await doPublishRequest({
      'type': 'yellowstone:addTask',
      'title': title,
      'taskListId': taskListId,
      'dueDate': null,
    });
  }

  Future<void> reorderTasks(int taskListId, int oldTaskId, int? afterTaskId) async {
    // TODO: While the event is pending, apply the reordering locally
    await doPublishRequest({
      'type': 'yellowstone:reorderTasks',
      'taskListId': taskListId,
      'oldTaskId': oldTaskId,
      'afterTaskId': afterTaskId,
    });
  }

  Future<void> markTaskComplete(int taskId, bool complete) async {
    await doPublishRequest({
      'type': 'yellowstone:updateTaskCompleted',
      'taskId': taskId,
      'completedAt': complete ? DateTime.now().toUtc().toIso8601String() : null,
    });
  }

  Future<void> reorderTaskList(int taskListId, int? afterTaskListId) async {
    // TODO: While the event is pending, apply the reordering locally
    await doPublishRequest({
      'type': 'yellowstone:reorderTaskList',
      'listId': taskListId,
      'afterListId': afterTaskListId,
    });
  }

  Future<void> archiveTaskList(int taskListId) async {
    await doPublishRequest({
      'type': 'yellowstone:updateTaskListArchived',
      'listId': taskListId,
      'archived': true,
    });
  }

  Future<void> unarchiveTaskList(int taskListId) async {
    await doPublishRequest({
      'type': 'yellowstone:updateTaskListArchived',
      'listId': taskListId,
      'archived': false,
    });
  }

  Future<void> createTaskList(String title, TaskListCategory category) async {
    await doPublishRequest({
      'type': 'yellowstone:addTaskList',
      'title': title,
      'category': category.name.toLowerCase(),
      'archived': false,
    });
  }

  Future<void> updateTaskListTitle(int taskListId, String title) async {
    await doPublishRequest({
      'type': 'yellowstone:updateTaskListTitle',
      'listId': taskListId,
      'title': title,
    });
  }

  Future<List<TaskHistory>> getTaskHistory(int taskId) async {
    final response = await _getCachedResponse('$baseUrl/task/history?id=$taskId');
    
    if (response.statusCode != 200) {
      throw Exception('Failed to load task history');
    }
    
    final Map<String, dynamic> data = json.decode(response.body);
    final List<dynamic> history = data['history'];
    
    return history.map((json) => TaskHistory.fromJson(json)).toList();
  }

  Future<void> addTaskComment(int taskId, String comment) async {
    await doPublishRequest({
      'type': 'yellowstone:addTaskComment',
      'taskId': taskId,
      'userComment': comment,
    });
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
          await createGetRequest('$baseUrl/poll?e=${_currentEventId + 1}')
        );
        final response = await http.Response.fromStream(streamedResponse);

        if (response.statusCode == 200) {
          final data = json.decode(response.body);
          _currentEventId = data['id'];
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

  Future<List<TaskList>> getAllTaskLists() async {
    final response = await _getCachedResponse('$baseUrl/tasklist/all');
    
    if (response.statusCode != 200) {
      throw Exception('Failed to load task lists');
    }
    
    final Map<String, dynamic> data = json.decode(response.body);
    final List<dynamic> taskLists = data['TaskLists'];
      
    return taskLists.map((json) => TaskList(
      id: json['Id'],
      title: json['Title'],
      category: _categoryFromString(json['Category']),
      archived: json['Archived'],
    )).toList();
  }

  Future<void> moveTasksToList(Set<int> taskIds, int newListId) async {
    await doPublishRequest({
      'type': 'yellowstone:moveTasks',
      'taskIds': taskIds.toList(),
      'newListId': newListId,
    });
    _currentEventId++;
    notifyListeners();
  }

  Future<void> copyTasksToList(Set<int> taskIds, int newListId) async {
    await doPublishRequest({
      'type': 'yellowstone:copyTasks',
      'taskIds': taskIds.toList(),
      'newListId': newListId,
    });
    _currentEventId++;
    notifyListeners();
  }
}
