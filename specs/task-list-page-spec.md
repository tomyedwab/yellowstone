# Task List Page Specification

**Route**: `/list/{listId}` and subroutes

## Overview
Detailed view of a specific task list showing all tasks with management, reordering, and batch operation capabilities.

## Layout

### General Structure
- **Column Layout**: Title header + task list content
- **Responsive**: Adapts between horizontal (tablet/desktop) and vertical (mobile) layouts

### Header Section
1. **Title Display**: Task list title prominently displayed
2. **Action Toolbar**: Context-sensitive buttons
   - **Normal Mode**: Edit, Archive/Unarchive, Selection Toggle
   - **Selection Mode**: Batch Select, Add to List, Copy, Move, Exit Selection

### Task List Content
- **Normal Mode**: Reorderable task list with drag-and-drop
- **Selection Mode**: Checkbox-based task selection interface

## Navigation Actions
- **Edit List**: Opens rename dialog
- **Archive/Unarchive**: Toggles list archive status
- **Task History**: Navigate to `/list/{listId}/task/{taskId}/history`

## API Endpoints

### Data Loading
- **GET `/task/list?listId={listId}`**: Fetches all tasks in the list
  - Response: Array of tasks with id, title, completion status, due dates
- **GET `/tasklist/recentcomments?listId={listId}`**: Gets recent task comments
  - Response: Array of recent comments with taskId and comment text
- **GET `/tasklist/get?id={listId}`**: Fetches list metadata
  - Response: List details including title, category, archive status
- **GET `/task/labels?listId={listId}`**: Gets task labels
  - Response: Array of labels associated with tasks (excluding list's own labels)

### List Event Publications
- **`TaskList:UpdateTitle`**: Updates list title
  - Parameters: listId, title
- **`TaskList:UpdateArchived`**: Archives list
  - Parameters: listId, archived=true
- **`TaskList:UpdateArchived`**: Restores archived list
  - Parameters: listId, archived=false

### Batch Task Event Publications
In backend/tasks/state/task_to_list.go:
- `TaskList:MoveTasks` - Move tasks between lists
  - Parameters: taskIds[], oldListId, newListId
- `TaskList:CopyTasks` - Copy tasks to other lists
  - Parameters: taskIds[], newListId
- `TaskList:DuplicateTasks` - Duplicate tasks with originals
  - Parameters: taskIds[], newListId

## User Interactions

### Selection Mode
1. **Enter Selection**: Click checklist icon in toolbar
2. **Select Tasks**: Click checkboxes on individual tasks
3. **Batch Selection Dialog**: Quick selection options:
   - Select All tasks
   - Select Completed tasks only
   - Select Uncompleted tasks only
   - Clear Selection

### Target List Selection Dialog
- **Purpose**: Choose destination list for move/copy operations
- **Operations**:
  - **Move to List**: Relocates tasks to different list
  - **Add to List**: Copies tasks without removing originals
  - **Copy to List**: Duplicates tasks keeping originals
- **Filtering**: Excludes current list from available targets

### List Management
1. **Rename Dialog**:
   - Title: "Rename List"
   - Text input pre-filled with current title
   - Submit on Enter key or button click
   - Cancel option available

2. **Archive Toggle**:
   - Single button in toolbar
   - Icon switches between archive/unarchive
   - Immediate API call and state update

## Display Modes

### Normal Mode Features
- Drag-and-drop task reordering
- Task completion checkboxes
- Individual task action menus
- New task creation input at bottom
- Task history access buttons

### Selection Mode Features
- Checkbox-based multi-select
- Disabled drag-and-drop reordering
- Batch operation toolbar
- No new task creation
- Focus on bulk operations

## Visual States
- **Loading**: Progress indicator while fetching data
- **Normal**: Standard task list with action buttons
- **Selection**: Checkboxes visible, different toolbar
- **Task Highlighting**: Current selected task highlighted
- **Empty State**: Message when no tasks exist

## Error Handling
- Loading errors logged to console
- Network failures show user notifications
- Invalid operations prevented with validation

## Responsive Behavior
- **Mobile**: Vertical layout with app bar, stacked content
- **Tablet/Desktop**: Horizontal layout with title and actions in row
- **Keyboard Handling**: Adjusts layout when virtual keyboard appears
