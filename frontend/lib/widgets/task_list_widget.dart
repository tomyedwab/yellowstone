import 'package:flutter/material.dart';
import '../models/task_list.dart';
import '../services/mock_data_service.dart';
import 'task_card.dart';

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
        if (taskList.tasks.isEmpty)
          const Padding(
            padding: EdgeInsets.all(16.0),
            child: Text('No tasks in this list'),
          )
        else
          ListView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: taskList.tasks.length,
            itemBuilder: (context, index) {
              return TaskCard(
                task: taskList.tasks[index],
                category: taskList.category,
                onComplete: () {
                  final task = taskList.tasks[index];
                  _mockDataService.markTaskComplete(
                    task.taskListId,
                    task.id,
                    !task.isCompleted,
                  );
                },
              );
            },
          ),
      ],
    );
  }
}
