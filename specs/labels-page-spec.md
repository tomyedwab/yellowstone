# Labels Page Specification

**Route**: `/labels`

## Overview
Management interface for label-based task organization. Displays all active label lists with creation capabilities and navigation to label-specific task views.

## Layout

### General Structure
- **Main Container**: Full-height scrollable container
- **Label List**: Scrollable, reorderable list of label categories
- **Create Card**: Fixed bottom card for adding new labels

### Label List View
- **Reorderable**: Drag-and-drop label reordering enabled
- **Item Styling**:
  - Rounded corners (8px radius)
  - 4px margins
  - Bottom border (#182631, 1.5px width)
  - Selected highlighting (#314150) when label is active

### Label Items
1. **Title**: Primary label name
2. **Subtitle**: Task count display "X tasks, Y completed"
3. **No Archive Button**: Labels don't have archive functionality in this view
4. **Reorder Handle**: Drag handle (visible on larger screens)
5. **Tap Action**: Navigates to label-specific task list

### Create New Label Card
- **Position**: Fixed at bottom
- **Icon**: Plus/add icon
- **Text**: "Create new label"
- **Action**: Opens label creation dialog

## Navigation Actions
- **Label Item Tap**: Navigate to `/labels/list/{labelId}`

## API Endpoints

### Data Loading
- **GET `/tasklist/all`**: Fetches all task lists
  - Response: Array of task lists with id, title, category, archived status
  - Client filters for category="label" and archived=false
- **GET `/tasklist/metadata`**: Gets task count metadata
  - Response: Array with listId, total task count, completed task count

### Event Publications
In backend/tasks/state/tasklist.go:
- **`TaskList:Reorder`**: Reorders label lists
  - Parameters: listId, afterListId (null for first position)
- **`TaskList:Add`**: Creates new label
  - Parameters: title, category="label", archived=false

## User Interactions

### Label Reordering
- Long press and drag to reorder
- Visual feedback during drag operation
- API call on drop to persist new order

### Label Creation Dialog
- **Title**: "Create New Label"
- **Input Field**: Text input with "Enter label title" placeholder
- **Auto-focus**: Input field focused on dialog open
- **Submit**: Enter key or dialog action button
- **Cancel**: Cancel button to close dialog
- **Validation**: Only creates if title is non-empty

## Key Differences from Todo Lists Page
- **No Archive Actions**: Labels are not archived from this interface
- **Category Filter**: Shows only category="label" items
- **Different Navigation**: Routes to `/labels/list/{id}` instead of `/list/{id}`
- **Label Context**: Focused on categorical/tagging organization

## Data Management
- **Auto-refresh**: Subscribes to data service changes
- **State Updates**: UI rebuilds when backend data changes
- **Error Handling**: Console logging (production error handling needed)
- **Loading State**: No explicit loading indicators

## Responsive Behavior
- **Mobile**: List items stack vertically, minimal reorder handles
- **Tablet/Desktop**: Reorder handles visible, optimized spacing
- **Bottom Navigation**: Integrated with app-wide navigation system

## Visual Theme
- **Consistent Styling**: Matches main todo lists page appearance
- **Label-specific Icons**: Uses label icon in navigation
- **Color Scheme**: Same dark theme as rest of application
