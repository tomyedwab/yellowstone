import 'package:flutter/material.dart';
import 'services/mock_data_service.dart';
import 'models/task_list.dart';
import 'pages/task_list_view.dart';
import 'pages/archived_lists_page.dart';
import 'pages/templates_page.dart';

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
      home: const RootPage(),
    );
  }
}

class RootPage extends StatefulWidget {
  const RootPage({super.key});

  @override
  State<RootPage> createState() => _RootPageState();
}

class _RootPageState extends State<RootPage> {
  int _selectedIndex = 0;

  static const List<Widget> _pages = [
    HomePage(),
    TemplatesPage(),
    ArchivedListsPage(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: _pages[_selectedIndex],
      bottomNavigationBar: NavigationBar(
        onDestinationSelected: (int index) {
          setState(() {
            _selectedIndex = index;
          });
        },
        selectedIndex: _selectedIndex,
        destinations: const <NavigationDestination>[
          NavigationDestination(
            icon: Icon(Icons.list),
            label: 'Lists',
          ),
          NavigationDestination(
            icon: Icon(Icons.copy_all),
            label: 'Templates',
          ),
          NavigationDestination(
            icon: Icon(Icons.archive),
            label: 'Archived',
          ),
        ],
      ),
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
    final taskLists = _mockDataService.getTaskLists()
        .where((list) => list.category == TaskListCategory.toDoList)
        .toList();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Active Lists'),
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
    );
  }
}
