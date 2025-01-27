import 'package:flutter/material.dart';
import '../services/rest_data_service.dart';

class NewTaskCard extends StatefulWidget {
  final RestDataService dataService;
  final int taskListId;

  const NewTaskCard({
    super.key,
    required this.dataService,
    required this.taskListId,
  });

  @override
  State<NewTaskCard> createState() => _NewTaskCardState();
}

class _NewTaskCardState extends State<NewTaskCard> {
  final TextEditingController _titleController = TextEditingController();

  @override
  void dispose() {
    _titleController.dispose();
    super.dispose();
  }

  void _createTask() {
    final title = _titleController.text.trim();
    if (title.isNotEmpty) {
      widget.dataService.createTask(widget.taskListId, title);
      _titleController.clear();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.all(12.0),
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(0),
      ),
      child: Padding(
        padding: const EdgeInsets.only(left: 56.0, right: 16.0, top: 4.0, bottom: 4.0),
        child: Row(
          children: [
            Expanded(
              child: TextField(
                controller: _titleController,
                decoration: const InputDecoration(
                  hintText: 'Add a new task...',
                  border: InputBorder.none,
                ),
                onSubmitted: (_) => _createTask(),
              ),
            ),
            IconButton(
              icon: const Icon(Icons.add),
              onPressed: _createTask,
            ),
          ],
        ),
      ),
    );
  }
}
