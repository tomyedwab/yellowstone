import 'package:flutter/material.dart';
import '../models/task_list.dart';
import '../models/task.dart';
import '../services/rest_data_service.dart';
import '../services/responsive_service.dart';
import 'task_card.dart';
import 'new_task_card.dart';

class TaskListWidget extends StatefulWidget {
  final RestDataService dataService;
  final ResponsiveService responsiveService;
  final int taskListId;
  final String taskListPrefix;
  final bool isSelectionMode;
  final Set<int> selectedTaskIds;
  final int? selectedTaskId;
  final void Function(int) onTaskSelectionChanged;

  const TaskListWidget({
    super.key,
    required this.dataService,
    required this.responsiveService,
    required this.taskListId,
    required this.taskListPrefix,
    this.isSelectionMode = false,
    required this.selectedTaskIds,
    required this.onTaskSelectionChanged,
    required this.selectedTaskId,
  });

  @override
  State<TaskListWidget> createState() => _TaskListWidgetState();
}

class _TaskListWidgetState extends State<TaskListWidget> {
  bool _isLoading = true;
  List<Task> _tasks = [];
  Map<int, TaskRecentComment>? _recentComments;
  TaskList? _taskList = null;
  Map<int, List<TaskLabel>> _taskLabels = {};

  @override
  void initState() {
    super.initState();
    widget.dataService.addListener(_onDataChanged);
    _loadTasks();
  }

  @override
  void dispose() {
    widget.dataService.removeListener(_onDataChanged);
    super.dispose();
  }

  void _onDataChanged() {
    _loadTasks();
  }

  Future<void> _loadTasks() async {
    try {
      final tasks = await widget.dataService.getTasksForList(widget.taskListId);
      final recentComments = await widget.dataService.getTaskListRecentComments(widget.taskListId); 
      final taskList = await widget.dataService.getTaskListById(widget.taskListId);
      final taskLabels = await widget.dataService.getTaskLabels(widget.taskListId);
      setState(() {
        _tasks = tasks;
        _recentComments = Map.fromEntries(recentComments.map((comment) => MapEntry(comment.taskId, comment)));
        _taskList = taskList;
        _taskLabels = taskLabels
          .where((label) => label.listId != widget.taskListId)
          .fold<Map<int, List<TaskLabel>>>(
            {},
            (map, label) => map..update(
              label.taskId,
              (labels) => [...labels, label],
              ifAbsent: () => [label],
            ),
          );
        _isLoading = false;
      });
    } catch (e) {
      // TODO: Handle error
      print('Error loading tasks: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (widget.isSelectionMode)
          SizedBox(
            height: MediaQuery.of(context).size.height - widget.responsiveService.tasksViewBottomBoxSize,
            child: SingleChildScrollView(
              child: Column(
                children: [
                  for (final task in _tasks)
                    TaskCard(
                      key: ValueKey(task.id),
                      dataService: widget.dataService,
                      responsiveService: widget.responsiveService,
                      task: task,
                      taskListId: widget.taskListId,
                      taskListPrefix: widget.taskListPrefix,
                      category: _taskList!.category,
                      labels: _taskLabels[task.id],
                      recentComment: _recentComments?[task.id],
                      onComplete: () {
                        widget.dataService.markTaskComplete(
                          task.id,
                          !task.isCompleted,
                        );
                      },
                      isSelectionMode: true,
                      isSelected: widget.selectedTaskIds.contains(task.id),
                      isHighlighted: widget.selectedTaskId == task.id,
                      onSelectionChanged: (selected) => widget.onTaskSelectionChanged(task.id),
                    ),
                ],
              )
            )
          )
        else
          SizedBox(
            height: MediaQuery.of(context).size.height - widget.responsiveService.tasksViewBottomBoxSize,
            child: ReorderableListView(
              onReorder: (oldIndex, newIndex) {
                if (oldIndex < newIndex) {
                  newIndex -= 1;
                }
                widget.dataService.reorderTasks(
                  _taskList!.id,
                  _tasks[oldIndex].id,
                  newIndex == 0 ? null : _tasks[newIndex - 1].id,
                );
              },
              children: [
                for (final task in _tasks)
                  KeyedSubtree(
                    key: ValueKey(task.id),
                    child: TaskCard(
                      dataService: widget.dataService,
                      responsiveService: widget.responsiveService,
                      task: task,
                      taskListId: widget.taskListId,
                      taskListPrefix: widget.taskListPrefix,
                      category: _taskList!.category,
                      labels: _taskLabels[task.id],
                      recentComment: _recentComments?[task.id],
                      onComplete: () {
                        widget.dataService.markTaskComplete(
                          task.id,
                          !task.isCompleted,
                        );
                      },
                      isSelectionMode: false,
                      isSelected: false,
                      isHighlighted: widget.selectedTaskId == task.id,
                      onSelectionChanged: null,
                    ),
                  ),
              ],
            ),
          ),
        if (!widget.isSelectionMode)
          NewTaskCard(
            dataService: widget.dataService,
            taskListId: _taskList!.id,
          ),
      ],
    );
  }
}
