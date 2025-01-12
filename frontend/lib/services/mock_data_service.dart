import '../models/task.dart';
import '../models/task_list.dart';
import 'package:intl/intl.dart';

class MockDataService {
  List<TaskList> getTaskLists() {
    return [
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
            isCompleted: true,
            completedAt: DateTime.now().subtract(const Duration(hours: 3)),
          ),
        ],
      ),
    ];
  }
}
