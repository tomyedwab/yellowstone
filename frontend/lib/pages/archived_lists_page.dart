import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../services/rest_data_service.dart';
import '../models/task_list.dart';
import 'task_list_view.dart';
import '../services/responsive_service.dart';

class ArchivedListsPage extends StatefulWidget {
  final RestDataService dataService;
  final int? selectedListId;
  final ResponsiveService responsiveService;
  const ArchivedListsPage({super.key, required this.dataService, this.selectedListId, required this.responsiveService});

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
    // TODO: Sort tasks by category and add headings
    return ListView.builder(
      itemCount: _taskLists.length,
      itemBuilder: (context, index) {
        final taskList = _taskLists[index];
        return Container(
          key: ValueKey(taskList.id),
          margin: const EdgeInsets.symmetric(horizontal: 0.0, vertical: 0.0),
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
    );
  }
}
