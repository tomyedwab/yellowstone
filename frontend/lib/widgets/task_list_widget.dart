import 'package:flutter/material.dart';
import '../models/task_list.dart';
import '../models/task.dart';
import '../services/rest_data_service.dart';
import 'task_card.dart';
import 'new_task_card.dart';

class TaskListWidget extends StatefulWidget {
  final RestDataService dataService;
  final int taskListId;
  final bool isSelectionMode;
  final Set<int> selectedTaskIds;
  final void Function(int) onTaskSelectionChanged;

  const TaskListWidget({
    super.key,
    required this.dataService,
    required this.taskListId,
    this.isSelectionMode = false,
    required this.selectedTaskIds,
    required this.onTaskSelectionChanged,
  });

  @override
  State<TaskListWidget> createState() => _TaskListWidgetState();
}

class _TaskListWidgetState extends State<TaskListWidget> {
  List<Task> _tasks = [];
  Map<int, TaskRecentComment>? _recentComments;

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
      setState(() {
        _tasks = tasks;
        _recentComments = Map.fromEntries(recentComments.map((comment) => MapEntry(comment.taskId, comment)));
      });
    } catch (e) {
      // TODO: Handle error
      print('Error loading tasks: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<TaskList>(
      future: widget.dataService.getTaskListById(widget.taskListId),
      builder: (context, snapshot) {
        if (!snapshot.hasData) {
          return const Center(child: CircularProgressIndicator());
        }
        final taskList = snapshot.data!;

        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (_tasks.isEmpty) 
              const Center(child: CircularProgressIndicator())
            else if (widget.isSelectionMode)
              Column(
                children: [
                  for (final task in _tasks)
                    TaskCard(
                      key: ValueKey(task.id),
                      dataService: widget.dataService,
                      task: task,
                      category: taskList.category,
                      recentComment: _recentComments?[task.id],
                      onComplete: () {
                        widget.dataService.markTaskComplete(
                          task.id,
                          !task.isCompleted,
                        );
                      },
                      isSelectionMode: true,
                      isSelected: widget.selectedTaskIds.contains(task.id),
                      onSelectionChanged: (selected) => widget.onTaskSelectionChanged(task.id),
                    ),
                ],
              )
            else
              ReorderableListView(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                onReorder: (oldIndex, newIndex) {
                  if (oldIndex < newIndex) {
                    newIndex -= 1;
                  }
                  widget.dataService.reorderTasks(
                    taskList.id,
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
                        task: task,
                        category: taskList.category,
                        recentComment: _recentComments?[task.id],
                        onComplete: () {
                          widget.dataService.markTaskComplete(
                            task.id,
                            !task.isCompleted,
                          );
                        },
                        isSelectionMode: false,
                        isSelected: false,
                        onSelectionChanged: null,
                      ),
                    ),
                ],
              ),
            if (!widget.isSelectionMode)
              NewTaskCard(
                dataService: widget.dataService,
                taskListId: taskList.id,
              ),
          ],
        );
      },
    );
  }
}
