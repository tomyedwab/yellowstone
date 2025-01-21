import 'package:flutter/material.dart';
import '../services/rest_data_service.dart';
import '../models/task_list.dart';
import 'task_list_view.dart';

class ArchivedListsPage extends StatefulWidget {
  final RestDataService dataService;
  const ArchivedListsPage({super.key, required this.dataService});

  @override
  State<ArchivedListsPage> createState() => _ArchivedListsPageState();
}

class _ArchivedListsPageState extends State<ArchivedListsPage> {
  List<TaskList> _taskLists = [];

  @override
  void initState() {
    super.initState();
    widget.dataService.addListener(_onDataChanged);
    _loadTaskLists();
  }

  @override
  void dispose() {
    widget.dataService.removeListener(_onDataChanged);
    super.dispose();
  }

  void _onDataChanged() {
    _loadTaskLists();
  }

  Future<void> _loadTaskLists() async {
    try {
      final lists = await widget.dataService.getTaskLists();
      setState(() {
        _taskLists = lists.where((list) => list.archived).toList();
      });
    } catch (e) {
      // TODO: Handle error
      print('Error loading task lists: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final taskLists = _taskLists;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Archived Lists'),
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
                    icon: const Icon(Icons.unarchive),
                    onPressed: () {
                      widget.dataService.unarchiveTaskList(taskList.id);
                    },
                  ),
                ],
              ),
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => TaskListView(
                      dataService: widget.dataService,
                      taskListId: taskList.id,
                    ),
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
