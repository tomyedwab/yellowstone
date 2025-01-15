import 'package:flutter/foundation.dart';
import '../models/task.dart';
import '../models/task_list.dart';

class RestDataService extends ChangeNotifier {
  static final RestDataService _instance = RestDataService._internal();
  
  factory RestDataService() {
    return _instance;
  }
  
  RestDataService._internal();

  // Mock implementations for now
  List<TaskList> getTaskLists({bool includeArchived = false}) {
    // TODO: Implement REST call
    return [];
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
