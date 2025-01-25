
enum TaskListCategory {
  template,
  toDoList,
}

class TaskList {
  final int id;
  final String title;
  final TaskListCategory category;
  final List<int> taskIds;
  final bool archived;

  TaskList({
    required this.id,
    required this.title,
    required this.category,
    this.taskIds = const [],
    this.archived = false,
  });
}

class TaskListMetadata {
  final int id;
  final int total;
  final int completed;

  TaskListMetadata({
    required this.id,
    required this.total,
    required this.completed,
  });
}
