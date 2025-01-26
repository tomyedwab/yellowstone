import 'package:flutter/material.dart';
import '../models/task_list.dart';
import '../models/task.dart';
import '../services/rest_data_service.dart';
import 'task_card.dart';
import 'new_task_card.dart';

class TaskListWidget extends StatefulWidget {
  final RestDataService dataService;
  final int taskListId;
  final String taskListPrefix;
  final bool isSelectionMode;
  final Set<int> selectedTaskIds;
  final int? selectedTaskId;
  final void Function(int) onTaskSelectionChanged;

  const TaskListWidget({
    super.key,
    required this.dataService,
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
      setState(() {
        _tasks = tasks;
        _recentComments = Map.fromEntries(recentComments.map((comment) => MapEntry(comment.taskId, comment)));
        _taskList = taskList;
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
        Padding(
          padding: const EdgeInsets.all(16.0),
          child: Text(
            _taskList!.title,
            style: Theme.of(context).textTheme.headlineSmall,
          ),
        ),
        if (widget.isSelectionMode)
          Column(
            children: [
              for (final task in _tasks)
                TaskCard(
                  key: ValueKey(task.id),
                  dataService: widget.dataService,
                  task: task,
                  taskListId: widget.taskListId,
                  taskListPrefix: widget.taskListPrefix,
                  category: _taskList!.category,
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
        else
          SizedBox(
            height: MediaQuery.of(context).size.height - 200, // Adjust this value as needed
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
                      task: task,
                      taskListId: widget.taskListId,
                      taskListPrefix: widget.taskListPrefix,
                      category: _taskList!.category,
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
