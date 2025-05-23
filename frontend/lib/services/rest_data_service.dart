import 'dart:convert';
import '../models/task.dart';
import '../models/task_list.dart';
import '../models/task_history.dart';
import '../yesterday/api.dart';
import '../yesterday/auth.dart';

class RestDataService extends YesterdayApi {
  final Map<int, Task> _tasks = {};

  RestDataService(YesterdayAuth auth) : super(auth);

  Future<List<TaskList>> getTaskLists({bool includeArchived = false}) async {
    final response = await getCachedResponse('/tasklist/all');

    if (response.statusCode != 200) {
      throw Exception('Failed to load task lists');
    }

    final Map<String, dynamic> data = json.decode(response.body);
    final List<dynamic> taskLists = data['TaskLists'];

    final inFlightReorderedTaskLists = inFlightRequests.where(
        (request) => request.$2['type'] == 'yellowstone:reorderTaskList');
    for (final inFlightRequest in inFlightReorderedTaskLists) {
      final oldTaskListId = inFlightRequest.$2['listId'];
      final afterTaskListId = inFlightRequest.$2['afterListId'];
      final oldTaskListIndex =
          taskLists.indexWhere((taskList) => taskList['Id'] == oldTaskListId);
      final afterTaskListIndex =
          taskLists.indexWhere((taskList) => taskList['Id'] == afterTaskListId);
      if (oldTaskListIndex != -1) {
        final taskList = taskLists[oldTaskListIndex];
        taskLists.removeAt(oldTaskListIndex);
        taskLists.insert(afterTaskListIndex + 1, taskList);
      }
    }

    return taskLists
        .map((json) => TaskList(
              id: json['Id'],
              title: json['Title'],
              category: _categoryFromString(json['Category']),
              archived: json['Archived'],
            ))
        .toList();
  }

  Future<List<TaskListMetadata>> getTaskListMetadata() async {
    final response = await getCachedResponse('/tasklist/metadata');

    if (response.statusCode != 200) {
      throw Exception('Failed to load task list metadata');
    }
    final List<dynamic> data = json.decode(response.body);
    return data
        .map((json) => TaskListMetadata(
              id: json['ListId'],
              total: json['Total'],
              completed: json['Completed'],
            ))
        .toList();
  }

  TaskListCategory _categoryFromString(String category) {
    switch (category.toLowerCase()) {
      case 'todolist':
        return TaskListCategory.toDoList;
      case 'label':
        return TaskListCategory.label;
      case 'template':
        return TaskListCategory.template;
      default:
        throw Exception('Unknown category: $category');
    }
  }

  Future<TaskList> getTaskListById(int taskListId) async {
    // Get the task list details
    final listResponse =
        await getCachedResponse('/tasklist/get?id=$taskListId');

    if (listResponse.statusCode != 200) {
      throw Exception('Failed to load task list: ${listResponse.body}');
    }

    // Get the tasks for this list
    final tasksResponse =
        await getCachedResponse('/task/list?listId=$taskListId');

    if (tasksResponse.statusCode != 200) {
      throw Exception('Failed to load tasks: ${tasksResponse.body}');
    }

    final Map<String, dynamic> listJson = jsonDecode(listResponse.body);
    final Map<String, dynamic> tasksJson = jsonDecode(tasksResponse.body);
    final List<dynamic> tasksData = tasksJson['Tasks'];

    // Convert task JSON data to Task objects and store them
    for (var taskData in tasksData) {
      final task = Task(
        id: taskData['Id'],
        title: taskData['Title'],
        taskListId: taskListId,
        dueDate: taskData['DueDate'] != null
            ? DateTime.parse(taskData['DueDate'])
            : null,
        isCompleted: taskData['CompletedAt'] != null,
        completedAt: taskData['CompletedAt'] != null
            ? DateTime.parse(taskData['CompletedAt'])
            : null,
      );
      _tasks[task.id] = task;
    }

    return TaskList(
      id: listJson['Id'],
      title: listJson['Title'],
      category: _categoryFromString(listJson['Category']),
      archived: listJson['Archived'],
      taskIds: tasksData.map<int>((task) => task['Id'] as int).toList(),
    );
  }

  Task getTaskById(int taskId) {
    final task = _tasks[taskId];
    if (task == null) {
      throw Exception('Task not found');
    }
    return task;
  }

  Future<List<Task>> getTasksForList(int taskListId) async {
    final response = await getCachedResponse('/task/list?listId=$taskListId');

    if (response.statusCode == 200) {
      final Map<String, dynamic> data = jsonDecode(response.body);
      final List<dynamic> tasksData = data['Tasks'];

      final inFlightCompletedTasks = inFlightRequests.where(
          (request) => request.$2['type'] == 'yellowstone:updateTaskCompleted');
      final inFlightRenamedTasks = inFlightRequests.where(
          (request) => request.$2['type'] == 'yellowstone:updateTaskTitle');
      final inFlightReorderedTasks = inFlightRequests.where((request) =>
          request.$2['type'] == 'yellowstone:reorderTasks' &&
          request.$2['taskListId'] == taskListId);

      for (final inFlightRequest in inFlightReorderedTasks) {
        final oldTaskId = inFlightRequest.$2['oldTaskId'];
        final afterTaskId = inFlightRequest.$2['afterTaskId'];
        // Reorder the tasks in tasksData
        final oldTaskIndex =
            tasksData.indexWhere((task) => task['Id'] == oldTaskId);
        final afterTaskIndex =
            tasksData.indexWhere((task) => task['Id'] == afterTaskId);
        if (oldTaskIndex != -1) {
          final taskData = tasksData[oldTaskIndex];
          tasksData.removeAt(oldTaskIndex);
          tasksData.insert(afterTaskIndex + 1, taskData);
        }
      }

      final tasks = tasksData.map((taskData) {
        var title = taskData['Title'];
        for (final inFlightRequest in inFlightRenamedTasks) {
          if (inFlightRequest.$2['taskId'] == taskData['Id']) {
            title = inFlightRequest.$2['title'];
          }
        }
        var completedAt = taskData['CompletedAt'];
        for (final inFlightRequest in inFlightCompletedTasks) {
          if (inFlightRequest.$2['taskId'] == taskData['Id']) {
            completedAt = inFlightRequest.$2['completedAt'];
          }
        }
        final task = Task(
          id: taskData['Id'],
          title: title,
          taskListId: taskListId,
          dueDate: taskData['DueDate'] != null
              ? DateTime.parse(taskData['DueDate'])
              : null,
          isCompleted: completedAt != null,
          completedAt: completedAt != null ? DateTime.parse(completedAt) : null,
        );
        _tasks[task.id] = task;
        return task;
      }).toList();

      return tasks;
    } else {
      throw Exception('Failed to load tasks: ${response.body}');
    }
  }

  Future<List<TaskRecentComment>> getTaskListRecentComments(
      int taskListId) async {
    final response =
        await getCachedResponse('/tasklist/recent_comments?listId=$taskListId');
    if (response.statusCode != 200) {
      throw Exception('Failed to load task list recent comments');
    }
    final List<dynamic> data = jsonDecode(response.body);
    return data
        .map((json) => TaskRecentComment(
              taskId: json['TaskId'],
              userComment: json['UserComment'],
              createdAt: DateTime.parse(json['CreatedAt']),
            ))
        .toList();
  }

  Future<List<TaskLabel>> getTaskLabels(int taskListId) async {
    final response =
        await getCachedResponse('/tasklist/labels?listId=$taskListId');
    if (response.statusCode != 200) {
      throw Exception('Failed to load task labels');
    }
    final List<dynamic> data = jsonDecode(response.body);
    return data
        .map((json) => TaskLabel(
              taskId: json['TaskId'],
              label: json['Label'],
              listId: json['ListId'],
            ))
        .toList();
  }

  Future<void> updateTaskTitle(int taskId, String title) async {
    await doPublishRequest({
      'type': 'yellowstone:updateTaskTitle',
      'taskId': taskId,
      'title': title,
    });
  }

  Future<void> updateTaskDueDate(int taskId, DateTime? dueDate) async {
    await doPublishRequest({
      'type': 'yellowstone:updateTaskDueDate',
      'taskId': taskId,
      'dueDate': dueDate?.toUtc().toIso8601String(),
    });
  }

  Future<void> deleteTask(int taskListId, int taskId) async {
    await doPublishRequest({
      'type': 'yellowstone:deleteTask',
      'taskId': taskId,
    });
  }

  Future<void> createTask(int taskListId, String title) async {
    await doPublishRequest({
      'type': 'yellowstone:addTask',
      'title': title,
      'taskListId': taskListId,
      'dueDate': null,
    });
  }

  Future<void> reorderTasks(
      int taskListId, int oldTaskId, int? afterTaskId) async {
    await doPublishRequest({
      'type': 'yellowstone:reorderTasks',
      'taskListId': taskListId,
      'oldTaskId': oldTaskId,
      'afterTaskId': afterTaskId,
    });
  }

  Future<void> markTaskComplete(int taskId, bool complete) async {
    await doPublishRequest({
      'type': 'yellowstone:updateTaskCompleted',
      'taskId': taskId,
      'completedAt': complete ? DateTime.now().toUtc().toIso8601String() : null,
    });
  }

  Future<void> reorderTaskList(int taskListId, int? afterTaskListId) async {
    await doPublishRequest({
      'type': 'yellowstone:reorderTaskList',
      'listId': taskListId,
      'afterListId': afterTaskListId,
    });
  }

  Future<void> archiveTaskList(int taskListId) async {
    await doPublishRequest({
      'type': 'yellowstone:updateTaskListArchived',
      'listId': taskListId,
      'archived': true,
    });
  }

  Future<void> unarchiveTaskList(int taskListId) async {
    await doPublishRequest({
      'type': 'yellowstone:updateTaskListArchived',
      'listId': taskListId,
      'archived': false,
    });
  }

  Future<void> createTaskList(String title, TaskListCategory category) async {
    await doPublishRequest({
      'type': 'yellowstone:addTaskList',
      'title': title,
      'category': category.name.toLowerCase(),
      'archived': false,
    });
  }

  Future<void> updateTaskListTitle(int taskListId, String title) async {
    await doPublishRequest({
      'type': 'yellowstone:updateTaskListTitle',
      'listId': taskListId,
      'title': title,
    });
  }

  Future<TaskHistoryResponse> getTaskHistory(int taskId) async {
    final response = await getCachedResponse('/task/history?id=$taskId');

    if (response.statusCode != 200) {
      throw Exception('Failed to load task history');
    }

    final Map<String, dynamic> data = json.decode(response.body);
    final List<dynamic> history = data['history'];

    return TaskHistoryResponse(
      history: history.map((json) => TaskHistory.fromJson(json)).toList(),
      title: data['title'],
    );
  }

  Future<void> addTaskComment(int taskId, String comment) async {
    await doPublishRequest({
      'type': 'yellowstone:addTaskComment',
      'taskId': taskId,
      'userComment': comment,
    });
  }

  Future<List<TaskList>> getAllTaskLists() async {
    final response = await getCachedResponse('/tasklist/all');

    if (response.statusCode != 200) {
      throw Exception('Failed to load task lists');
    }

    final Map<String, dynamic> data = json.decode(response.body);
    final List<dynamic> taskLists = data['TaskLists'];

    return taskLists
        .map((json) => TaskList(
              id: json['Id'],
              title: json['Title'],
              category: _categoryFromString(json['Category']),
              archived: json['Archived'],
            ))
        .toList();
  }

  Future<void> moveTasksToList(
      Set<int> taskIds, int oldListId, int newListId) async {
    await doPublishRequest({
      'type': 'yellowstone:moveTasks',
      'taskIds': taskIds.toList(),
      'oldListId': oldListId,
      'newListId': newListId,
    });
  }

  Future<void> copyTasksToList(Set<int> taskIds, int newListId) async {
    await doPublishRequest({
      'type': 'yellowstone:copyTasks',
      'taskIds': taskIds.toList(),
      'newListId': newListId,
    });
  }

  Future<void> duplicateTasksToList(Set<int> taskIds, int newListId) async {
    await doPublishRequest({
      'type': 'yellowstone:duplicateTasks',
      'taskIds': taskIds.toList(),
      'newListId': newListId,
    });
  }
}
