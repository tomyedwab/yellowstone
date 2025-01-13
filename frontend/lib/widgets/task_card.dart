import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../models/task.dart';
import '../models/task_list.dart';

class TaskCard extends StatelessWidget {
  final Task task;
  final TaskListCategory category;
  final VoidCallback? onComplete;
  final DateFormat _dateFormat = DateFormat('MMM dd, yyyy');

  TaskCard({
    super.key,
    required this.task,
    required this.category,
    this.onComplete,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.all(8.0),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    task.title,
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                          decoration: task.isCompleted ? TextDecoration.lineThrough : null,
                        ),
                  ),
                ),
                if (category != TaskListCategory.template) ...[
                  IconButton(
                    icon: Icon(
                      task.isCompleted ? Icons.check_circle : Icons.circle_outlined,
                      color: task.isCompleted ? Colors.green : Colors.grey,
                    ),
                    onPressed: onComplete,
                  ),
                ] else if (task.isCompleted) ...[
                  const Icon(Icons.check_circle, color: Colors.green),
                ],
              ],
            ),
            if (task.parentTaskId != null) ...[
              const SizedBox(height: 4.0),
              Text(
                'Subtask of #${task.parentTaskId}',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
            if (task.dueDate != null) ...[
              const SizedBox(height: 8.0),
              Text(
                'Due: ${_dateFormat.format(task.dueDate!)}',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ],
            if (task.comments.isNotEmpty) ...[
              const SizedBox(height: 8.0),
              const Text('Comments:', style: TextStyle(fontWeight: FontWeight.bold)),
              ...task.comments.map((comment) => Padding(
                    padding: const EdgeInsets.only(left: 8.0, top: 4.0),
                    child: Text('• $comment'),
                  )),
            ],
            if (task.completedAt != null) ...[
              const SizedBox(height: 8.0),
              Text(
                'Completed: ${_dateFormat.format(task.completedAt!)}',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ],
        ),
      ),
    );
  }
}
