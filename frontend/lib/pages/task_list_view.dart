import 'package:flutter/material.dart';
import '../services/mock_data_service.dart';
import '../widgets/task_list_widget.dart';

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
