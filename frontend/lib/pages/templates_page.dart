import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../services/rest_data_service.dart';
import '../models/task_list.dart';
import '../services/responsive_service.dart';

class TemplatesPage extends StatefulWidget {
  final RestDataService dataService;
  final int? selectedListId;
  final ResponsiveService responsiveService;
  const TemplatesPage({super.key, required this.dataService, this.selectedListId, required this.responsiveService});

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
    return SafeArea(child: Column(
      children: [
        SizedBox(
          height: MediaQuery.of(context).size.height - widget.responsiveService.listsViewBottomBoxSize,
          child: ReorderableListView(
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
            children: [
              for (final template in _taskLists)
                KeyedSubtree(
                  key: ValueKey(template.id),
                  child: Container(
                    margin: const EdgeInsets.symmetric(horizontal: 4.0, vertical: 4.0),
                    decoration: BoxDecoration(
                      color: widget.selectedListId != null && widget.selectedListId == template.id ? const Color.fromARGB(255, 49, 65, 80) : null,
                      borderRadius: BorderRadius.circular(8),
                      border: const Border(
                        bottom: BorderSide(
                          color: Color(0xff182631),
                          width: 1.5,
                        ),
                      ),
                    ),
                    child: ListTile(
                      title: Text(template.title),
                      subtitle: Text('${_taskListMetadata[template.id]?.total ?? 0} tasks'),
                      onTap: () {
                        context.go('/templates/list/${template.id}');
                      },
                    ),
                  ),
                ),
            ],
          ),
        ),
        Card(
          margin: const EdgeInsets.all(8.0),
          elevation: 0,
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
    ));
  }
}
