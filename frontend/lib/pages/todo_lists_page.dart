import 'package:flutter/material.dart';
import '../services/mock_data_service.dart';
import '../models/task_list.dart';
import 'task_list_view.dart';

class ToDoListsPage extends StatefulWidget {
  const ToDoListsPage({super.key});

  @override
  State<ToDoListsPage> createState() => _ToDoListsPageState();
}

class _ToDoListsPageState extends State<ToDoListsPage> {
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
    final taskLists = _mockDataService.getTaskLists()
        .where((list) => list.category == TaskListCategory.toDoList)
        .toList();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Active Lists'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: Column(
        children: [
          Expanded(
            child: ReorderableListView.builder(
              itemCount: taskLists.length,
              onReorder: (oldIndex, newIndex) {
                final taskLists = _mockDataService.getTaskLists()
                    .where((list) => list.category == TaskListCategory.toDoList)
                    .toList();
                final movedList = taskLists[oldIndex];
                
                // If newIndex is 0, place at start
                if (newIndex == 0) {
                  _mockDataService.reorderTaskLists(movedList.id, null);
                } else {
                  // Otherwise place after the item that's now at newIndex-1
                  final afterList = taskLists[newIndex - 1];
                  _mockDataService.reorderTaskLists(movedList.id, afterList.id);
                }
              },
              itemBuilder: (context, index) {
                final taskList = taskLists[index];
                return Card(
                  key: ValueKey(taskList.id),
                  margin: const EdgeInsets.all(8.0),
                  child: ListTile(
                    title: Text(taskList.title),
                    subtitle: Text('${taskList.taskIds.length} tasks'),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Chip(
                          label: Text(
                            taskList.category.name,
                            style: const TextStyle(color: Colors.white),
                          ),
                          backgroundColor: taskList.category == TaskListCategory.template
                              ? Colors.blue
                              : Colors.green,
                        ),
                        IconButton(
                          icon: const Icon(Icons.archive),
                          onPressed: () {
                            _mockDataService.archiveTaskList(taskList.id);
                          },
                        ),
                      ],
                    ),
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => TaskListView(taskListId: taskList.id),
                        ),
                      );
                    },
                  ),
                );
              },
            ),
          ),
          Card(
            margin: const EdgeInsets.all(8.0),
            child: ListTile(
              leading: const Icon(Icons.add),
              title: const Text('Create new list'),
              onTap: () {
                showDialog(
                  context: context,
                  builder: (context) => AlertDialog(
                    title: const Text('Create New List'),
                    content: TextField(
                      autofocus: true,
                      decoration: const InputDecoration(
                        hintText: 'Enter list title',
                      ),
                      onSubmitted: (value) {
                        if (value.isNotEmpty) {
                          _mockDataService.createTaskList(
                            value,
                            TaskListCategory.toDoList,
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
          ),
        ],
      ),
    );
  }
}
