import 'package:flutter/material.dart';
import '../models/task.dart';
import '../models/task_list.dart';
import '../services/rest_data_service.dart';
import '../services/responsive_service.dart';
import 'task_card.dart';
import 'new_task_card.dart';

class ReorderableTaskListWidget extends StatelessWidget {
  final RestDataService dataService;
  final ResponsiveService responsiveService;
  final int taskListId;
  final String taskListPrefix;
  final List<Task> tasks;
  final TaskListCategory category;
  final Map<int, List<TaskLabel>> taskLabels;
  final Map<int, TaskRecentComment>? recentComments;
  final int? selectedTaskId;

  const ReorderableTaskListWidget({
    super.key,
    required this.dataService,
    required this.responsiveService,
    required this.taskListId,
    required this.taskListPrefix,
    required this.tasks,
    required this.category,
    required this.taskLabels,
    required this.recentComments,
    required this.selectedTaskId,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom + 8.0,
      ),
      child: Column(
        children: [
          Expanded(
            child: ReorderableListView(
              shrinkWrap: false,
              physics: const ClampingScrollPhysics(),
              padding: const EdgeInsets.symmetric(vertical: 20),
              onReorder: (oldIndex, newIndex) {
                if (oldIndex < newIndex) {
                  newIndex -= 1;
                }
                dataService.reorderTasks(
                  taskListId,
                  tasks[oldIndex].id,
                  newIndex == 0 ? null : tasks[newIndex - 1].id,
                );
              },
              children: [
                for (final task in tasks)
                  KeyedSubtree(
                    key: ValueKey(task.id),
                    child: TaskCard(
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
                      isSelectionMode: false,
                      isSelected: false,
                      isHighlighted: selectedTaskId == task.id,
                      onSelectionChanged: null,
                    ),
                  ),
              ],
            ),
          ),
          NewTaskCard(
            dataService: dataService,
            taskListId: taskListId,
          ),
        ],
      ),
    );
  }
} 