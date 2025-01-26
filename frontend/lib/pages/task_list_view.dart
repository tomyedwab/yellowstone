import 'package:flutter/material.dart';
import '../services/rest_data_service.dart';
import '../widgets/task_list_widget.dart';
import '../models/task_list.dart';
import '../models/task.dart';

class TaskListView extends StatefulWidget {
  final RestDataService dataService;
  final int taskListId;

  const TaskListView({
    super.key,
    required this.dataService,
    required this.taskListId,
  });

  @override
  State<TaskListView> createState() => _TaskListViewState();
}

class _TaskListViewState extends State<TaskListView> {
  TaskList? _taskList;

  @override
  void initState() {
    super.initState();
    widget.dataService.addListener(_onDataChanged);
    _loadTaskList();
  }

  @override
  void dispose() {
    widget.dataService.removeListener(_onDataChanged);
    super.dispose();
  }

  void _onDataChanged() {
    _loadTaskList();
  }

  Future<void> _loadTaskList() async {
    try {
      final taskList = await widget.dataService.getTaskListById(widget.taskListId);
      setState(() {
        _taskList = taskList;
      });
    } catch (e) {
      // TODO: Handle error
      print('Error loading task list: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_taskList == null) {
      return const Scaffold(
        body: Center(
          child: CircularProgressIndicator(),
        ),
      );
    }
    
    final taskList = _taskList!;
    
    return Scaffold(
      appBar: AppBar(
        title: Text(taskList.title),
        actions: [
          IconButton(
            icon: const Icon(Icons.edit),
            onPressed: () {
              showDialog(
                context: context,
                builder: (context) => AlertDialog(
                  title: const Text('Rename List'),
                  content: TextField(
                    autofocus: true,
                    decoration: const InputDecoration(
                      hintText: 'Enter new title',
                    ),
                    onSubmitted: (value) {
                      if (value.isNotEmpty) {
                        widget.dataService.updateTaskListTitle(
                          taskList.id,
                          value,
                        );
                        Navigator.pop(context);
                      }
                    },
                  ),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.pop(context),
                      child: const Text('CANCEL'),
                    ),
                  ],
                ),
              );
            },
          ),
          IconButton(
            icon: Icon(taskList.archived ? Icons.unarchive : Icons.archive),
            onPressed: () {
              if (taskList.archived) {
                widget.dataService.unarchiveTaskList(taskList.id);
                // TODO: Navigate to Lists page
              } else {
                widget.dataService.archiveTaskList(taskList.id);
                // TODO: Navigate to ArchivedListsPage
              }
            },
          ),
          Chip(
            label: Text(
              taskList.category.name,
              style: const TextStyle(color: Colors.black),
            ),
            backgroundColor: const Color(0xff7faad0)
          )
        ],
      ),
      body: SingleChildScrollView(
        child: TaskListWidget(
          dataService: widget.dataService,
          taskListId: widget.taskListId,
        ),
      ),
    );
  }
}
