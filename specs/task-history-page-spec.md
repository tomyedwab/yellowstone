# Task History Page Specification

**Route**: `/list/{listId}/task/{taskId}/history`

## Overview
Displays chronological history of a specific task including system events, user comments, and activity timeline with ability to add new comments.

## Layout

### General Structure
- **Header**: Task title display
- **History List**: Scrollable chronological list of history entries
- **Comment Input**: Fixed bottom input area for adding new comments

### Header Section
- **Task Title**: Displays the task name prominently
- **Back Navigation**: Return to task list view (mobile) or close panel (desktop)

### History Timeline
- **Reverse Chronological**: Most recent entries at top
- **Card-based Layout**: Each history entry in separate card
- **Entry Types**:
  - System comments (task completion, creation, modifications)
  - User comments (manual notes and observations)

### History Entry Cards
1. **Timestamp**: Date and time formatted as "YYYY-MM-DD HH:MM"
2. **System Comment**: Automated activity description (if present)
   - Background color: Surface variant
   - Rounded container (8px radius)
3. **User Comment**: Manual comment text (if present)
   - Background color: Surface variant
   - Rounded container (8px radius)

### Comment Input Section
- **Position**: Fixed at bottom of screen
- **Input Field**: Multi-line text input with "Add a comment..." placeholder
- **Send Button**: Filled icon button with send icon
- **Layout**: Horizontal row with expanded text field and send button

## Navigation Actions
- **Back**: Returns to task list view
- **Send Comment**: Adds comment and refreshes history

## API Endpoints

### Data Loading
- **GET `/task/history?taskId={taskId}`**: Fetches complete task history
  - Response: Object with task title and array of history entries
  - History entries contain: createdAt, systemComment, userComment (optional)

### Comment Event Publications
In backend/tasks/state/task_history.go:
- **`Task:AddComment`**: Adds new user comment to task
  - Parameters: taskId, userComment
  - Triggers history reload to show new comment

## User Interactions

### Comment Creation
1. **Text Input**: Multi-line text field for comment entry
2. **Submit Actions**:
   - Send button click
   - Enter key (implementation dependent)
3. **Validation**: Only submits if comment text is non-empty
4. **Auto-clear**: Input field clears after successful submission

### History Navigation
- **Scroll**: Vertical scrolling through history entries
- **No Pagination**: All history loaded at once
- **Auto-refresh**: History reloads after adding comments

## Visual States
- **Loading**: Progress indicator while fetching history
- **Empty History**: No special empty state (history always has creation event)
- **Error States**: Error messages for failed loads or comment submissions

## Data Formatting
- **DateTime Display**: Local format "YYYY-MM-DD HH:MM"
- **System Comments**: Automated messages about task state changes
- **User Comments**: Formatted as italic text to distinguish from system events
- **Text Wrapping**: All comment text supports multi-line display

## Error Handling
- **Loading Failures**: User notification via snackbar/toast
- **Comment Submission Errors**: Error message display
- **Network Issues**: Graceful degradation with retry options

## Responsive Behavior
- **Mobile**: Full screen view with app bar navigation
- **Tablet/Desktop**: Panel view within task list layout
- **Keyboard Handling**: Input area adjusts for virtual keyboard
- **Text Scaling**: History content adapts to user font size preferences
