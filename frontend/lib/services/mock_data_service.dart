import 'package:flutter/foundation.dart';
import '../models/task.dart';
import '../models/task_list.dart';
import 'package:intl/intl.dart';

class MockDataService extends ChangeNotifier {
  static final MockDataService _instance = MockDataService._internal();
  
  factory MockDataService() {
    return _instance;
  }
  
  MockDataService._internal() {
    _initializeData();
  }
  final List<TaskList> _taskLists = [];
  
  void _initializeData() {
    _taskLists.addAll([
      TaskList(
        id: 1,
        title: 'Project Planning',
        category: TaskListCategory.template,
        tasks: [
          Task(
            id: 1,
            taskListId: 1,
            title: 'Complete Project Proposal',
            comments: ['Initial draft done', 'Needs review'],
            dueDate: DateTime.now().add(const Duration(days: 7)),
            isCompleted: false,
          ),
          Task(
            id: 3,
            taskListId: 1,
            title: 'Update Documentation',
            comments: ['Started updating API docs'],
            parentTaskId: 1,
            isCompleted: false,
          ),
        ],
      ),
      TaskList(
        id: 2,
        title: 'Daily Tasks',
        category: TaskListCategory.toDoList,
        tasks: [
          Task(
            id: 2,
            taskListId: 2,
            title: 'Review Team Updates',
            comments: ['Team A submitted', 'Waiting for Team B'],
            dueDate: DateTime.now().add(const Duration(days: 2)),
            isCompleted: false,
            completedAt: null,
          ),
        ],
      ),
    ]);
  }

  List<TaskList> getTaskLists() {
    return List.unmodifiable(_taskLists);
  }

  TaskList getTaskListById(int taskListId) {
    return _taskLists.firstWhere(
      (list) => list.id == taskListId,
      orElse: () => throw Exception('TaskList not found'),
    );
  }

  Task? getTaskById(int taskListId, int taskId) {
    final taskList = _taskLists.firstWhere(
      (list) => list.id == taskListId,
      orElse: () => throw Exception('TaskList not found'),
    );
    
    return taskList.tasks.firstWhere(
      (task) => task.id == taskId,
      orElse: () => throw Exception('Task not found'),
    );
  }

  void markTaskComplete(int taskListId, int taskId, bool complete) {
    final taskListIndex = _taskLists.indexWhere((list) => list.id == taskListId);
    if (taskListIndex == -1) throw Exception('TaskList not found');

    final taskIndex = _taskLists[taskListIndex].tasks.indexWhere((task) => task.id == taskId);
    if (taskIndex == -1) throw Exception('Task not found');

    final oldTask = _taskLists[taskListIndex].tasks[taskIndex];
    final newTask = Task(
      id: oldTask.id,
      title: oldTask.title,
      taskListId: oldTask.taskListId,
      comments: oldTask.comments,
      dueDate: oldTask.dueDate,
      parentTaskId: oldTask.parentTaskId,
      isCompleted: complete,
      completedAt: complete ? DateTime.now() : null,
    );

    final updatedTasks = List<Task>.from(_taskLists[taskListIndex].tasks);
    updatedTasks[taskIndex] = newTask;

    _taskLists[taskListIndex] = TaskList(
      id: _taskLists[taskListIndex].id,
      title: _taskLists[taskListIndex].title,
      category: _taskLists[taskListIndex].category,
      tasks: updatedTasks,
    );
    
    notifyListeners();
  }
}
