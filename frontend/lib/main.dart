import 'package:flutter/material.dart';
import 'services/mock_data_service.dart';
import 'models/task_list.dart';
import 'pages/task_list_view.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Task Lists',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
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
    final taskLists = _mockDataService.getTaskLists();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Task Lists'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: ReorderableListView.builder(
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
