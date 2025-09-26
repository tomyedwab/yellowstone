package state

import (
	"fmt"

	"github.com/jmoiron/sqlx"
	"tomyedwab.com/yellowstone-server/tasks/generated"
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

func InitTaskHistory(tx *sqlx.Tx) error {
	fmt.Printf("Initializing TaskHistory v1\n")
	_, err := tx.Exec(taskHistorySchema)
	return err
}

// Events

type AddTaskHistoryEvent struct {
	TaskId        int     `db:"task_id"`
	UpdateType    string  `db:"update_type"`
	SystemComment string  `db:"system_comment"`
	UserComment   *string `db:"user_comment"`
}

// Event handler
const insertTaskHistoryV1Sql = `
INSERT INTO task_history_v1 (task_id, update_type, system_comment, user_comment)
VALUES (:task_id, :update_type, :system_comment, :user_comment);
`

func (h *StateEventHandler) HandleTaskAddCommentEvent(tx *sqlx.Tx, event *generated.TaskAddCommentEvent) (bool, error) {
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
SELECT id, task_id AS taskid, update_type AS updatetype, system_comment AS systemcomment, user_comment AS usercomment, created_at AS createdat
FROM task_history_v1
WHERE task_id = $1
ORDER BY created_at DESC;
`

func (r *StateResolver) GetApiTaskHistory(db *sqlx.DB, id int) (generated.TaskHistoryResponse, error) {
	var history []generated.TaskHistory = make([]generated.TaskHistory, 0)
	err := db.Select(&history, getTaskHistoryV1Sql, id)

	var title string
	if err == nil {
		err = db.Get(&title, getTaskTitleV1Sql, id)
	}

	return generated.TaskHistoryResponse{History: history, Title: title}, err
}
