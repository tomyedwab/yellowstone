# Templates Page Specification

**Route**: `/templates`

## Overview
Management interface for task list templates. Displays reusable template lists that can be used to create new task lists with predefined tasks.

## Layout

### General Structure
- **Main Container**: Full-height scrollable container
- **Template List**: Scrollable, reorderable list of template task lists
- **Create Card**: Fixed bottom card for adding new templates

### Template List View
- **Reorderable**: Drag-and-drop template reordering enabled
- **Item Styling**:
  - Rounded corners (8px radius)
  - 4px margins
  - Bottom border (#182631, 1.5px width)
  - Selected highlighting (#314150) when template is active

### Template Items
1. **Title**: Primary template name
2. **Subtitle**: Task count display "X tasks" (no completion count for templates)
3. **No Archive Button**: Templates don't have archive functionality in this view
4. **Reorder Handle**: Drag handle (visible on larger screens)
5. **Tap Action**: Navigates to template-specific task view

### Create New Template Card
- **Position**: Fixed at bottom
- **Icon**: Plus/add icon
- **Text**: "Create new template"
- **Action**: Opens template creation dialog

## Navigation Actions
- **Template Item Tap**: Navigate to `/templates/list/{templateId}`

## API Endpoints

### Data Loading
- **GET `/tasklist/all`**: Fetches all task lists
  - Response: Array of task lists with id, title, category, archived status
  - Client filters for category="template" and archived=false
- **GET `/tasklist/metadata`**: Gets task count metadata
  - Response: Array with listId, total task count
  - Note: No completed count since templates don't track completion

### Event Publications
In backend/tasks/state/tasklist.go:
- **`TaskList:Reorder`**: Reorders template lists
  - Parameters: listId, afterListId (null for first position)
- **`TaskList:Add`**: Creates new template
  - Parameters: title, category="template", archived=false

## User Interactions

### Template Reordering
- Long press and drag to reorder
- Visual feedback during drag operation
- API call on drop to persist new order

### Template Creation Dialog
- **Title**: "Create New Template"
- **Input Field**: Text input with "Enter template title" placeholder
- **Auto-focus**: Input field focused on dialog open
- **Submit**: Enter key or dialog action button
- **Cancel**: Cancel button to close dialog
- **Validation**: Only creates if title is non-empty

## Key Differences from Todo Lists Page
- **No Completion Tracking**: Templates show only task count, not completion status
- **No Archive Actions**: Templates are not archived from this interface
- **Category Filter**: Shows only category="template" items
- **Different Navigation**: Routes to `/templates/list/{id}`
- **Template Purpose**: Designed for reusable task list patterns

## Template Usage Context
- **Purpose**: Create reusable task list structures
- **Use Cases**:
  - Project checklists
  - Recurring workflows
  - Standard operating procedures
  - Process templates

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
- **Template-specific Icons**: Uses copy/template icon in navigation
- **Color Scheme**: Same dark theme as rest of application
