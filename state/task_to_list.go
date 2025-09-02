package state

import (
	"fmt"
	"time"

	"github.com/jmoiron/sqlx"

	"github.com/tomyedwab/yesterday/applib/database"
)

// Table schema
const taskToListSchema = `
CREATE TABLE IF NOT EXISTS task_to_list_v1 (
    task_id INTEGER NOT NULL,
    list_id INTEGER NOT NULL,
    position INTEGER NOT NULL,
    PRIMARY KEY (task_id, list_id),
    FOREIGN KEY (task_id) REFERENCES task_v1(id),
    FOREIGN KEY (list_id) REFERENCES task_list_v1(id)
);
`

// Events

const AddTaskToListEventType = "TaskList:AddTask"
const MoveTasksEventType = "TaskList:MoveTasks"
const CopyTasksEventType = "TaskList:CopyTasks"
const ReorderTasksEventType = "TaskList:ReorderTasks"
const DuplicateTasksEventType = "TaskList:DuplicateTasks"

type AddTaskToListEvent struct {
	TaskId int `db:"task_id"`
	ListId int `db:"list_id"`
}

type MoveTasksEvent struct {
	TaskIds   []int `db:"task_ids"`
	OldListId int   `db:"old_list_id"`
	NewListId int   `db:"new_list_id"`
}

type CopyTasksEvent struct {
	TaskIds   []int `db:"task_ids"`
	NewListId int   `db:"new_list_id"`
}

type ReorderTasksEvent struct {
	TaskListId  int  `db:"task_list_id"`
	OldTaskId   int  `db:"old_task_id"`
	AfterTaskId *int `db:"after_task_id"`
}

type DuplicateTasksEvent struct {
	TaskIds   []int `db:"task_ids"`
	NewListId int   `db:"new_list_id"`
}

// Event handler
const insertTaskToListV1Sql = `
INSERT INTO task_to_list_v1 (task_id, list_id, position)
SELECT :task_id, :list_id, COALESCE(MAX(position), 0) + 1
FROM task_to_list_v1 WHERE list_id = :list_id
ON CONFLICT (task_id, list_id) DO NOTHING;
`

const deleteTaskToListV1Sql = `
DELETE FROM task_to_list_v1
WHERE task_id = :task_id;
`

const moveTaskFromListV1Sql = `
DELETE FROM task_to_list_v1
WHERE task_id = :task_id AND list_id = :old_list_id;
`

const moveTaskToListV1Sql = `
INSERT INTO task_to_list_v1 (task_id, list_id, position)
SELECT :task_id, :new_list_id, COALESCE(MAX(position), 0) + 1
FROM task_to_list_v1 WHERE list_id = :new_list_id
ON CONFLICT (task_id, list_id) DO NOTHING;
`

// We can get into situations where multiple tasks have the same position in the
// list. Repairing just means ordering the tasks by position and then updating
// the position to be the row number.
const repairTaskOrderV1Sql = `
UPDATE task_to_list_v1
SET position = (
    SELECT row_num
    FROM (
        SELECT task_id, ROW_NUMBER() OVER (ORDER BY position) as row_num
        FROM task_to_list_v1
        WHERE list_id = :task_list_id
    ) numbered
    WHERE numbered.task_id = task_to_list_v1.task_id
)
WHERE list_id = :task_list_id;
`

const reorderTasksV1Sql = `
WITH old_task AS (
  SELECT position FROM task_to_list_v1 WHERE task_id = :old_task_id AND list_id = :task_list_id
), new_task AS (
  SELECT position + 1 AS position FROM task_to_list_v1 WHERE task_id = :after_task_id AND list_id = :task_list_id
)
UPDATE task_to_list_v1
SET position = CASE
  WHEN position = (SELECT position FROM old_task) THEN (SELECT position FROM new_task)
  WHEN position > (SELECT position FROM old_task) AND position < (SELECT position FROM new_task) THEN position - 1
  WHEN position < (SELECT position FROM old_task) AND position >= (SELECT position FROM new_task) THEN position + 1
  ELSE position
END
WHERE list_id = :task_list_id;
`

const reorderTaskToFrontV1Sql = `
UPDATE task_to_list_v1
SET position = CASE
  WHEN task_id = :old_task_id THEN 1
  ELSE position + 1
END
WHERE list_id = :task_list_id;
`

const duplicateTaskV1Sql = `
INSERT INTO task_v1 (title, due_date, completed_at)
SELECT title, due_date, NULL
FROM task_v1
WHERE id = $1
RETURNING id;
`

const addDuplicatedTaskToListV1Sql = `
INSERT INTO task_to_list_v1 (task_id, list_id, position)
SELECT :task_id, :list_id, COALESCE(MAX(position), 0) + 1
FROM task_to_list_v1 WHERE list_id = :list_id;
`

func InitTaskToList(db *database.Database, tx *sqlx.Tx) error {
	database.AddEventHandler(db, AddTaskToListEventType, handleAddTaskToListEvent)
	database.AddEventHandler(db, DeleteTaskEventType, handleListDeleteTaskEvent)
	database.AddEventHandler(db, MoveTasksEventType, handleMoveTasksEvent)
	database.AddEventHandler(db, CopyTasksEventType, handleCopyTasksEvent)
	database.AddEventHandler(db, ReorderTasksEventType, handleReorderTasksEvent)
	database.AddEventHandler(db, DuplicateTasksEventType, handleDuplicateTasksEvent)

	fmt.Printf("Initializing TaskToList v1\n")
	_, err := tx.Exec(taskToListSchema)
	return err
}

func handleAddTaskToListEvent(tx *sqlx.Tx, event *AddTaskToListEvent) (bool, error) {
	fmt.Printf("TaskToList v1: AddTaskToListEvent %d %d\n", event.TaskId, event.ListId)
	_, err := tx.NamedExec(insertTaskToListV1Sql, event)
	return true, err
}

func handleListDeleteTaskEvent(tx *sqlx.Tx, event *DeleteTaskEvent) (bool, error) {
	fmt.Printf("TaskToList v1: DeleteTaskEvent %d\n", event.TaskId)
	_, err := tx.NamedExec(deleteTaskToListV1Sql, event)
	return true, err
}

func handleMoveTasksEvent(tx *sqlx.Tx, event *MoveTasksEvent) (bool, error) {
	fmt.Printf("TaskToList v1: MoveTasksEvent %v from %d to %d\n", event.TaskIds, event.OldListId, event.NewListId)
	for _, taskId := range event.TaskIds {
		// First remove from old list
		_, err := tx.NamedExec(moveTaskFromListV1Sql, map[string]interface{}{
			"task_id":     taskId,
			"old_list_id": event.OldListId,
		})
		if err != nil {
			return true, err
		}

		// Then add to new list
		_, err = tx.NamedExec(moveTaskToListV1Sql, map[string]interface{}{
			"task_id":     taskId,
			"new_list_id": event.NewListId,
		})
		if err != nil {
			return true, err
		}
	}
	return true, nil
}

func handleCopyTasksEvent(tx *sqlx.Tx, event *CopyTasksEvent) (bool, error) {
	fmt.Printf("TaskToList v1: CopyTasksEvent %v to %d\n", event.TaskIds, event.NewListId)
	for _, taskId := range event.TaskIds {
		_, err := tx.NamedExec(insertTaskToListV1Sql, map[string]interface{}{
			"task_id": taskId,
			"list_id": event.NewListId,
		})
		if err != nil {
			return true, err
		}
	}
	return true, nil
}

func handleReorderTasksEvent(tx *sqlx.Tx, event *ReorderTasksEvent) (bool, error) {
	_, err := tx.NamedExec(repairTaskOrderV1Sql, map[string]interface{}{
		"task_list_id": event.TaskListId,
	})
	if err != nil {
		return true, err
	}

	if event.AfterTaskId == nil {
		fmt.Printf("TaskToList v1: ReorderTasksEvent %d %d -> front\n", event.TaskListId, event.OldTaskId)
		_, err := tx.NamedExec(reorderTaskToFrontV1Sql, event)
		return true, err
	} else {
		fmt.Printf("TaskToList v1: ReorderTasksEvent %d %d -> %d\n", event.TaskListId, event.OldTaskId, *event.AfterTaskId)
		_, err := tx.NamedExec(reorderTasksV1Sql, event)
		return true, err
	}
}

func handleDuplicateTasksEvent(tx *sqlx.Tx, event *DuplicateTasksEvent) (bool, error) {
	fmt.Printf("TaskToList v1: DuplicateTasksEvent %v to %d\n", event.TaskIds, event.NewListId)
	for _, sourceTaskId := range event.TaskIds {
		// First duplicate the task
		var newTaskId int
		err := tx.Get(&newTaskId, duplicateTaskV1Sql, sourceTaskId)
		if err != nil {
			return true, err
		}

		// Then add the new task to the target list
		_, err = tx.NamedExec(addDuplicatedTaskToListV1Sql, map[string]interface{}{
			"task_id": newTaskId,
			"list_id": event.NewListId,
		})
		if err != nil {
			return true, err
		}
	}
	return true, nil
}

// State queries
const getTasksForListV1Sql = `
SELECT t.id, t.title, t.due_date, t.completed_at
FROM task_v1 t
JOIN task_to_list_v1 ttl ON t.id = ttl.task_id
WHERE ttl.list_id = $1
ORDER BY ttl.position;
`

// Returns the number of total & completed tasks for every list
const getTaskMetadataV1Sql = `
SELECT list_id, COUNT(*) AS total, SUM(CASE WHEN task_v1.completed_at IS NOT NULL THEN 1 ELSE 0 END) AS completed
FROM task_to_list_v1
LEFT JOIN task_v1 ON task_to_list_v1.task_id = task_v1.id
GROUP BY list_id;
`

const getRecentCommentsForListV1Sql = `
WITH latest_comments AS (
    SELECT task_id, user_comment, created_at,
           ROW_NUMBER() OVER (PARTITION BY task_id ORDER BY created_at DESC) as rn
    FROM task_history_v1
    WHERE user_comment IS NOT NULL
)
SELECT ttl.list_id, ttl.task_id, lc.user_comment, lc.created_at
FROM task_to_list_v1 ttl
LEFT JOIN latest_comments lc ON ttl.task_id = lc.task_id AND lc.rn = 1
WHERE ttl.list_id = $1 AND lc.user_comment IS NOT NULL
ORDER BY ttl.position;
`

const getTaskLabelsForListV1Sql = `
SELECT ttl1.task_id, ttl2.list_id, tl.title AS label
FROM task_to_list_v1 ttl1
LEFT JOIN task_to_list_v1 ttl2 ON ttl1.task_id = ttl2.task_id
LEFT JOIN task_list_v1 tl ON ttl2.list_id = tl.id
WHERE ttl1.list_id = $1 AND tl.category = 'label'
`

type TaskListMetadataV1 struct {
	ListId    int `db:"list_id"`
	Total     int `db:"total"`
	Completed int `db:"completed"`
}

type TaskRecentCommentV1 struct {
	ListId      int        `db:"list_id"`
	TaskId      int        `db:"task_id"`
	UserComment *string    `db:"user_comment"`
	CreatedAt   *time.Time `db:"created_at"`
}

type TaskLabelsV1 struct {
	TaskId int    `db:"task_id"`
	Label  string `db:"label"`
	ListId int    `db:"list_id"`
}

func taskDBForList(db *sqlx.DB, listId int) (TaskV1Response, error) {
	var tasks []TaskV1 = make([]TaskV1, 0)
	err := db.Select(&tasks, getTasksForListV1Sql, listId)
	return TaskV1Response{Tasks: tasks}, err
}

func taskListDBMetadata(db *sqlx.DB) ([]TaskListMetadataV1, error) {
	var metadata []TaskListMetadataV1 = make([]TaskListMetadataV1, 0)
	err := db.Select(&metadata, getTaskMetadataV1Sql)
	return metadata, err
}

func taskListDBRecentComments(db *sqlx.DB, listId int) ([]TaskRecentCommentV1, error) {
	var comments []TaskRecentCommentV1 = make([]TaskRecentCommentV1, 0)
	err := db.Select(&comments, getRecentCommentsForListV1Sql, listId)
	return comments, err
}

func taskListDBGetAllTaskLabels(db *sqlx.DB, listId int) ([]TaskLabelsV1, error) {
	var labels []TaskLabelsV1 = make([]TaskLabelsV1, 0)
	err := db.Select(&labels, getTaskLabelsForListV1Sql, listId)
	return labels, err
}
