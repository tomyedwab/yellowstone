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
                _mockDataService.reorderTaskLists(oldIndex, newIndex);
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
                // TODO: Implement new list creation
              },
            ),
          ),
        ],
      ),
    );
  }
}
