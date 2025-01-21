import 'package:flutter/material.dart';
import '../models/task.dart';

class TaskOptionsSheet extends StatelessWidget {
  final Task task;
  final VoidCallback onDelete;
  final VoidCallback onEditDueDate;
  final VoidCallback onClearDueDate;

  const TaskOptionsSheet({
    super.key,
    required this.task,
    required this.onDelete,
    required this.onEditDueDate,
    required this.onClearDueDate,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      color: const Color(0xFF1A1B1E),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ListTile(
            leading: const Icon(Icons.calendar_today, color: Colors.white),
            title: const Text(
              'Edit Due Date',
              style: TextStyle(color: Colors.white),
            ),
            onTap: () {
              Navigator.pop(context);
              onEditDueDate();
            },
          ),
          ListTile(
            leading: const Icon(Icons.calendar_today, color: Colors.white),
            title: const Text(
              'Clear Due Date',
              style: TextStyle(color: Colors.white),
            ),
            onTap: () {
              Navigator.pop(context);
              onClearDueDate();
            },
          ),
          ListTile(
            leading: const Icon(Icons.delete, color: Colors.white),
            title: const Text(
              'Delete Task',
              style: TextStyle(color: Colors.white),
            ),
            onTap: onDelete,
          ),
        ],
      ),
    );
  }
} 