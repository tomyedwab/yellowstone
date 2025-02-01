import 'package:flutter/material.dart';
import '../services/rest_data_service.dart';
import '../services/responsive_service.dart';
import '../widgets/task_list_widget.dart';
import '../models/task_list.dart';

class TaskListView extends StatefulWidget {
  final RestDataService dataService;
  final ResponsiveService responsiveService;  
  final int taskListId;
  final String taskListPrefix;
  final int? selectedTaskId;

  const TaskListView({
    super.key,
    required this.dataService,
    required this.responsiveService,
    required this.taskListId,
    required this.taskListPrefix,
    required this.selectedTaskId,
  });

  @override
  State<TaskListView> createState() => _TaskListViewState();
}

class _TaskListViewState extends State<TaskListView> {
  TaskList? _taskList;
  bool _isSelectionMode = false;
  final Set<int> _selectedTaskIds = {};
  final GlobalKey<TaskListWidgetState> _taskListKey = GlobalKey();

  @override
  void initState() {
    super.initState();
    widget.dataService.addListener(_onDataChanged);
    _loadTaskList();
  }

  @override
  void dispose() {
    widget.dataService.removeListener(_onDataChanged);
    super.dispose();
  }

  void _onDataChanged() {
    _loadTaskList();
  }

  void _toggleSelectionMode() {
    setState(() {
      _isSelectionMode = !_isSelectionMode;
      if (!_isSelectionMode) {
        _selectedTaskIds.clear();
      }
    });
  }

  void _toggleTaskSelection(int taskId) {
    setState(() {
      if (_selectedTaskIds.contains(taskId)) {
        _selectedTaskIds.remove(taskId);
      } else {
        _selectedTaskIds.add(taskId);
      }
    });
  }

  Future<void> _loadTaskList() async {
    try {
      final taskList = await widget.dataService.getTaskListById(widget.taskListId);
      setState(() {
        _taskList = taskList;
      });
    } catch (e) {
      // TODO: Handle error
      print('Error loading task list: $e');
    }
  }

  Future<void> _showListSelectionDialog({required bool isCopy}) async {
    final lists = await widget.dataService.getAllTaskLists();
    if (!mounted) return;

    await showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(isCopy ? 'Add to List' : 'Move to List'),
        content: SizedBox(
          width: double.maxFinite,
          child: ListView.builder(
            shrinkWrap: true,
            itemCount: lists.length,
            itemBuilder: (context, index) {
              final list = lists[index];
              if (list.id == widget.taskListId) return const SizedBox.shrink();
              
              return ListTile(
                title: Text(list.title),
                onTap: () {
                  if (isCopy) {
                    widget.dataService.copyTasksToList(_selectedTaskIds, list.id);
                  } else {
                    widget.dataService.moveTasksToList(_selectedTaskIds, widget.taskListId, list.id);
                  }
                  Navigator.pop(context);
                  _toggleSelectionMode();
                },
              );
            },
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('CANCEL'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_taskList == null) {
      return const Scaffold(
        body: Center(
          child: CircularProgressIndicator(),
        ),
      );
    }
    
    final taskList = _taskList!;

    final icons = [
      if (_isSelectionMode) ...[
        IconButton(
          icon: const Icon(Icons.select_all),
          onPressed: _showBatchSelectionDialog,
          tooltip: 'Batch select',
        ),
        IconButton(
          icon: const Icon(Icons.library_add),
          onPressed: () => _showListSelectionDialog(isCopy: true),
          tooltip: 'Add to another list',
        ),
        IconButton(
          icon: const Icon(Icons.drive_file_move),
          onPressed: () => _showListSelectionDialog(isCopy: false),
          tooltip: 'Move to another list',
        ),
      ],
      if (!_isSelectionMode) ...[
        IconButton(
          icon: const Icon(Icons.edit),
          onPressed: () {
            showDialog(
              context: context,
              builder: (context) => AlertDialog(
                title: const Text('Rename List'),
                content: TextField(
                  autofocus: true,
                  decoration: const InputDecoration(
                    hintText: 'Enter new title',
                  ),
                  onSubmitted: (value) {
                    if (value.isNotEmpty) {
                      widget.dataService.updateTaskListTitle(
                        taskList.id,
                        value,
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
        IconButton(
          icon: Icon(taskList.archived ? Icons.unarchive : Icons.archive),
          onPressed: () {
            if (taskList.archived) {
              widget.dataService.unarchiveTaskList(taskList.id);
              // TODO: Navigate to Lists page
            } else {
              widget.dataService.archiveTaskList(taskList.id);
              // TODO: Navigate to ArchivedListsPage
            }
          },
        ),
      ],
      IconButton(
        icon: Icon(_isSelectionMode ? Icons.close : Icons.checklist),
        onPressed: _toggleSelectionMode,
        tooltip: _isSelectionMode ? 'Exit selection mode' : 'Enter selection mode',
      ),
    ];

    final body = TaskListWidget(
      key: _taskListKey,
      dataService: widget.dataService,
      responsiveService: widget.responsiveService,
      taskListId: widget.taskListId,
      taskListPrefix: widget.taskListPrefix,
      selectedTaskId: widget.selectedTaskId,
      isSelectionMode: _isSelectionMode,
      selectedTaskIds: _selectedTaskIds,
      onTaskSelectionChanged: _toggleTaskSelection,
    );
    
    if (widget.responsiveService.layoutType == LayoutType.horizontal) {
      return Scaffold(
        resizeToAvoidBottomInset: true,
        body: Column(children: [
          Row(
            children: [
              Padding(
                padding: const EdgeInsets.all(16.0),
                child: Text(
                  _taskList!.title,
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
              ),
              const Spacer(),
              ...icons,
            ],
          ),
          
          Expanded(child: body),
        ],),
      );
    }
    return Scaffold(
      resizeToAvoidBottomInset: true,
      appBar: AppBar(
        actions: icons,
      ),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.all(16.0),
          child: Text(
            _taskList!.title,
            style: Theme.of(context).textTheme.headlineSmall,
          ),
        ),
        Expanded(child: body),
      ],),
    );
  }

  Future<void> _showBatchSelectionDialog() async {
    await showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Batch Selection'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.select_all),
              title: const Text('Select all'),
              onTap: () {
                _selectAllTasks();
                Navigator.pop(context);
              },
            ),
            ListTile(
              leading: const Icon(Icons.check_circle),
              title: const Text('Select completed'),
              onTap: () {
                _selectCompletedTasks();
                Navigator.pop(context);
              },
            ),
            ListTile(
              leading: const Icon(Icons.radio_button_unchecked),
              title: const Text('Select uncompleted'),
              onTap: () {
                _selectUncompletedTasks();
                Navigator.pop(context);
              },
            ),
            ListTile(
              leading: const Icon(Icons.clear_all),
              title: const Text('Select none'),
              onTap: () {
                _clearSelection();
                Navigator.pop(context);
              },
            ),
          ],
        ),
      ),
    );
  }

  void _selectAllTasks() {
    final tasks = _taskListKey.currentState?.tasks;
    if (tasks != null) {
      setState(() {
        _selectedTaskIds.clear();
        _selectedTaskIds.addAll(tasks.map((task) => task.id));
      });
    }
  }

  void _selectCompletedTasks() {
    final tasks = _taskListKey.currentState?.tasks;
    if (tasks != null) {
      setState(() {
        _selectedTaskIds.clear();
        _selectedTaskIds.addAll(
          tasks.where((task) => task.isCompleted).map((task) => task.id),
        );
      });
    }
  }

  void _selectUncompletedTasks() {
    final tasks = _taskListKey.currentState?.tasks;
    if (tasks != null) {
      setState(() {
        _selectedTaskIds.clear();
        _selectedTaskIds.addAll(
          tasks.where((task) => !task.isCompleted).map((task) => task.id),
        );
      });
    }
  }

  void _clearSelection() {
    setState(() {
      _selectedTaskIds.clear();
    });
  }
}
