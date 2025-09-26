package state

import (
	"fmt"
	"time"

	"github.com/jmoiron/sqlx"
	"tomyedwab.com/yellowstone-server/tasks/generated"
)

// Table schema

const taskSchema = `
CREATE TABLE IF NOT EXISTS task_v1 (
	id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    title TEXT NOT NULL,
    due_date DATETIME,
    completed_at DATETIME
);
`

func InitTask(tx *sqlx.Tx) error {
	fmt.Printf("Initializing Task v1\n")
	_, err := tx.Exec(taskSchema)
	return err
}

// Event handler

const insertTaskV1Sql = `
INSERT INTO task_v1 (title, due_date)
VALUES (:title, :duedate);
`

const updateTaskTitleV1Sql = `
UPDATE task_v1
SET title = :title
WHERE id = :taskid;
`

const updateTaskCompletedV1Sql = `
UPDATE task_v1
SET completed_at = :completedat
WHERE id = :taskid;
`

const updateTaskDueDateV1Sql = `
UPDATE task_v1
SET due_date = :duedate
WHERE id = :taskid;
`

const deleteTaskV1Sql = `
DELETE FROM task_v1
WHERE id = :taskid;
`

func (h *StateEventHandler) HandleTaskAddEvent(tx *sqlx.Tx, event *generated.TaskAddEvent) (bool, error) {
	fmt.Printf("Task v1: AddTaskEvent %v for list %d\n", event.Title, event.TaskListId)
	result, err := tx.NamedExec(
		insertTaskV1Sql,
		*event,
	)
	if err != nil {
		return true, err
	}

	// Get the ID of the newly inserted task
	taskId, err := result.LastInsertId()
	if err != nil {
		return true, err
	}

	// Add the task to the list
	addToList := generated.TaskListAddTaskEvent{
		TaskId: int(taskId),
		ListId: event.TaskListId,
	}
	_, err = tx.NamedExec(insertTaskToListV1Sql, addToList)
	return true, err
}

func (h *StateEventHandler) HandleTaskUpdateTitleEvent(tx *sqlx.Tx, event *generated.TaskUpdateTitleEvent) (bool, error) {
	fmt.Printf("Task v1: UpdateTaskTitleEvent %d %v\n", event.TaskId, event.Title)
	_, err := tx.NamedExec(
		updateTaskTitleV1Sql,
		*event,
	)
	if err != nil {
		return true, err
	}
	historyEvent := AddTaskHistoryEvent{
		TaskId:        event.TaskId,
		UpdateType:    "update_title",
		SystemComment: fmt.Sprintf("Title updated to: %s", event.Title),
	}
	_, err = tx.NamedExec(insertTaskHistoryV1Sql, historyEvent)
	return true, err
}

func (h *StateEventHandler) HandleTaskUpdateCompletedEvent(tx *sqlx.Tx, event *generated.TaskUpdateCompletedEvent) (bool, error) {
	fmt.Printf("Task v1: UpdateTaskCompletedEvent %d %v\n", event.TaskId, event.CompletedAt)
	_, err := tx.NamedExec(
		updateTaskCompletedV1Sql,
		*event,
	)
	if err != nil {
		return true, err
	}
	var comment string
	if event.CompletedAt != nil {
		comment = fmt.Sprintf("Task marked as completed at %s", event.CompletedAt.Format(time.RFC3339))
	} else {
		comment = "Task marked as not completed"
	}
	historyEvent := AddTaskHistoryEvent{
		TaskId:        event.TaskId,
		UpdateType:    "update_completed",
		SystemComment: comment,
	}
	_, err = tx.NamedExec(insertTaskHistoryV1Sql, historyEvent)
	return true, err
}

func (h *StateEventHandler) HandleTaskUpdateDueDateEvent(tx *sqlx.Tx, event *generated.TaskUpdateDueDateEvent) (bool, error) {
	fmt.Printf("Task v1: UpdateTaskDueDateEvent %d %v\n", event.TaskId, event.DueDate)
	_, err := tx.NamedExec(
		updateTaskDueDateV1Sql,
		*event,
	)
	if err != nil {
		return true, err
	}
	var comment string
	if event.DueDate != nil {
		comment = fmt.Sprintf("Due date set to %s", event.DueDate.Format(time.RFC3339))
	} else {
		comment = "Due date removed"
	}
	historyEvent := AddTaskHistoryEvent{
		TaskId:        event.TaskId,
		UpdateType:    "update_due_date",
		SystemComment: comment,
	}
	_, err = tx.NamedExec(insertTaskHistoryV1Sql, historyEvent)
	return true, err
}

func (h *StateEventHandler) HandleTaskDeleteEvent(tx *sqlx.Tx, event *generated.TaskDeleteEvent) (bool, error) {
	fmt.Printf("Task v1: DeleteTaskEvent %d\n", event.TaskId)
	_, err := tx.NamedExec(
		deleteTaskV1Sql,
		*event,
	)
	if err != nil {
		return true, err
	}
	_, err = tx.NamedExec(deleteTaskToListV1Sql, event)
	if err != nil {
		return true, err
	}
	historyEvent := AddTaskHistoryEvent{
		TaskId:        event.TaskId,
		UpdateType:    "delete",
		SystemComment: "Task deleted",
	}
	_, err = tx.NamedExec(insertTaskHistoryV1Sql, historyEvent)
	return true, err
}

// State queries

const getTaskByIdV1Sql = `
SELECT id, title, due_date AS duedate, completed_at AS completedat
FROM task_v1 WHERE id = $1;
`

const getTaskTitleV1Sql = `
SELECT title
FROM task_v1
WHERE id = $1;
`

func (r *StateResolver) GetApiTaskGet(db *sqlx.DB, id int) (generated.Task, error) {
	var task generated.Task
	err := db.Get(&task, getTaskByIdV1Sql, id)
	return task, err
}
