# Todo Lists Page Specification

**Route**: `/` (home page)

## Overview
Main dashboard displaying all active (non-archived) todo lists with creation and management capabilities.

## Layout

### General Structure
- **Main Container**: Full-height scrollable container
- **List View**: Scrollable, reorderable list of todo lists (fills most of screen height)
- **Create Card**: Fixed bottom card for adding new lists

### List View
- **Reorderable**: Drag-and-drop list reordering enabled
- **Item Styling**:
  - Rounded corners (8px radius)
  - 4px margins
  - Bottom border (#182631, 1.5px width)
  - Selected highlighting (#314150) when list is selected

### List Items
1. **Title**: Primary list name
2. **Subtitle**: Task count display "X tasks, Y completed"
3. **Archive Button**: Archive icon button
4. **Reorder Handle**: Drag handle (visible on larger screens)
5. **Tap Action**: Navigates to task list detail view

### Create New List Card
- **Position**: Fixed at bottom
- **Icon**: Plus/add icon
- **Text**: "Create new list"
- **Action**: Opens creation dialog

## Navigation Actions
- **List Item Tap**: Navigate to `/list/{listId}`
- **Archive Button**: Archives list and refreshes view

## API Endpoints

### Data Loading
- **GET `/tasklist/all`**: Fetches all task lists
  - Response: Array of task lists with id, title, category, archived status
  - Client filters for category="todolist" and archived=false
- **GET `/tasklist/metadata`**: Gets task count metadata
  - Response: Array with listId, total task count, completed task count

### Event Publications
In backend/tasks/state/tasklist.go:
- **`TaskList:Reorder`**: Reorders lists
  - Parameters: listId, afterListId (null for first position)
- **`TaskList:UpdateArchived`**: Archives a list
  - Parameters: listId, archived=true
- **`TaskList:Add`**: Creates new list
  - Parameters: title, category="toDoList", archived=false

## User Interactions

### List Reordering
- Long press and drag to reorder
- Visual feedback during drag
- API call on drop to persist order

### List Creation Dialog
- **Title**: "Create New List"
- **Input Field**: Text input with "Enter list title" placeholder
- **Auto-focus**: Input field focused on dialog open
- **Submit**: Enter key or dialog action
- **Cancel**: Cancel button to close dialog
- **Validation**: Only creates if title is non-empty

### Archive Action
- Single tap on archive icon
- Immediate API call and UI refresh
- No confirmation required

## Data Management
- **Auto-refresh**: Subscribes to data service changes
- **State Updates**: UI rebuilds when backend data changes
- **Error Handling**: Console logging (production error handling needed)
- **Loading State**: No explicit loading indicators

## Responsive Behavior
- **Mobile**: List items stack vertically, minimal reorder handles
- **Tablet/Desktop**: Reorder handles visible, optimized spacing
- **Bottom Space**: Adjusts for different screen sizes and virtual keyboards
