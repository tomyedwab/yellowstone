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
  final Map<int, Task> _tasks = {};
  
  void _initializeData() {
    // First create all tasks
    final task1 = Task(
      id: 1,
      taskListId: 1,
      title: 'Complete Project Proposal',
      comments: ['Initial draft done', 'Needs review'],
      dueDate: DateTime.now().add(const Duration(days: 7)),
      isCompleted: false,
    );
    
    final task2 = Task(
      id: 2,
      taskListId: 2,
      title: 'Review Team Updates',
      comments: ['Team A submitted', 'Waiting for Team B'],
      dueDate: DateTime.now().add(const Duration(days: 2)),
      isCompleted: false,
      completedAt: null,
    );
    
    final task3 = Task(
      id: 3,
      taskListId: 1,
      title: 'Update Documentation',
      comments: ['Started updating API docs'],
      parentTaskId: 1,
      isCompleted: false,
    );

    // Add tasks to map
    _tasks[task1.id] = task1;
    _tasks[task2.id] = task2;
    _tasks[task3.id] = task3;

    // Create task lists with task IDs
    _taskLists.addAll([
      TaskList(
        id: 1,
        title: 'Project Planning',
        category: TaskListCategory.template,
        taskIds: [1, 3],
      ),
      TaskList(
        id: 2,
        title: 'Daily Tasks',
        category: TaskListCategory.toDoList,
        taskIds: [2],
      ),
    ]);
  }

  List<TaskList> getTaskLists({bool includeArchived = false}) {
    return List.unmodifiable(
      _taskLists.where((list) => includeArchived || !list.archived).toList(),
  }

  TaskList getTaskListById(int taskListId) {
    return _taskLists.firstWhere(
      (list) => list.id == taskListId,
      orElse: () => throw Exception('TaskList not found'),
    );
  }

  Task getTaskById(int taskId) {
    final task = _tasks[taskId];
    if (task == null) throw Exception('Task not found');
    return task;
  }

  List<Task> getTasksForList(int taskListId) {
    final taskList = getTaskListById(taskListId);
    return taskList.taskIds.map((id) => getTaskById(id)).toList();
  }

  void updateTask(int taskListId, int taskId, {
    String? title,
    DateTime? dueDate,
    bool? isCompleted,
  }) {
    final taskListIndex = _taskLists.indexWhere((list) => list.id == taskListId);
    if (taskListIndex == -1) throw Exception('TaskList not found');

    final oldTask = getTaskById(taskId);
    final newTask = Task(
      id: oldTask.id,
      title: title ?? oldTask.title,
      taskListId: oldTask.taskListId,
      comments: oldTask.comments,
      dueDate: dueDate,
      parentTaskId: oldTask.parentTaskId,
      isCompleted: isCompleted ?? oldTask.isCompleted,
      completedAt: isCompleted == true ? DateTime.now() : oldTask.completedAt,
    );

    _tasks[taskId] = newTask;
    
    notifyListeners();
  }

  void deleteTask(int taskListId, int taskId) {
    final taskListIndex = _taskLists.indexWhere((list) => list.id == taskListId);
    if (taskListIndex == -1) throw Exception('TaskList not found');

    if (!_tasks.containsKey(taskId)) throw Exception('Task not found');
    
    final updatedTaskIds = List<int>.from(_taskLists[taskListIndex].taskIds);
    updatedTaskIds.remove(taskId);

    _taskLists[taskListIndex] = TaskList(
      id: _taskLists[taskListIndex].id,
      title: _taskLists[taskListIndex].title,
      category: _taskLists[taskListIndex].category,
      taskIds: updatedTaskIds,
    );

    _tasks.remove(taskId);
    
    notifyListeners();
  }

  int _getNextTaskId() {
    return _tasks.keys
        .fold(0, (max, id) => id > max ? id : max) + 1;
  }

  void createTask(int taskListId, String title) {
    final taskListIndex = _taskLists.indexWhere((list) => list.id == taskListId);
    if (taskListIndex == -1) throw Exception('TaskList not found');

    final newTask = Task(
      id: _getNextTaskId(),
      title: title,
      taskListId: taskListId,
    );

    _tasks[newTask.id] = newTask;
    
    final updatedTaskIds = List<int>.from(_taskLists[taskListIndex].taskIds)
      ..add(newTask.id);

    _taskLists[taskListIndex] = TaskList(
      id: _taskLists[taskListIndex].id,
      title: _taskLists[taskListIndex].title,
      category: _taskLists[taskListIndex].category,
      taskIds: updatedTaskIds,
    );
    
    notifyListeners();
  }

  void reorderTasks(int taskListId, int oldIndex, int newIndex) {
    final taskListIndex = _taskLists.indexWhere((list) => list.id == taskListId);
    if (taskListIndex == -1) throw Exception('TaskList not found');

    final taskIds = List<int>.from(_taskLists[taskListIndex].taskIds);
    if (newIndex > oldIndex) {
      newIndex -= 1;
    }
    final taskId = taskIds.removeAt(oldIndex);
    taskIds.insert(newIndex, taskId);

    _taskLists[taskListIndex] = TaskList(
      id: _taskLists[taskListIndex].id,
      title: _taskLists[taskListIndex].title,
      category: _taskLists[taskListIndex].category,
      taskIds: taskIds,
    );
    
    notifyListeners();
  }

  void markTaskComplete(int taskId, bool complete) {
    final oldTask = getTaskById(taskId);
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

    _tasks[taskId] = newTask;
    
    notifyListeners();
  }

  void reorderTaskLists(int oldIndex, int newIndex) {
    if (oldIndex < newIndex) {
      newIndex -= 1;
    }
    final taskList = _taskLists.removeAt(oldIndex);
    _taskLists.insert(newIndex, taskList);
    notifyListeners();
  }

  void archiveTaskList(int taskListId) {
    final index = _taskLists.indexWhere((list) => list.id == taskListId);
    if (index == -1) throw Exception('TaskList not found');

    final taskList = _taskLists[index];
    _taskLists[index] = TaskList(
      id: taskList.id,
      title: taskList.title,
      category: taskList.category,
      taskIds: taskList.taskIds,
      archived: true,
    );
    
    notifyListeners();
  }
}
