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
        _taskLists = lists.where((list) => list.category == TaskListCategory.toDoList && !list.archived).toList();
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
                  await widget.dataService.reorderTaskLists(movedList.id, null);
                } else {
                  // Otherwise place after the item that's now at newIndex-1
                  final afterList = _taskLists[newIndex - 1];
                  await widget.dataService.reorderTaskLists(movedList.id, afterList.id);
                }
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
