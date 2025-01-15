import 'package:flutter/material.dart';
import '../services/rest_data_service.dart';
import '../widgets/task_list_widget.dart';
import '../models/task_list.dart';

class TaskListView extends StatefulWidget {
  final int taskListId;

  const TaskListView({
    super.key,
    required this.taskListId,
  });

  @override
  State<TaskListView> createState() => _TaskListViewState();
}

class _TaskListViewState extends State<TaskListView> {
  final RestDataService _restDataService = RestDataService();
  TaskList? _taskList;

  @override
  void initState() {
    super.initState();
    _restDataService.addListener(_onDataChanged);
    _loadTaskList();
  }

  @override
  void dispose() {
    _restDataService.removeListener(_onDataChanged);
    super.dispose();
  }

  void _onDataChanged() {
    _loadTaskList();
  }

  Future<void> _loadTaskList() async {
    try {
      final taskList = await _restDataService.getTaskListById(widget.taskListId);
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
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: SingleChildScrollView(
        child: TaskListWidget(taskListId: widget.taskListId),
      ),
    );
  }
}
