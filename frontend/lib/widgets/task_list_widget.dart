import 'package:flutter/material.dart';
import '../models/task_list.dart';
import '../models/task.dart';
import '../services/rest_data_service.dart';
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
  final RestDataService _restDataService = RestDataService();
  List<Task> _tasks = [];
  bool _isEditing = false;
  late TextEditingController _titleController;

  @override
  void initState() {
    super.initState();
    _restDataService.addListener(_onDataChanged);
    _loadTasks();
    _titleController = TextEditingController();
  }

  @override
  void dispose() {
    _restDataService.removeListener(_onDataChanged);
    _titleController.dispose();
    super.dispose();
  }

  void _onDataChanged() {
    _loadTasks();
  }

  Future<void> _loadTasks() async {
    try {
      final tasks = await _restDataService.getTasksForList(widget.taskListId);
      setState(() {
        _tasks = tasks;
      });
    } catch (e) {
      // TODO: Handle error
      print('Error loading tasks: $e');
    }
  }

  void _startEditing(String currentTitle) {
    _titleController.text = currentTitle;
    setState(() => _isEditing = true);
  }

  void _saveTitle() {
    if (_titleController.text.isNotEmpty) {
      _restDataService.updateTaskListTitle(
        widget.taskListId,
        _titleController.text,
      );
      setState(() => _isEditing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<TaskList>(
      future: _restDataService.getTaskListById(widget.taskListId),
      builder: (context, snapshot) {
        if (!snapshot.hasData) {
          return const Center(child: CircularProgressIndicator());
        }
        final taskList = snapshot.data!;
    
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.all(16.0),
              child: Row(
                children: [
                  Expanded(
                    child: _isEditing
                        ? TextField(
                            controller: _titleController,
                            autofocus: true,
                            style: Theme.of(context).textTheme.headlineSmall,
                            onSubmitted: (_) => _saveTitle(),
                            decoration: const InputDecoration(
                              border: OutlineInputBorder(),
                            ),
                          )
                        : GestureDetector(
                            onDoubleTap: () => _startEditing(taskList.title),
                            child: Text(
                              taskList.title,
                              style: Theme.of(context).textTheme.headlineSmall,
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
                  ] else
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
                if (_tasks.isEmpty) 
                  const Center(child: CircularProgressIndicator())
                else
                  ReorderableListView(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    onReorder: (oldIndex, newIndex) {
                      _restDataService.reorderTasks(taskList.id, oldIndex, newIndex);
                    },
                    children: [
                      for (final taskId in taskList.taskIds)
                        KeyedSubtree(
                          key: ValueKey(taskId),
                          child: TaskCard(
                            task: _tasks.firstWhere((t) => t.id == taskId),
                            category: taskList.category,
                            onComplete: () {
                              final task = _tasks.firstWhere((t) => t.id == taskId);
                              _restDataService.markTaskComplete(
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
      },
    );
  }
}
