import 'package:flutter/material.dart';
import '../services/rest_data_service.dart';
import '../models/task_list.dart';
import 'task_list_view.dart';

class TemplatesPage extends StatefulWidget {
  final RestDataService dataService;
  const TemplatesPage({super.key, required this.dataService});

  @override
  State<TemplatesPage> createState() => _TemplatesPageState();
}

class _TemplatesPageState extends State<TemplatesPage> {
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
        _taskLists = lists.where((list) => list.category == TaskListCategory.template && !list.archived).toList();
        _taskListMetadata = Map.fromEntries(metadata.map((m) => MapEntry(m.id, m)));
      });
    } catch (e) {
      // TODO: Handle error
      print('Error loading task lists: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final templates = _taskLists;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Templates'),
      ),
      body: Column(
        children: [
          Expanded(
            child: ReorderableListView.builder(
              itemCount: templates.length,
              onReorder: (oldIndex, newIndex) {
                final movedList = _taskLists[oldIndex];
                
                // If newIndex is 0, place at start
                if (newIndex == 0) {
                  widget.dataService.reorderTaskList(movedList.id, null);
                } else {
                  // Otherwise place after the item that's now at newIndex-1
                  final afterList = _taskLists[newIndex - 1];
                  widget.dataService.reorderTaskList(movedList.id, afterList.id);
                }
              },
              itemBuilder: (context, index) {
                final template = templates[index];
                return Container(
                  key: ValueKey(template.id),
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
                    title: Text(template.title),
                    subtitle: Text('${_taskListMetadata[template.id]?.total ?? 0} tasks'),
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
                          builder: (context) => TaskListView(
                            dataService: widget.dataService,
                            taskListId: template.id,
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
                          widget.dataService.createTaskList(
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
