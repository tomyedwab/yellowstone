# Archived Lists Page Specification

**Route**: `/archived`

## Overview
Management interface for archived task lists from all categories (todo lists, labels, templates). Provides ability to view and restore previously archived lists.

## Layout

### General Structure
- **Main Container**: Simple scrollable list container
- **Archived List**: Non-reorderable list of archived task lists
- **No Create Card**: No creation functionality for archived items

### Archived List View
- **Non-reorderable**: No drag-and-drop reordering (archived items maintain original order)
- **Item Styling**:
  - Rounded corners (8px radius)
  - No margins (0px horizontal, 0px vertical)
  - Bottom border (#182631, 1.5px width)
  - Selected highlighting (#314150) when list is active

### Archived List Items
1. **Title**: Original list title
2. **Subtitle**: Task count display "X tasks" (using taskIds.length from list data)
3. **Unarchive Button**: Icon button with unarchive/restore icon
4. **No Reorder Handle**: Archived items cannot be reordered
5. **Tap Action**: Navigates to archived list detail view

## Navigation Actions
- **List Item Tap**: Navigate to `/archived/list/{listId}`
- **Unarchive Button**: Restores list to active state

## API Endpoints

### Data Loading
- **GET `/tasklist/all`**: Fetches all task lists
  - Response: Array of task lists with id, title, category, archived status
  - Client filters for archived=true (includes all categories)

### Event Publications
In backend/tasks/state/tasklist.go:
- **`TaskList:UpdateArchived`**: Restores archived list
  - Parameters: listId, archived=false
  - Makes list active again in its original category

## User Interactions

### List Restoration
- Single tap on unarchive icon
- Immediate API call and UI refresh
- No confirmation dialog required
- List moves back to appropriate category page (todo lists, labels, or templates)

### Viewing Archived Content
- Tap on list item to view contents
- Read-only or limited edit access to archived tasks
- Maintains task history and data integrity

## Key Differences from Other List Pages
- **Multi-category**: Shows archived items from all categories (todo, label, template)
- **No Reordering**: Archived items maintain original order
- **No Creation**: Cannot create new archived items directly
- **Restore Focus**: Primary action is restoration rather than management
- **Simplified Layout**: No bottom create card, simpler item margins

## Categories Included
- **Todo Lists**: Regular task lists that were archived
- **Labels**: Label categories that were archived
- **Templates**: Template lists that were archived

## Future Enhancements (TODO noted in code)
- **Category Grouping**: Sort and group by original category with headings
- **Bulk Operations**: Select multiple items for bulk unarchive
- **Search/Filter**: Find specific archived lists

## Data Management
- **Auto-refresh**: Subscribes to data service changes
- **State Updates**: UI rebuilds when backend data changes
- **Error Handling**: Console logging (production error handling needed)
- **Loading State**: No explicit loading indicators

## Responsive Behavior
- **Mobile**: Simple vertical list layout
- **Tablet/Desktop**: Same list layout, no special responsive features
- **Bottom Navigation**: Integrated with app-wide navigation system

## Visual Theme
- **Consistent Styling**: Matches other list pages but simplified
- **Archive-specific Icons**: Uses archive/unarchive icons
- **Color Scheme**: Same dark theme as rest of application
- **Reduced Visual Weight**: Simpler margins reflect archived status
