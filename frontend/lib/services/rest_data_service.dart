import 'dart:convert';
import 'dart:math';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import '../models/task.dart';
import '../models/task_list.dart';

const String baseUrl = 'http://localhost:8334';

class RestDataService extends ChangeNotifier {
  static final RestDataService _instance = RestDataService._internal();
  
  factory RestDataService() {
    return _instance;
  }
  
  RestDataService._internal() {
    _random = Random();
  }
  
  late final Random _random;
  
  String _generateClientId() {
    return List.generate(16, (_) => _random.nextInt(16).toRadixString(16)).join();
  }

  // Mock implementations for now
  Future<List<TaskList>> getTaskLists({bool includeArchived = false}) async {
    final response = await http.get(Uri.parse('$baseUrl/tasklist/all'));
    
    if (response.statusCode == 200) {
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
    final listResponse = await http.get(Uri.parse('$baseUrl/tasklist/get?id=$taskListId'));
    
    if (listResponse.statusCode != 200) {
      throw Exception('Failed to load task list: ${listResponse.body}');
    }

    // Get the tasks for this list
    final tasksResponse = await http.get(Uri.parse('$baseUrl/task/list?listId=$taskListId'));
    
    if (tasksResponse.statusCode != 200) {
      throw Exception('Failed to load tasks: ${tasksResponse.body}');
    }

    final Map<String, dynamic> listJson = jsonDecode(listResponse.body);
    final Map<String, dynamic> tasksJson = jsonDecode(tasksResponse.body);
    final List<dynamic> tasks = tasksJson['Tasks'];

    return TaskList(
      id: listJson['Id'],
      title: listJson['Title'],
      category: _categoryFromString(listJson['Category']),
      archived: listJson['Archived'],
      taskIds: tasks.map<int>((task) => task['Id'] as int).toList(),
    );
  }

  Task getTaskById(int taskId) {
    // TODO: Implement REST call
    throw Exception('Not implemented');
  }

  List<Task> getTasksForList(int taskListId) {
    // TODO: Implement REST call
    return [];
  }

  void updateTask(int taskListId, int taskId, {
    String? title,
    DateTime? dueDate,
    bool? isCompleted,
  }) {
    // TODO: Implement REST call
    notifyListeners();
  }

  void deleteTask(int taskListId, int taskId) {
    // TODO: Implement REST call
    notifyListeners();
  }

  void createTask(int taskListId, String title) {
    // TODO: Implement REST call
    notifyListeners();
  }

  void reorderTasks(int taskListId, int oldIndex, int newIndex) {
    // TODO: Implement REST call
    notifyListeners();
  }

  void markTaskComplete(int taskId, bool complete) {
    // TODO: Implement REST call
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
