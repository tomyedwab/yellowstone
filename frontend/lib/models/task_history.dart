class TaskHistory {
  final int id;
  final int taskId;
  final String updateType;
  final String systemComment;
  final String? userComment;
  final DateTime createdAt;

  TaskHistory({
    required this.id,
    required this.taskId,
    required this.updateType,
    required this.systemComment,
    this.userComment,
    required this.createdAt,
  });

  factory TaskHistory.fromJson(Map<String, dynamic> json) {
    return TaskHistory(
      id: json['Id'],
      taskId: json['TaskId'],
      updateType: json['UpdateType'],
      systemComment: json['SystemComment'],
      userComment: json['UserComment'],
      createdAt: DateTime.parse(json['CreatedAt']),
    );
  }
} 