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
      body: Column(
        children: [
          Expanded(
            child: ReorderableListView.builder(
              itemCount: templates.length,
              onReorder: (oldIndex, newIndex) {
                final templates = _mockDataService.getTaskLists()
                    .where((list) => list.category == TaskListCategory.template)
                    .toList();
                final movedList = templates[oldIndex];
                
                // If newIndex is 0, place at start
                if (newIndex == 0) {
                  _mockDataService.reorderTaskLists(movedList.id, null);
                } else {
                  // Otherwise place after the item that's now at newIndex-1
                  final afterList = templates[newIndex - 1];
                  _mockDataService.reorderTaskLists(movedList.id, afterList.id);
                }
              },
              itemBuilder: (context, index) {
          final template = templates[index];
          return Card(
            key: ValueKey(template.id),
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
          ),
          Card(
            margin: const EdgeInsets.all(8.0),
            child: ListTile(
              leading: const Icon(Icons.add),
              title: const Text('Create new template'),
              onTap: () {
                showDialog(
                  context: context,
                  builder: (context) => AlertDialog(
                    title: const Text('Create New Template'),
                    content: TextField(
                      autofocus: true,
                      decoration: const InputDecoration(
                        hintText: 'Enter template title',
                      ),
                      onSubmitted: (value) {
                        if (value.isNotEmpty) {
                          _mockDataService.createTaskList(
                            value,
                            TaskListCategory.template,
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
