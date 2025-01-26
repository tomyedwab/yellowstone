import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../services/rest_data_service.dart';
import '../models/task_list.dart';
import 'task_list_view.dart';

class ArchivedListsPage extends StatefulWidget {
  final RestDataService dataService;
  final int? selectedListId;
  const ArchivedListsPage({super.key, required this.dataService, this.selectedListId});

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

    // TODO: Sort tasks by category and add headings
    return Scaffold(
      appBar: AppBar(
        title: const Text('Archived Lists'),
      ),
      body: ListView.builder(
        itemCount: taskLists.length,
        itemBuilder: (context, index) {
          final taskList = taskLists[index];
          return Container(
            key: ValueKey(taskList.id),
            margin: const EdgeInsets.only(left: 16.0, right: 32.0, top: 4.0, bottom: 4.0),
            decoration: BoxDecoration(
              color: widget.selectedListId != null && widget.selectedListId == taskList.id ? const Color.fromARGB(255, 49, 65, 80) : null,
              borderRadius: BorderRadius.circular(8),
              border: const Border(
                bottom: BorderSide(
                  color: Color(0xff182631),
                  width: 1.5,
                ),
              ),
            ),
            child: ListTile(
              title: Text(taskList.title),
              subtitle: Text('${taskList.taskIds.length} tasks'),
              trailing: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  IconButton(
                    icon: const Icon(Icons.unarchive),
                    onPressed: () {
                      widget.dataService.unarchiveTaskList(taskList.id);
                    },
                  ),
                ],
              ),
              onTap: () {
                context.go('/archived/list/${taskList.id}');
              },
            ),
          );
        },
      ),
    );
  }
}
