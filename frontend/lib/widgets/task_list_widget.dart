import 'package:flutter/material.dart';
import '../models/task.dart';
import '../models/task_list.dart';
import '../services/rest_data_service.dart';
import '../services/responsive_service.dart';
import 'selectable_task_list_widget.dart';
import 'reorderable_task_list_widget.dart';

class TaskListWidget extends StatefulWidget {
  final RestDataService dataService;
  final ResponsiveService responsiveService;
  final int taskListId;
  final String taskListPrefix;
  final bool isSelectionMode;
  final Set<int> selectedTaskIds;
  final int? selectedTaskId;
  final void Function(int)? onTaskSelectionChanged;

  const TaskListWidget({
    super.key,
    required this.dataService,
    required this.responsiveService,
    required this.taskListId,
    required this.taskListPrefix,
    this.isSelectionMode = false,
    required this.selectedTaskIds,
    required this.selectedTaskId,
    required this.onTaskSelectionChanged,
  });

  @override
  State<TaskListWidget> createState() => TaskListWidgetState();
}

class TaskListWidgetState extends State<TaskListWidget> {

  @override
  Widget build(BuildContext context) {

  }
}
