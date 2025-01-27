import 'package:flutter/material.dart';
import '../models/task_history.dart';
import '../services/rest_data_service.dart';
import '../services/responsive_service.dart';

class TaskHistoryPage extends StatefulWidget {
  final int taskId;
  final ResponsiveService responsiveService;

  const TaskHistoryPage({super.key, required this.taskId, required this.responsiveService});

  @override
  State<TaskHistoryPage> createState() => _TaskHistoryPageState();
}

class _TaskHistoryPageState extends State<TaskHistoryPage> {
  final _commentController = TextEditingController();
  List<TaskHistory> _history = [];
  String _title = "";
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  @override
  void dispose() {
    _commentController.dispose();
    super.dispose();
  }

  Future<void> _loadHistory() async {
    try {
      final history = await RestDataService().getTaskHistory(widget.taskId);
      setState(() {
        _history = history.history;
        _title = history.title;
        _isLoading = false;
      });
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error loading history: $e')),
        );
      }
    }
  }

  Future<void> _addComment() async {
    final comment = _commentController.text.trim();
    if (comment.isEmpty) return;

    try {
      await RestDataService().addTaskComment(widget.taskId, comment);
      _commentController.clear();
      await _loadHistory(); // Reload history to show new comment
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error adding comment: $e')),
        );
      }
    }
  }

  String _formatDateTime(DateTime dateTime) {
    return '${dateTime.year}-${dateTime.month.toString().padLeft(2, '0')}-${dateTime.day.toString().padLeft(2, '0')} '
           '${dateTime.hour.toString().padLeft(2, '0')}:${dateTime.minute.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final body = Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(16.0),
          child: Text(
            _title,
            style: Theme.of(context).textTheme.headlineSmall,
          ),
        ),
        Expanded(
          child: _isLoading
              ? const Center(child: CircularProgressIndicator())
              : ListView.builder(
                  itemCount: _history.length,
                  itemBuilder: (context, index) {
                    final entry = _history[index];
                    return Card(
                      margin: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 8,
                      ),
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  _formatDateTime(entry.createdAt),
                                  style: Theme.of(context).textTheme.bodySmall,
                                ),
                                const SizedBox(height: 8),
                                if (entry.systemComment != "") ...[
                                  Container(
                                    width: double.infinity,
                                    padding: const EdgeInsets.all(8),
                                    decoration: BoxDecoration(
                                      color: Theme.of(context).colorScheme.surfaceVariant,
                                      borderRadius: BorderRadius.circular(8),
                                    ),
                                    child: Text(
                                      entry.systemComment,
                                      softWrap: true,
                                    ),
                                  ),
                                ],
                                if (entry.userComment != null) ...[
                                  Container(
                                    width: double.infinity,
                                    padding: const EdgeInsets.all(8),
                                    decoration: BoxDecoration(
                                      color: Theme.of(context).colorScheme.surfaceVariant,
                                      borderRadius: BorderRadius.circular(8),
                                    ),
                                    child: Text(
                                      entry.userComment!,
                                      softWrap: true,
                                    ),
                                  ),
                                ],
                              ],
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
        ),
        Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _commentController,
                  decoration: const InputDecoration(
                    hintText: 'Add a comment...',
                    border: OutlineInputBorder(),
                  ),
                  maxLines: null,
                ),
              ),
              const SizedBox(width: 16),
              IconButton.filled(
                onPressed: _addComment,
                icon: const Icon(Icons.send, color: Colors.black),
              ),
            ],
          ),
        ),
      ],
    );

    if (widget.responsiveService.layoutType == LayoutType.horizontal) {
      return body;
    }

    return Scaffold(
      appBar: AppBar(),
      body: body,
    );
  }
} 