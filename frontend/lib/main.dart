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

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    final mockDataService = MockDataService();
    final taskLists = mockDataService.getTaskLists();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Task Lists'),
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
              subtitle: Text('${taskList.tasks.length} tasks'),
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
                    builder: (context) => TaskListView(taskList: taskList),
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
