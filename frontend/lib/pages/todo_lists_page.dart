import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../services/rest_data_service.dart';
import '../models/task_list.dart';
import '../services/responsive_service.dart';

class ToDoListsPage extends StatefulWidget {
  final RestDataService dataService;
  final ResponsiveService responsiveService;
  final int? selectedListId;
  const ToDoListsPage({
    super.key,
    required this.dataService,
    required this.responsiveService,
    this.selectedListId,
  });

  @override
  State<ToDoListsPage> createState() => _ToDoListsPageState();
}

class _ToDoListsPageState extends State<ToDoListsPage> {
  List<TaskList> _taskLists = [];
  Map<int, TaskListMetadata> _taskListMetadata = {};

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
      final metadata = await widget.dataService.getTaskListMetadata();
      setState(() {
        _taskLists = lists.where((list) => list.category == TaskListCategory.toDoList && !list.archived).toList();
        _taskListMetadata = Map.fromEntries(metadata.map((m) => MapEntry(m.id, m)));
      });
    } catch (e) {
      // TODO: Handle error
      print('Error loading task lists: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(child: Column(
      children: [
        SizedBox(
          height: MediaQuery.of(context).size.height - widget.responsiveService.listsViewBottomBoxSize,
          child: ReorderableListView(
            onReorder: (oldIndex, newIndex) async {
              final movedList = _taskLists[oldIndex];
              
              // If newIndex is 0, place at start
              if (newIndex == 0) {
                await widget.dataService.reorderTaskList(movedList.id, null);
              } else {
                // Otherwise place after the item that's now at newIndex-1
                final afterList = _taskLists[newIndex - 1];
                await widget.dataService.reorderTaskList(movedList.id, afterList.id);
              }
            },
            children: [
              for (final taskList in _taskLists)
                KeyedSubtree(
                  key: ValueKey(taskList.id),
                  child: Container(
                    margin: const EdgeInsets.symmetric(horizontal: 4.0, vertical: 4.0),
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
                      subtitle: Text('${_taskListMetadata[taskList.id]?.total ?? 0} tasks, ${_taskListMetadata[taskList.id]?.completed ?? 0} completed'),
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          IconButton(
                            icon: const Icon(Icons.archive),
                            onPressed: () {
                              widget.dataService.archiveTaskList(taskList.id);
                            },
                          ),
                          if (widget.responsiveService.reorderableHandlesVisible) const SizedBox(width: 12),
                        ],
                      ),
                      onTap: () {
                        context.go('/list/${taskList.id}');
                      },
                    ),
                  ),
                )
            ],
          ),
        ),
        Card(
          margin: const EdgeInsets.all(8.0),
          elevation: 0,
          child: ListTile(
            leading: const Icon(Icons.add),
            title: const Text('Create new list'),
            onTap: () {
              showDialog(
                context: context,
                builder: (context) => AlertDialog(
                  title: const Text('Create New List'),
                  content: TextField(
                    autofocus: true,
                    decoration: const InputDecoration(
                      hintText: 'Enter list title',
                    ),
                    onSubmitted: (value) {
                      if (value.isNotEmpty) {
                        widget.dataService.createTaskList(
                          value,
                          TaskListCategory.toDoList,
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
    ));
  }
}

