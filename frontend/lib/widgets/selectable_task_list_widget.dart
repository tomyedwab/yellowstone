import 'package:flutter/material.dart';
import '../models/task.dart';
import '../models/task_list.dart';
import '../services/rest_data_service.dart';
import '../services/responsive_service.dart';
import 'task_card.dart';

class SelectableTaskListWidget extends StatelessWidget {
  final RestDataService dataService;
  final ResponsiveService responsiveService;
  final int taskListId;
  final String taskListPrefix;
  final List<Task> tasks;
  final TaskListCategory category;
  final Map<int, List<TaskLabel>> taskLabels;
  final Map<int, TaskRecentComment>? recentComments;
  final Set<int> selectedTaskIds;
  final void Function(int) onTaskSelectionChanged;

  const SelectableTaskListWidget({
    super.key,
    required this.dataService,
    required this.responsiveService,
    required this.taskListId,
    required this.taskListPrefix,
    required this.tasks,
    required this.category,
    required this.taskLabels,
    required this.recentComments,
    required this.selectedTaskIds,
    required this.onTaskSelectionChanged,
  });

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Column(
        children: [
          for (final task in tasks)
            TaskCard(
              key: ValueKey(task.id),
              dataService: dataService,
              responsiveService: responsiveService,
              task: task,
              taskListId: taskListId,
              taskListPrefix: taskListPrefix,
              category: category,
              labels: taskLabels[task.id],
              recentComment: recentComments?[task.id],
              onComplete: () {
                dataService.markTaskComplete(
                  task.id,
                  !task.isCompleted,
                );
              },
              isSelectionMode: true,
              isSelected: selectedTaskIds.contains(task.id),
              isHighlighted: false,
              onSelectionChanged: (selected) => onTaskSelectionChanged(task.id),
            ),
        ],
      ),
    );
  }
} 