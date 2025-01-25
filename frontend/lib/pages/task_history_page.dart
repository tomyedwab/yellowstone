import 'package:flutter/material.dart';
import '../models/task.dart';
import '../models/task_history.dart';
import '../services/rest_data_service.dart';

class TaskHistoryPage extends StatefulWidget {
  final Task task;

  const TaskHistoryPage({super.key, required this.task});

  @override
  State<TaskHistoryPage> createState() => _TaskHistoryPageState();
}

class _TaskHistoryPageState extends State<TaskHistoryPage> {
  final _commentController = TextEditingController();
  List<TaskHistory> _history = [];
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
      final history = await RestDataService().getTaskHistory(widget.task.id);
      setState(() {
        _history = history;
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
      await RestDataService().addTaskComment(widget.task.id, comment);
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
    return Scaffold(
      appBar: AppBar(
        title: Text('History - ${widget.task.title}'),
      ),
      body: Column(
        children: [
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
                              Row(
                                mainAxisAlignment: MainAxisAlignment.start,
                                children: [
                                  Text(
                                    _formatDateTime(entry.createdAt),
                                    style: Theme.of(context).textTheme.bodySmall,
                                  ),
                                  const SizedBox(width: 16),
                                  if (entry.systemComment != "") ...[
                                    Container(
                                      decoration: BoxDecoration(
                                        color: Theme.of(context).colorScheme.surfaceVariant,
                                        borderRadius: BorderRadius.circular(8),
                                      ),
                                      child: Text(entry.systemComment),
                                    ),
                                  ],
                                  if (entry.userComment != null) ...[
                                    Container(
                                      decoration: BoxDecoration(
                                        color: Theme.of(context).colorScheme.surfaceVariant,
                                        borderRadius: BorderRadius.circular(8),
                                      ),
                                      child: Text(entry.userComment!),
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
      ),
    );
  }
} 