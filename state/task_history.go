package state

import (
	"fmt"
	"time"

	"github.com/jmoiron/sqlx"

	"tomyedwab.com/yellowstone-server/database/events"
)

// Table schema
const taskHistorySchema = `
CREATE TABLE IF NOT EXISTS task_history_v1 (
	id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
	task_id INTEGER NOT NULL,
	update_type TEXT NOT NULL,
	system_comment TEXT NOT NULL,
	user_comment TEXT,
	created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	FOREIGN KEY (task_id) REFERENCES task_v1(id)
);
`

// Events
type AddTaskHistoryEvent struct {
	events.GenericEvent

	TaskId        int     `db:"task_id"`
	UpdateType    string  `db:"update_type"`
	SystemComment string  `db:"system_comment"`
	UserComment   *string `db:"user_comment"`
}

type AddTaskCommentEvent struct {
	events.GenericEvent

	TaskId      int    `db:"task_id"`
	UserComment string `db:"user_comment"`
}

// Event handler
const insertTaskHistoryV1Sql = `
INSERT INTO task_history_v1 (task_id, update_type, system_comment, user_comment)
VALUES (:task_id, :update_type, :system_comment, :user_comment);
`

func TaskHistoryDBHandleEvent(tx *sqlx.Tx, event events.Event) (bool, error) {
	switch evt := event.(type) {
	case *events.DBInitEvent:
		fmt.Printf("Initializing TaskHistory v1\n")
		_, err := tx.Exec(taskHistorySchema)
		return true, err

	case *UpdateTaskTitleEvent:
		historyEvent := AddTaskHistoryEvent{
			TaskId:        evt.TaskId,
			UpdateType:    "update_title",
			SystemComment: fmt.Sprintf("Title updated to: %s", evt.Title),
		}
		_, err := tx.NamedExec(insertTaskHistoryV1Sql, historyEvent)
		return true, err

	case *UpdateTaskCompletedEvent:
		var comment string
		if evt.CompletedAt != nil {
			comment = fmt.Sprintf("Task marked as completed at %s", evt.CompletedAt.Format(time.RFC3339))
		} else {
			comment = "Task marked as not completed"
		}
		historyEvent := AddTaskHistoryEvent{
			TaskId:        evt.TaskId,
			UpdateType:    "update_completed",
			SystemComment: comment,
		}
		_, err := tx.NamedExec(insertTaskHistoryV1Sql, historyEvent)
		return true, err

	case *UpdateTaskDueDateEvent:
		var comment string
		if evt.DueDate != nil {
			comment = fmt.Sprintf("Due date set to %s", evt.DueDate.Format(time.RFC3339))
		} else {
			comment = "Due date removed"
		}
		historyEvent := AddTaskHistoryEvent{
			TaskId:        evt.TaskId,
			UpdateType:    "update_due_date",
			SystemComment: comment,
		}
		_, err := tx.NamedExec(insertTaskHistoryV1Sql, historyEvent)
		return true, err

	case *DeleteTaskEvent:
		historyEvent := AddTaskHistoryEvent{
			TaskId:        evt.TaskId,
			UpdateType:    "delete",
			SystemComment: "Task deleted",
		}
		_, err := tx.NamedExec(insertTaskHistoryV1Sql, historyEvent)
		return true, err

	case *AddTaskCommentEvent:
		historyEvent := AddTaskHistoryEvent{
			TaskId:      evt.TaskId,
			UpdateType:  "add_comment",
			UserComment: &evt.UserComment,
		}
		_, err := tx.NamedExec(insertTaskHistoryV1Sql, historyEvent)
		return true, err
	}

	return false, nil
}

// State queries
const getTaskHistoryV1Sql = `
SELECT id, task_id, update_type, system_comment, user_comment, created_at
FROM task_history_v1
WHERE task_id = $1
ORDER BY created_at DESC;
`

type TaskHistoryV1 struct {
	Id            int       `db:"id"`
	TaskId        int       `db:"task_id"`
	UpdateType    string    `db:"update_type"`
	SystemComment string    `db:"system_comment"`
	UserComment   *string   `db:"user_comment"`
	CreatedAt     time.Time `db:"created_at"`
}

type TaskHistoryV1Response struct {
	History []TaskHistoryV1 `json:"history"`
}

func taskHistoryDBForTask(db *sqlx.DB, taskId int) (TaskHistoryV1Response, error) {
	var history []TaskHistoryV1 = make([]TaskHistoryV1, 0)
	err := db.Select(&history, getTaskHistoryV1Sql, taskId)
	return TaskHistoryV1Response{History: history}, err
}
