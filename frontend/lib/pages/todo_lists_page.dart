import 'package:flutter/material.dart';
import '../services/rest_data_service.dart';
import '../models/task_list.dart';
import 'task_list_view.dart';

class ToDoListsPage extends StatefulWidget {
  final RestDataService dataService;
  const ToDoListsPage({
    super.key,
    required this.dataService,
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
    final taskLists = _taskLists;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Active Lists'),
      ),
      body: Column(
        children: [
          Expanded(
            child: ReorderableListView.builder(
              itemCount: taskLists.length,
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
              itemBuilder: (context, index) {
                final taskList = taskLists[index];
                return Container(
                  key: ValueKey(taskList.id),
                  margin: const EdgeInsets.only(left: 16.0, right: 32.0, top: 4.0, bottom: 4.0),
                  decoration: const BoxDecoration(
                    border: Border(
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
          ),
          Card(
            margin: const EdgeInsets.all(8.0),
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
      ),
    );
  }
}
