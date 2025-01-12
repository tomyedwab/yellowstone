class Task {
  final int id;
  final String title;
  final List<String> comments;
  final DateTime? dueDate;
  final int? parentTaskId;
  final bool isCompleted;
  final DateTime? completedAt;
  final int taskListId;

  Task({
    required this.id,
    required this.title,
    required this.taskListId,
    this.comments = const [],
    this.dueDate,
    this.parentTaskId,
    this.isCompleted = false,
    this.completedAt,
  });
}
