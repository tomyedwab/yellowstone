import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../models/task.dart';
import '../models/task_list.dart';
import '../services/rest_data_service.dart';
import '../services/responsive_service.dart';
import 'task_options_sheet.dart';
import '../pages/task_history_page.dart';

class TaskCard extends StatefulWidget {
  final RestDataService dataService;
  final ResponsiveService responsiveService;
  final Task task;
  final int taskListId;
  final String taskListPrefix;
  final List<TaskLabel>? labels;
  final TaskListCategory category;
  final VoidCallback? onComplete;
  final VoidCallback? onReorder;
  final TaskRecentComment? recentComment;
  final bool isDragging;
  final bool isSelectionMode;
  final bool isSelected;
  final bool isHighlighted;
  final ValueChanged<bool>? onSelectionChanged;
  static final DateFormat _dateFormat = DateFormat('MMM dd, yyyy');

  const TaskCard({
    super.key,
    required this.dataService,
    required this.responsiveService,
    required this.task,
    required this.taskListId,
    required this.taskListPrefix,
    required this.category,
    required this.labels,
    this.onComplete,
    this.onReorder,
    this.isDragging = false,
    this.recentComment,
    this.isSelectionMode = false,
    this.isSelected = false,
    this.isHighlighted = false,
    this.onSelectionChanged,
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
    final labels = widget.labels ?? [];
    return Container(
      margin: const EdgeInsets.only(left: 16.0, right: 4.0, top: 4.0, bottom: 4.0),
      decoration: BoxDecoration(
        color: widget.isHighlighted ? const Color.fromARGB(255, 49, 65, 80) : null,
        borderRadius: BorderRadius.circular(8),
        border: const Border(
          bottom: BorderSide(
            color: Color(0xff182631),
            width: 1.5,
          ),
        ),
      ),
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
                onTap: widget.isSelectionMode ? null : () => setState(() => _isEditing = true),
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
          subtitle: widget.task.completedAt != null || widget.task.dueDate != null || widget.recentComment != null || labels.isNotEmpty
            ? Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (widget.task.completedAt != null)
                    Text(
                      'Completed ${TaskCard._dateFormat.format(widget.task.completedAt!)}',
                      style: const TextStyle(
                        color: Colors.white70,
                        fontSize: 14,
                      ),
                    )
                  else if (widget.task.dueDate != null)
                    Text(
                      'Due ${TaskCard._dateFormat.format(widget.task.dueDate!)}',
                      style: const TextStyle(
                        color: Colors.white70,
                        fontSize: 14,
                      ),
                    ),
                  if (widget.recentComment != null)
                    Text(
                      widget.recentComment!.userComment,
                      style: const TextStyle(
                        color: Colors.white70,
                        fontSize: 14,
                        fontStyle: FontStyle.italic
                      ),
                    ),
                  if (labels.isNotEmpty)
                    Wrap(
                      spacing: 4,
                      children: labels.map((label) => GestureDetector(
                        onTap: () {
                          context.go('/labels/list/${label.listId}');
                        },
                        child: Chip(
                          label: Text(
                            label.label,
                            style: const TextStyle(
                              color: Colors.white70,
                              fontSize: 12,
                            ),
                          ),
                          backgroundColor: Colors.black26,
                          visualDensity: VisualDensity.compact,
                          side: const BorderSide(
                            width: 0.5,
                            color: Colors.white30,
                          ),
                        ),
                      )).toList(),
                    ),
                ],
              )
            : null,
          leading: widget.isSelectionMode
            ? Checkbox(
                value: widget.isSelected,
                onChanged: (value) => widget.onSelectionChanged?.call(value ?? false),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(4),
                ),
              )
            : GestureDetector(
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
          trailing: !widget.isSelectionMode
            ? Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  IconButton(
                    icon: const Icon(Icons.history),
                    onPressed: () {
                      context.go('${widget.taskListPrefix}list/${widget.taskListId}/task/${widget.task.id}/history');
                    },
                    tooltip: 'View history',
                  ),
                  GestureDetector(
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
                  if (widget.responsiveService.reorderableHandlesVisible) const SizedBox(width: 30),
                ],
              )
            : null,
        ),
      ),
    );
  }
}
