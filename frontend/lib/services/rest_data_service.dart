import 'dart:convert';
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
  
  RestDataService._internal();

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

  TaskList getTaskListById(int taskListId) {
    // TODO: Implement REST call
    throw Exception('Not implemented');
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

  void archiveTaskList(int taskListId) {
    // TODO: Implement REST call
    notifyListeners();
  }

  void unarchiveTaskList(int taskListId) {
    // TODO: Implement REST call
    notifyListeners();
  }

  void createTaskList(String title, TaskListCategory category) {
    // TODO: Implement REST call
    notifyListeners();
  }

  void updateTaskList(int taskListId, {String? title}) {
    // TODO: Implement REST call
    notifyListeners();
  }
}
