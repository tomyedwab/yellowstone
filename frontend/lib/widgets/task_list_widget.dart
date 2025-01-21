import 'package:flutter/material.dart';
import '../models/task_list.dart';
import '../models/task.dart';
import '../services/rest_data_service.dart';
import 'task_card.dart';
import 'new_task_card.dart';

class TaskListWidget extends StatefulWidget {
  final RestDataService dataService;
  final int taskListId;

  const TaskListWidget({
    super.key,
    required this.dataService,
    required this.taskListId,
  });

  @override
  State<TaskListWidget> createState() => _TaskListWidgetState();
}

class _TaskListWidgetState extends State<TaskListWidget> {
  List<Task> _tasks = [];

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
      setState(() {
        _tasks = tasks;
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
            else
              ReorderableListView(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                    onReorder: (oldIndex, newIndex) async {
                      await widget.dataService.reorderTasks(
                        taskList.id,
                        taskList.taskIds[oldIndex],
                        newIndex == 0 ? null : taskList.taskIds[newIndex - 1],
                      );
                    },
                    children: [
                      for (final taskId in taskList.taskIds)
                        KeyedSubtree(
                          key: ValueKey(taskId),
                          child: TaskCard(
                            dataService: widget.dataService,
                            task: _tasks.firstWhere((t) => t.id == taskId),
                            category: taskList.category,
                            onComplete: () {
                              final task = _tasks.firstWhere((t) => t.id == taskId);
                              widget.dataService.markTaskComplete(
                                task.id,
                                !task.isCompleted,
                              );
                            },
                          ),
                        ),
                    ],
              ),
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
