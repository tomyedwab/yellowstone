import 'package:flutter/material.dart';
import '../models/task_list.dart';
import '../widgets/task_list_widget.dart';

class TaskListView extends StatelessWidget {
  final TaskList taskList;

  const TaskListView({
    super.key,
    required this.taskList,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(taskList.title),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: SingleChildScrollView(
        child: TaskListWidget(taskList: taskList),
      ),
    );
  }
}
