import 'package:flutter/material.dart';
import '../models/task_list.dart';
import '../services/mock_data_service.dart';
import 'task_card.dart';

class TaskListWidget extends StatelessWidget {
  final TaskList taskList;

  const TaskListWidget({
    super.key,
    required this.taskList,
  });

  @override
  Widget build(BuildContext context) {
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
                  MockDataService().markTaskComplete(
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
