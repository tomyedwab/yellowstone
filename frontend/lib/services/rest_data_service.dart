import 'dart:convert';
import 'dart:math';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:webview_cookie_jar/webview_cookie_jar.dart';
import '../models/task.dart';
import '../models/task_list.dart';

typedef LoginRedirectHandler = void Function(String loginUrl);

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
  
  RestDataService._internal() {
    _random = Random();
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
    request.body = json.encode(event);
    final response = await http.Client().send(request);
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

  // Mock implementations for now
  Future<List<TaskList>> getTaskLists({bool includeArchived = false}) async {
    final streamedResponse = await http.Client().send(await createGetRequest('$baseUrl/tasklist/all'));
    final response = await http.Response.fromStream(streamedResponse);
    _handleResponse(response);
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
    final listStreamedResponse = await http.Client().send(await createGetRequest('$baseUrl/tasklist/get?id=$taskListId'));
    final listResponse = await http.Response.fromStream(listStreamedResponse);
    _handleResponse(listResponse);
    if (listResponse.statusCode != 200) {
      throw Exception('Failed to load task list: ${listResponse.body}');
    }

    // Get the tasks for this list
    final tasksStreamedResponse = await http.Client().send(await createGetRequest('$baseUrl/task/list?listId=$taskListId'));
    final tasksResponse = await http.Response.fromStream(tasksStreamedResponse);
    _handleResponse(tasksResponse);
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
    final streamedResponse = await http.Client().send(await createGetRequest('$baseUrl/task/list?listId=$taskListId'));
    final response = await http.Response.fromStream(streamedResponse);
    _handleResponse(response);
    if (response.statusCode == 200) {
      final Map<String, dynamic> data = jsonDecode(response.body);
      final List<dynamic> tasksData = data['Tasks'];
      
      final tasks = tasksData.map((taskData) {
        final task = Task(
          id: taskData['Id'],
          title: taskData['Title'],
          taskListId: taskListId,
          dueDate: taskData['DueDate'] != null ? DateTime.parse(taskData['DueDate']) : null,
          isCompleted: taskData['CompletedAt'] != null,
          completedAt: taskData['CompletedAt'] != null ? DateTime.parse(taskData['CompletedAt']) : null,
        );
        _tasks[task.id] = task;
        return task;
      }).toList();
      
      return tasks;
    } else {
      throw Exception('Failed to load tasks: ${response.body}');
    }
  }

  Future<void> updateTaskTitle(int taskId, String title) async {
    await doPublishRequest({
      'type': 'yellowstone:updateTaskTitle',
      'taskId': taskId,
      'title': title,
    });
    notifyListeners();
  }

  Future<void> updateTaskDueDate(int taskId, DateTime? dueDate) async {
    await doPublishRequest({
      'type': 'yellowstone:updateTaskDueDate',
      'taskId': taskId,
      'dueDate': dueDate?.toUtc().toIso8601String(),
    });
    notifyListeners();
  }

  Future<void> deleteTask(int taskListId, int taskId) async {
    await doPublishRequest({
      'type': 'yellowstone:deleteTask',
      'taskId': taskId,
    });
    notifyListeners();
  }

  Future<void> createTask(int taskListId, String title) async {
    await doPublishRequest({
      'type': 'yellowstone:addTask',
      'title': title,
      'taskListId': taskListId,
      'dueDate': null,
    });
    notifyListeners();
  }

  Future<void> reorderTasks(int taskListId, int oldTaskId, int? afterTaskId) async {
    await doPublishRequest({
      'type': 'yellowstone:reorderTasks',
      'taskListId': taskListId,
      'oldTaskId': oldTaskId,
      'afterTaskId': afterTaskId,
    });
    notifyListeners();
  }

  Future<void> markTaskComplete(int taskId, bool complete) async {
    await doPublishRequest({
      'type': 'yellowstone:updateTaskCompleted',
      'taskId': taskId,
      'completedAt': complete ? DateTime.now().toUtc().toIso8601String() : null,
    });
    notifyListeners();
  }

  Future<void> reorderTaskLists(int taskListId, int? afterTaskListId) async {
    // TODO: Implement REST call
    notifyListeners();
  }

  Future<void> archiveTaskList(int taskListId) async {
    await doPublishRequest({
      'type': 'yellowstone:updateTaskListArchived',
      'listId': taskListId,
      'archived': true,
    });
    notifyListeners();
  }

  Future<void> unarchiveTaskList(int taskListId) async {
    await doPublishRequest({
      'type': 'yellowstone:updateTaskListArchived',
      'listId': taskListId,
      'archived': false,
    });
    notifyListeners();
  }

  Future<void> createTaskList(String title, TaskListCategory category) async {
    await doPublishRequest({
      'type': 'yellowstone:addTaskList',
      'title': title,
      'category': category.name.toLowerCase(),
      'archived': false,
    });
    notifyListeners();
  }

  Future<void> updateTaskListTitle(int taskListId, String title) async {
    await doPublishRequest({
      'type': 'yellowstone:updateTaskListTitle',
      'listId': taskListId,
      'title': title,
    });
    notifyListeners();
  }
}
