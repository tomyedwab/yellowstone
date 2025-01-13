import 'package:flutter/material.dart';
import '../services/mock_data_service.dart';
import '../models/task_list.dart';
import 'task_list_view.dart';

class ArchivedListsPage extends StatefulWidget {
  const ArchivedListsPage({super.key});

  @override
  State<ArchivedListsPage> createState() => _ArchivedListsPageState();
}

class _ArchivedListsPageState extends State<ArchivedListsPage> {
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
    final taskLists = _mockDataService.getTaskLists(includeArchived: true)
        .where((list) => list.archived)
        .toList();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Archived Lists'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: ListView.builder(
        itemCount: taskLists.length,
        itemBuilder: (context, index) {
          final taskList = taskLists[index];
          return Card(
            margin: const EdgeInsets.all(8.0),
            child: ListTile(
              title: Text(taskList.title),
              subtitle: Text('${taskList.taskIds.length} tasks'),
              trailing: Chip(
                label: Text(
                  taskList.category.name,
                  style: const TextStyle(color: Colors.white),
                ),
                backgroundColor: taskList.category == TaskListCategory.template
                    ? Colors.blue
                    : Colors.green,
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
    );
  }
}
