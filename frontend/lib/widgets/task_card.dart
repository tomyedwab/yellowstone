import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../models/task.dart';
import '../models/task_list.dart';
import '../services/mock_data_service.dart';

class TaskCard extends StatefulWidget {
  final Task task;
  final TaskListCategory category;
  final VoidCallback? onComplete;
  static final DateFormat _dateFormat = DateFormat('MMM dd, yyyy');

  const TaskCard({
    super.key,
    required this.task,
    required this.category,
    this.onComplete,
  });

  @override
  State<TaskCard> createState() => _TaskCardState();
}

class _TaskCardState extends State<TaskCard> {
  final MockDataService _mockDataService = MockDataService();
  bool _isEditing = false;
  late TextEditingController _titleController;

  @override
  void initState() {
    super.initState();
    _titleController = TextEditingController(text: widget.task.title);
  }

  @override
  void dispose() {
    _titleController.dispose();
    super.dispose();
  }

  Future<void> _selectDueDate() async {
    final date = await showDatePicker(
      context: context,
      initialDate: widget.task.dueDate ?? DateTime.now(),
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 365)),
    );
    
    if (date != null) {
      _mockDataService.updateTask(
        widget.task.taskListId,
        widget.task.id,
        dueDate: date,
      );
    }
  }

  void _clearDueDate() {
    _mockDataService.updateTask(
      widget.task.taskListId,
      widget.task.id,
      dueDate: null,
    );
  }

  void _saveTitle() {
    if (_titleController.text.isNotEmpty) {
      _mockDataService.updateTask(
        widget.task.taskListId,
        widget.task.id,
        title: _titleController.text,
      );
      setState(() => _isEditing = false);
    }
  }

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
                  child: _isEditing
                      ? TextField(
                          controller: _titleController,
                          autofocus: true,
                          onSubmitted: (_) => _saveTitle(),
                          decoration: const InputDecoration(
                            border: OutlineInputBorder(),
                          ),
                        )
                      : GestureDetector(
                          onDoubleTap: () {
                            if (widget.category != TaskListCategory.template) {
                              setState(() => _isEditing = true);
                            }
                          },
                          child: Text(
                            widget.task.title,
                            style: Theme.of(context).textTheme.titleLarge?.copyWith(
                                  decoration: widget.task.isCompleted
                                      ? TextDecoration.lineThrough
                                      : null,
                                ),
                          ),
                        ),
                ),
                if (_isEditing) ...[
                  IconButton(
                    icon: const Icon(Icons.check),
                    onPressed: _saveTitle,
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => setState(() => _isEditing = false),
                  ),
                ] else ...[
                  if (widget.category != TaskListCategory.template) ...[
                    IconButton(
                      icon: Icon(
                        widget.task.isCompleted
                            ? Icons.check_circle
                            : Icons.circle_outlined,
                        color: widget.task.isCompleted ? Colors.green : Colors.grey,
                      ),
                      onPressed: widget.onComplete,
                    ),
                    IconButton(
                      icon: const Icon(Icons.calendar_today),
                      onPressed: _selectDueDate,
                    ),
                    if (widget.task.dueDate != null)
                      IconButton(
                        icon: const Icon(Icons.clear),
                        onPressed: _clearDueDate,
                      ),
                  ] else if (widget.task.isCompleted) ...[
                    const Icon(Icons.check_circle, color: Colors.green),
                  ],
                ],
              ],
            ),
            if (widget.task.parentTaskId != null) ...[
              const SizedBox(height: 4.0),
              Text(
                'Subtask of #${widget.task.parentTaskId}',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
            if (widget.task.dueDate != null) ...[
              const SizedBox(height: 8.0),
              Text(
                'Due: ${widget._dateFormat.format(widget.task.dueDate!)}',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ],
            if (widget.task.comments.isNotEmpty) ...[
              const SizedBox(height: 8.0),
              const Text('Comments:', style: TextStyle(fontWeight: FontWeight.bold)),
              ...widget.task.comments.map((comment) => Padding(
                    padding: const EdgeInsets.only(left: 8.0, top: 4.0),
                    child: Text('• $comment'),
                  )),
            ],
            if (widget.task.completedAt != null) ...[
              const SizedBox(height: 8.0),
              Text(
                'Completed: ${_dateFormat.format(widget.task.completedAt!)}',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ],
        ),
      ),
    );
  }
}
