import 'package:flutter/material.dart';
import '../services/mock_data_service.dart';
import '../models/task_list.dart';
import 'task_list_view.dart';

class TemplatesPage extends StatefulWidget {
  const TemplatesPage({super.key});

  @override
  State<TemplatesPage> createState() => _TemplatesPageState();
}

class _TemplatesPageState extends State<TemplatesPage> {
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
    final templates = _mockDataService.getTaskLists()
        .where((list) => list.category == TaskListCategory.template)
        .toList();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Templates'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: ListView.builder(
        itemCount: templates.length,
        itemBuilder: (context, index) {
          final template = templates[index];
          return Card(
            margin: const EdgeInsets.all(8.0),
            child: ListTile(
              title: Text(template.title),
              subtitle: Text('${template.taskIds.length} tasks'),
              trailing: Chip(
                label: Text(
                  template.category.name,
                  style: const TextStyle(color: Colors.white),
                ),
                backgroundColor: Colors.blue,
              ),
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => TaskListView(taskListId: template.id),
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
