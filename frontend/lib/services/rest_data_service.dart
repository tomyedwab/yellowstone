import 'dart:convert';
import 'dart:math';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import '../models/task.dart';
import '../models/task_list.dart';

// Utility function to create a request for a url without following redirects
http.Request CreateRequest(String url) {
  return http.Request('GET', Uri.parse(url))
    ..followRedirects = false
    ..maxRedirects = 0
    ..connectionTimeout = const Duration(seconds: 10);
}

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
        // return 'http://10.0.2.2:8334/api';
        return 'https://yellowstone.tomyedwab.com/api';
      }
    }
  }
  static final RestDataService _instance = RestDataService._internal();
  
  factory RestDataService() {
    return _instance;
  }
  
  RestDataService._internal() {
    _random = Random();
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
    final streamedResponse = await http.Client().send(CreateRequest('$baseUrl/tasklist/all'));
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
    } else {
      throw Exception('Failed to load task lists');
    }
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
    final listStreamedResponse = await http.Client().send(CreateRequest('$baseUrl/tasklist/get?id=$taskListId'));
    final listResponse = await http.Response.fromStream(listStreamedResponse);
    _handleResponse(listResponse);
    if (listResponse.statusCode != 200) {
      throw Exception('Failed to load task list: ${listResponse.body}');
    }

    // Get the tasks for this list
    final tasksStreamedResponse = await http.Client().send(CreateRequest('$baseUrl/task/list?listId=$taskListId'));
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
    final streamedResponse = await http.Client().send(CreateRequest('$baseUrl/task/list?listId=$taskListId'));
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
    final clientId = _generateClientId();
    final event = {
      'type': 'yellowstone:updateTaskTitle',
      'taskId': taskId,
      'title': title,
    };
    
    final response = await http.post(
      Uri.parse('$baseUrl/publish?cid=$clientId'),
      headers: {'Content-Type': 'application/json'},
      body: json.encode(event),
    );
    
    if (response.statusCode != 200) {
      throw Exception('Failed to update task title: ${response.body}');
    }
    
    notifyListeners();
  }

  Future<void> updateTaskDueDate(int taskId, DateTime? dueDate) async {
    final clientId = _generateClientId();
    final event = {
      'type': 'yellowstone:updateTaskDueDate',
      'taskId': taskId,
      'dueDate': dueDate?.toUtc().toIso8601String(),
    };
    
    final response = await http.post(
      Uri.parse('$baseUrl/publish?cid=$clientId'),
      headers: {'Content-Type': 'application/json'},
      body: json.encode(event),
    );
    
    if (response.statusCode != 200) {
      throw Exception('Failed to update task due date: ${response.body}');
    }
    
    notifyListeners();
  }

  void deleteTask(int taskListId, int taskId) {
    // TODO: Implement REST call
    notifyListeners();
  }

  Future<void> createTask(int taskListId, String title) async {
    final clientId = _generateClientId();
    final event = {
      'type': 'yellowstone:addTask',
      'title': title,
      'taskListId': taskListId,
      'dueDate': null,
    };
    
    final response = await http.post(
      Uri.parse('$baseUrl/publish?cid=$clientId'),
      headers: {'Content-Type': 'application/json'},
      body: json.encode(event),
    );
    
    if (response.statusCode != 200) {
      throw Exception('Failed to create task: ${response.body}');
    }
    
    notifyListeners();
  }

  void reorderTasks(int taskListId, int oldIndex, int newIndex) {
    // TODO: Implement REST call
    notifyListeners();
  }

  Future<void> markTaskComplete(int taskId, bool complete) async {
    final clientId = _generateClientId();
    final event = {
      'type': 'yellowstone:updateTaskCompleted',
      'taskId': taskId,
      'completedAt': complete ? DateTime.now().toUtc().toIso8601String() : null,
    };
    
    final response = await http.post(
      Uri.parse('$baseUrl/publish?cid=$clientId'),
      headers: {'Content-Type': 'application/json'},
      body: json.encode(event),
    );
    
    if (response.statusCode != 200) {
      throw Exception('Failed to update task completion: ${response.body}');
    }
    
    notifyListeners();
  }

  void reorderTaskLists(int taskListId, int? afterTaskListId) {
    // TODO: Implement REST call
    notifyListeners();
  }

  Future<void> archiveTaskList(int taskListId) async {
    final clientId = _generateClientId();
    final event = {
      'type': 'yellowstone:updateTaskListArchived',
      'listId': taskListId,
      'archived': true,
    };
    
    final response = await http.post(
      Uri.parse('$baseUrl/publish?cid=$clientId'),
      headers: {'Content-Type': 'application/json'},
      body: json.encode(event),
    );
    
    if (response.statusCode != 200) {
      throw Exception('Failed to archive task list: ${response.body}');
    }
    
    notifyListeners();
  }

  Future<void> unarchiveTaskList(int taskListId) async {
    final clientId = _generateClientId();
    final event = {
      'type': 'yellowstone:updateTaskListArchived',
      'listId': taskListId,
      'archived': false,
    };
    
    final response = await http.post(
      Uri.parse('$baseUrl/publish?cid=$clientId'),
      headers: {'Content-Type': 'application/json'},
      body: json.encode(event),
    );
    
    if (response.statusCode != 200) {
      throw Exception('Failed to unarchive task list: ${response.body}');
    }
    
    notifyListeners();
  }

  Future<void> createTaskList(String title, TaskListCategory category) async {
    final clientId = _generateClientId();
    final event = {
      'type': 'yellowstone:addTaskList',
      'title': title,
      'category': category.name.toLowerCase(),
      'archived': false,
    };
    
    final response = await http.post(
      Uri.parse('$baseUrl/publish?cid=$clientId'),
      headers: {'Content-Type': 'application/json'},
      body: json.encode(event),
    );
    
    if (response.statusCode != 200) {
      throw Exception('Failed to create task list: ${response.body}');
    }
    
    notifyListeners();
  }

  Future<void> updateTaskListTitle(int taskListId, String title) async {
    final clientId = _generateClientId();
    final event = {
      'type': 'yellowstone:updateTaskListTitle',
      'listId': taskListId,
      'title': title,
    };
    
    final response = await http.post(
      Uri.parse('$baseUrl/publish?cid=$clientId'),
      headers: {'Content-Type': 'application/json'},
      body: json.encode(event),
    );
    
    if (response.statusCode != 200) {
      throw Exception('Failed to update task list: ${response.body}');
    }
    
    notifyListeners();
  }
}
