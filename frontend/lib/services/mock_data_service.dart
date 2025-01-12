import '../models/task.dart';
import 'package:intl/intl.dart';

class MockDataService {
  List<Task> getTasks() {
    return [
      Task(
        id: 1,
        title: 'Complete Project Proposal',
        comments: ['Initial draft done', 'Needs review'],
        dueDate: DateTime.now().add(const Duration(days: 7)),
        isCompleted: false,
      ),
      Task(
        id: 2,
        title: 'Review Team Updates',
        comments: ['Team A submitted', 'Waiting for Team B'],
        dueDate: DateTime.now().add(const Duration(days: 2)),
        isCompleted: true,
        completedAt: DateTime.now().subtract(const Duration(hours: 3)),
      ),
      Task(
        id: 3,
        title: 'Update Documentation',
        comments: ['Started updating API docs'],
        parentTaskId: 1,
        isCompleted: false,
      ),
    ];
  }
}
