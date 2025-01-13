import 'package:flutter/material.dart';
import '../models/task_list.dart';
import '../services/mock_data_service.dart';
import 'task_card.dart';
import 'new_task_card.dart';

class TaskListWidget extends StatefulWidget {
  final int taskListId;

  const TaskListWidget({
    super.key,
    required this.taskListId,
  });

  @override
  State<TaskListWidget> createState() => _TaskListWidgetState();
}

class _TaskListWidgetState extends State<TaskListWidget> {
  final MockDataService _mockDataService = MockDataService();

  @override
  void initState() {
    super.initState();
    _mockDataService.addListener(_onDataChanged);
  }

  @override
  void dispose() {
    _mockDataService.removeListener(_onDataChanged);
    super.dispose();
  }

  void _onDataChanged() {
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final taskList = _mockDataService.getTaskListById(widget.taskListId);
    
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.all(16.0),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  taskList.title,
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
              ),
              Chip(
                label: Text(
                  taskList.category.name,
                  style: const TextStyle(color: Colors.white),
                ),
                backgroundColor: taskList.category == TaskListCategory.template
                    ? Colors.blue
                    : Colors.green,
              ),
            ],
          ),
        ),
        Column(
          children: [
            ReorderableListView(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              onReorder: (oldIndex, newIndex) {
                _mockDataService.reorderTasks(taskList.id, oldIndex, newIndex);
              },
              children: [
                for (int index = 0; index < taskList.taskIds.length; index++)
                  KeyedSubtree(
                    key: ValueKey(taskList.taskIds[index]),
                    child: TaskCard(
                      task: _mockDataService.getTaskById(taskList.taskIds[index]),
                      category: taskList.category,
                      onComplete: () {
                        final task = _mockDataService.getTaskById(taskList.taskIds[index]);
                        _mockDataService.markTaskComplete(
                          task.id,
                          !task.isCompleted,
                        );
                      },
                    ),
                  ),
              ],
            ),
            NewTaskCard(taskListId: taskList.id),
          ],
        ),
      ],
    );
  }
}
