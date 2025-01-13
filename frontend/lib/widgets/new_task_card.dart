import 'package:flutter/material.dart';
import '../services/mock_data_service.dart';

class NewTaskCard extends StatefulWidget {
  final int taskListId;

  const NewTaskCard({
    super.key,
    required this.taskListId,
  });

  @override
  State<NewTaskCard> createState() => _NewTaskCardState();
}

class _NewTaskCardState extends State<NewTaskCard> {
  final MockDataService _mockDataService = MockDataService();
  final TextEditingController _titleController = TextEditingController();

  @override
  void dispose() {
    _titleController.dispose();
    super.dispose();
  }

  void _createTask() {
    final title = _titleController.text.trim();
    if (title.isNotEmpty) {
      _mockDataService.createTask(widget.taskListId, title);
      _titleController.clear();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.all(8.0),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
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
