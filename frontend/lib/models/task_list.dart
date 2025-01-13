import 'task.dart';

enum TaskListCategory {
  template,
  toDoList,
}

class TaskList {
  final int id;
  final String title;
  final TaskListCategory category;
  final List<int> taskIds;

  TaskList({
    required this.id,
    required this.title,
    required this.category,
    this.taskIds = const [],
  });
}
