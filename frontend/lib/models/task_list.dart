enum TaskListCategory {
  template,
  toDoList,
}

class TaskList {
  final int id;
  final String title;
  final TaskListCategory category;
  final List<Task> tasks;

  TaskList({
    required this.id,
    required this.title,
    required this.category,
    this.tasks = const [],
  });
}
