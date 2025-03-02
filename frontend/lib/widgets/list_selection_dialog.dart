import 'package:flutter/material.dart';
import '../models/task_list.dart';

class ListSelectionDialog extends StatelessWidget {
  final List<TaskList> lists;
  final int currentListId;
  final bool isCopy;
  final bool keepOriginal;
  final Function(int targetListId, bool keepOriginal) onListSelected;

  const ListSelectionDialog({
    super.key,
    required this.lists,
    required this.currentListId,
    required this.isCopy,
    this.keepOriginal = false,
    required this.onListSelected,
  });

  @override
  Widget build(BuildContext context) {
    String title;
    if (isCopy) {
      title = keepOriginal ? 'Copy to List' : 'Add to List';
    } else {
      title = 'Move to List';
    }

    return AlertDialog(
      title: Text(title),
      content: SizedBox(
        width: double.maxFinite,
        child: ListView.builder(
          shrinkWrap: true,
          itemCount: lists.length,
          itemBuilder: (context, index) {
            final list = lists[index];
            if (list.id == currentListId) return const SizedBox.shrink();
            
            return ListTile(
              title: Text(list.title),
              onTap: () {
                onListSelected(list.id, keepOriginal);
                Navigator.pop(context);
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
    );
  }
} 