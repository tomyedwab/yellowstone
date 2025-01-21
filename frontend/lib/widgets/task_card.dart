import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../models/task.dart';
import '../models/task_list.dart';
import '../services/rest_data_service.dart';
import 'task_options_sheet.dart';

class TaskCard extends StatefulWidget {
  final RestDataService dataService;
  final Task task;
  final TaskListCategory category;
  final VoidCallback? onComplete;
  static final DateFormat _dateFormat = DateFormat('MMM dd, yyyy');

  const TaskCard({
    super.key,
    required this.dataService,
    required this.task,
    required this.category,
    this.onComplete,
  });

  @override
  State<TaskCard> createState() => _TaskCardState();
}

class _TaskCardState extends State<TaskCard> {
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
      widget.dataService.updateTaskDueDate(widget.task.id, date);
    }
  }

  void _clearDueDate() {
    widget.dataService.updateTaskDueDate(widget.task.id, null);
  }

  void _saveTitle() {
    if (_titleController.text.isNotEmpty) {
      widget.dataService.updateTaskTitle(widget.task.id, _titleController.text);
      setState(() => _isEditing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 16.0, right: 32.0, top: 4.0, bottom: 4.0),
      child: Material(
        color: Colors.transparent,
        child: ListTile(
          contentPadding: EdgeInsets.zero,
          title: _isEditing
            ? Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                      Expanded(
                        child: TextField(
                        controller: _titleController,
                        autofocus: true,
                        onSubmitted: (_) => _saveTitle(),
                        decoration: const InputDecoration(
                          border: OutlineInputBorder(),
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
                    ]
                  ])
              ],
            )
            : GestureDetector(
                onTap: () => setState(() => _isEditing = true),
                child: Text(
                  widget.task.title,
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 16,
                    fontWeight: FontWeight.w400,
                    decoration: widget.task.isCompleted
                    ? TextDecoration.lineThrough
                    : null,
                  ),
                ),
            ),
          subtitle: widget.task.completedAt != null ?
            Text(
                'Completed ${TaskCard._dateFormat.format(widget.task.completedAt!)}',
                style: const TextStyle(
                  color: Colors.white70,
                  fontSize: 14,
                ),
              )
            : widget.task.dueDate != null
              ? Text(
                  'Due ${TaskCard._dateFormat.format(widget.task.dueDate!)}',
                  style: const TextStyle(
                    color: Colors.white70,
                    fontSize: 14,
                  ),
                )
              : null,
          leading: GestureDetector(
            onTap: () {
              if (widget.category != TaskListCategory.template) {
                widget.onComplete?.call();
              }
            },
            child: Container(
              width: 36,
              height: 24,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(
                  color: Colors.white,
                  width: 2,
                ),
              ),
              child: widget.task.isCompleted
                  ? const Icon(
                    Icons.check,
                    size: 16,
                    color: Colors.white,
                  )
                  : null,
            ),
          ),
          trailing: GestureDetector(
            onTap: () {
              showModalBottomSheet(
                context: context,
                builder: (context) => TaskOptionsSheet(
                  task: widget.task,
                  onDelete: () {
                        Navigator.pop(context);
                        widget.dataService.deleteTask(
                          widget.task.taskListId,
                          widget.task.id,
                        );
                      },
                      onEditDueDate: _selectDueDate,
                      onClearDueDate: _clearDueDate,
                    ),
                  );
            },
            child: const Icon(Icons.more_vert),
          ),
        ),
      ),
    );
  }
}
