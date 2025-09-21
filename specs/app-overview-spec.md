# Yellowstone Notes App - Overview Specification

## Application Structure

### Core Concept
Yellowstone is a task management application with three primary organizational methods:
1. **Todo Lists**: Traditional task lists with completion tracking
2. **Labels**: Category-based task organization
3. **Templates**: Reusable task list patterns

### Technology Stack
- **Frontend**: Flutter web application
- **Theme**: Dark theme with Material 3 design
- **Routing**: Go Router for navigation
- **Authentication**: Session-based auth with Yesterday library
- **API**: RESTful API with JSON responses

## Page Hierarchy

### Authentication
- **Login Page** (`/login`): User authentication entry point

### Main Application (Requires Authentication)
- **Todo Lists Page** (`/`): Home dashboard with active task lists
- **Labels Page** (`/labels`): Label category management
- **Templates Page** (`/templates`): Template list management
- **Archived Lists Page** (`/archived`): View and restore archived items

### Detailed Views
- **Task List Page** (`/list/{id}`, `/labels/list/{id}`, `/templates/list/{id}`, `/archived/list/{id}`): Individual list management with tasks
- **Task History Page** (`/list/{id}/task/{taskId}/history`): Task activity timeline and comments

## Core Features

### List Management
- Create, rename, archive/restore lists
- Drag-and-drop reordering
- Category-based organization (todo/label/template)
- Task count and completion tracking

### Task Management
- Create, edit, complete tasks
- Due date assignment
- Task reordering within lists
- Batch operations (move, copy, duplicate)
- Comment system with history tracking

### Selection and Batch Operations
- Multi-select mode for tasks
- Batch selection helpers (all, completed, uncompleted)
- Cross-list operations (move, copy to other lists)
- Bulk task management

## API Structure

### Data Retrieval (GET Endpoints)
- `GET /tasklist/all` - Fetch all lists
- `GET /tasklist/metadata` - Get task counts
- `GET /tasklist/get?id={id}` - Get specific list
- `GET /task/list?listId={id}` - Get tasks for list
- `GET /task/history?taskId={id}` - Get task history
- `GET /task/labels?listId={id}` - Get task labels
- `GET /tasklist/recentcomments?listId={id}` - Get recent comments

### Event Publications (Event-based Modifications)

#### Task List Events
- `yellowstone:addTaskList` - Create new list
- `yellowstone:updateTaskListTitle` - Update list title
- `yellowstone:updateTaskListArchived` - Archive/restore list
- `yellowstone:reorderTaskList` - Reorder lists

#### Task Events
In backend/tasks/state/task.go:
- `Task:Add` - Create new task
- `Task:UpdateTitle` - Update task title
- `Task:UpdateCompleted` - Mark task complete/incomplete
- `Task:UpdateDueDate` - Set/clear due date
- `Task:Delete` - Delete task
In backend/tasks/state/task_to_list.go:
- `TaskList:ReorderTasks` - Reorder tasks
- `TaskList:MoveTasks` - Move tasks between lists
- `TaskList:CopyTasks` - Copy tasks to other lists
- `TaskList:DuplicateTasks` - Duplicate tasks with originals
In backend/tasks/state/task_history.go:
- `Task:AddComment` - Add task comment

## Responsive Design

### Mobile Layout (Vertical)
- Bottom navigation bar
- Single-panel content view
- Touch-optimized interactions
- Virtual keyboard handling

### Desktop/Tablet Layout (Horizontal)
- Left sidebar navigation rail
- Multi-panel content (up to 3 panels)
- Mouse and keyboard optimizations
- Wider screen real estate usage

## Data Flow

### Real-time Updates
- Data service polling for server changes
- Automatic UI refresh on data changes
- Optimistic updates for user actions
- In-flight request tracking

### State Management
- Service-based data management
- Listener pattern for UI updates
- Cached API responses
- Local state for UI interactions

## User Experience Patterns

### Common Interactions
- Tap to select/navigate
- Long press for additional options
- Drag and drop for reordering
- Modal dialogs for creation/editing
- Bottom sheets for task options

### Navigation Flow
1. Login → Main dashboard
2. Select list type (todo/label/template/archived)
3. View/manage lists
4. Drill down to specific list
5. Manage individual tasks
6. Access task history and comments

## Color Scheme
- **Primary**: Light blue-white (#f6fbff)
- **Background**: Dark blue (#111e2a)
- **Surface**: Variations of dark theme
- **Borders**: Dark blue-gray (#182631)
- **Selection**: Medium blue-gray (#314150)
- **Indicator**: Light blue (#7faad0)

## Future Enhancements (Based on TODOs)
- Improved error handling with user notifications
- Archived list category grouping
- Enhanced search and filtering
- Performance optimizations
- Accessibility improvements
