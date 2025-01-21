package state

import (
	"fmt"

	"github.com/jmoiron/sqlx"
	"tomyedwab.com/yellowstone-server/database/events"
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
type AddTaskToListEvent struct {
	events.GenericEvent

	TaskId int `db:"task_id"`
	ListId int `db:"list_id"`
}

type ReorderTasksEvent struct {
	events.GenericEvent

	TaskListId  int  `db:"task_list_id"`
	OldTaskId   int  `db:"old_task_id"`
	AfterTaskId *int `db:"after_task_id"`
}

// Event handler
const insertTaskToListV1Sql = `
INSERT INTO task_to_list_v1 (task_id, list_id, position)
SELECT :task_id, :list_id, COALESCE(MAX(position), 0) + 1
FROM task_to_list_v1 WHERE list_id = :list_id;
`

const deleteTaskToListV1Sql = `
DELETE FROM task_to_list_v1
WHERE task_id = :task_id;
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

func TaskToListDBHandleEvent(tx *sqlx.Tx, event events.Event) (bool, error) {
	switch evt := event.(type) {
	case *events.DBInitEvent:
		fmt.Printf("Initializing TaskToList v1\n")
		_, err := tx.Exec(taskToListSchema)
		return true, err

	case *AddTaskToListEvent:
		fmt.Printf("TaskToList v1: AddTaskToListEvent %d %d %d\n", evt.Id, evt.TaskId, evt.ListId)
		_, err := tx.NamedExec(insertTaskToListV1Sql, evt)
		return true, err

	case *DeleteTaskEvent:
		fmt.Printf("TaskToList v1: DeleteTaskEvent %d %d\n", evt.Id, evt.TaskId)
		_, err := tx.NamedExec(deleteTaskToListV1Sql, evt)
		return true, err

	case *ReorderTasksEvent:
		fmt.Printf("TaskToList v1: ReorderTasksEvent %d %d %d %v\n", evt.Id, evt.TaskListId, evt.OldTaskId, evt.AfterTaskId)
		if evt.AfterTaskId == nil {
			_, err := tx.NamedExec(reorderTaskToFrontV1Sql, evt)
			return true, err
		} else {
			_, err := tx.NamedExec(reorderTasksV1Sql, evt)
			return true, err
		}
	}
	return false, nil
}

// State queries
const getTasksForListV1Sql = `
SELECT t.id, t.title, t.due_date, t.completed_at
FROM task_v1 t
JOIN task_to_list_v1 ttl ON t.id = ttl.task_id
WHERE ttl.list_id = $1
ORDER BY ttl.position;
`

func taskDBForList(db *sqlx.DB, listId int) (TaskV1Response, error) {
	var tasks []TaskV1 = make([]TaskV1, 0)
	err := db.Select(&tasks, getTasksForListV1Sql, listId)
	return TaskV1Response{Tasks: tasks}, err
}
