import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../services/rest_data_service.dart';
import '../models/task_list.dart';
import 'task_list_view.dart';
import '../services/responsive_service.dart';

class LabelsPage extends StatefulWidget {
  final RestDataService dataService;
  final int? selectedListId;
  final ResponsiveService responsiveService;
  const LabelsPage({super.key, required this.dataService, this.selectedListId, required this.responsiveService});

  @override
  State<LabelsPage> createState() => _LabelsPageState();
}

class _LabelsPageState extends State<LabelsPage> {
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
        _taskLists = lists.where((list) => list.category == TaskListCategory.label && !list.archived).toList();
        _taskListMetadata = Map.fromEntries(metadata.map((m) => MapEntry(m.id, m)));
      });
    } catch (e) {
      // TODO: Handle error
      print('Error loading task lists: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
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
              for (final label in _taskLists)
                KeyedSubtree(
                  key: ValueKey(label.id),
                  child: Container(
                    margin: const EdgeInsets.symmetric(horizontal: 4.0, vertical: 4.0),
                    decoration: BoxDecoration(
                      color: widget.selectedListId != null && widget.selectedListId == label.id ? const Color.fromARGB(255, 49, 65, 80) : null,
                      borderRadius: BorderRadius.circular(8),
                      border: const Border(
                        bottom: BorderSide(
                          color: Color(0xff182631),
                          width: 1.5,
                        ),
                      ),
                    ),
                    child: ListTile(
                      title: Text(label.title),
                      subtitle: Text('${_taskListMetadata[label.id]?.total ?? 0} tasks, ${_taskListMetadata[label.id]?.completed ?? 0} completed'),
                      onTap: () {
                        context.go('/labels/list/${label.id}');
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
            title: const Text('Create new label'),
            onTap: () {
              showDialog(
                context: context,
                builder: (context) => AlertDialog(
                  title: const Text('Create New Label'),
                  content: TextField(
                    autofocus: true,
                    decoration: const InputDecoration(
                      hintText: 'Enter label title',
                    ),
                    onSubmitted: (value) {
                      if (value.isNotEmpty) {
                        widget.dataService.createTaskList(
                          value,
                          TaskListCategory.label,
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
    );
  }
}
