package state

import (
	"fmt"
	"time"

	"github.com/jmoiron/sqlx"

	"github.com/tomyedwab/yesterday/applib/database"
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

const AddTaskCommentEventType = "Task:AddComment"

type AddTaskHistoryEvent struct {
	TaskId        int     `db:"task_id"`
	UpdateType    string  `db:"update_type"`
	SystemComment string  `db:"system_comment"`
	UserComment   *string `db:"user_comment"`
}

type AddTaskCommentEvent struct {
	TaskId      int    `db:"task_id"`
	UserComment string `db:"user_comment"`
}

// Event handler
const insertTaskHistoryV1Sql = `
INSERT INTO task_history_v1 (task_id, update_type, system_comment, user_comment)
VALUES (:task_id, :update_type, :system_comment, :user_comment);
`

func InitTaskHistory(db *database.Database, tx *sqlx.Tx) error {
	database.AddEventHandler(db, UpdateTaskTitleEventType, handleHistoryUpdateTaskTitleEvent)
	database.AddEventHandler(db, UpdateTaskCompletedEventType, handleHistoryUpdateTaskCompletedEvent)
	database.AddEventHandler(db, UpdateTaskDueDateEventType, handleHistoryUpdateTaskDueDateEvent)
	database.AddEventHandler(db, DeleteTaskEventType, handleHistoryDeleteTaskEvent)
	database.AddEventHandler(db, AddTaskCommentEventType, handleAddTaskCommentEvent)

	fmt.Printf("Initializing TaskHistory v1\n")
	_, err := tx.Exec(taskHistorySchema)
	return err
}

func handleHistoryUpdateTaskTitleEvent(tx *sqlx.Tx, event *UpdateTaskTitleEvent) (bool, error) {
	historyEvent := AddTaskHistoryEvent{
		TaskId:        event.TaskId,
		UpdateType:    "update_title",
		SystemComment: fmt.Sprintf("Title updated to: %s", event.Title),
	}
	_, err := tx.NamedExec(insertTaskHistoryV1Sql, historyEvent)
	return true, err
}

func handleHistoryUpdateTaskCompletedEvent(tx *sqlx.Tx, event *UpdateTaskCompletedEvent) (bool, error) {
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
	_, err := tx.NamedExec(insertTaskHistoryV1Sql, historyEvent)
	return true, err
}

func handleHistoryUpdateTaskDueDateEvent(tx *sqlx.Tx, event *UpdateTaskDueDateEvent) (bool, error) {
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
	_, err := tx.NamedExec(insertTaskHistoryV1Sql, historyEvent)
	return true, err
}

func handleHistoryDeleteTaskEvent(tx *sqlx.Tx, event *DeleteTaskEvent) (bool, error) {
	historyEvent := AddTaskHistoryEvent{
		TaskId:        event.TaskId,
		UpdateType:    "delete",
		SystemComment: "Task deleted",
	}
	_, err := tx.NamedExec(insertTaskHistoryV1Sql, historyEvent)
	return true, err
}

func handleAddTaskCommentEvent(tx *sqlx.Tx, event *AddTaskCommentEvent) (bool, error) {
	historyEvent := AddTaskHistoryEvent{
		TaskId:      event.TaskId,
		UpdateType:  "add_comment",
		UserComment: &event.UserComment,
	}
	_, err := tx.NamedExec(insertTaskHistoryV1Sql, historyEvent)
	return true, err
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
	Title   string          `json:"title"`
}

func taskHistoryDBForTask(db *sqlx.DB, taskId int) (TaskHistoryV1Response, error) {
	var history []TaskHistoryV1 = make([]TaskHistoryV1, 0)
	err := db.Select(&history, getTaskHistoryV1Sql, taskId)

	var title string
	if err == nil {
		err = db.Get(&title, getTaskTitleV1Sql, taskId)
	}

	return TaskHistoryV1Response{History: history, Title: title}, err
}
