package state

import (
	"fmt"
	"net/http"
	"strconv"
	"time"

	"github.com/jmoiron/sqlx"

	"tomyedwab.com/yellowstone-server/database"
	"tomyedwab.com/yellowstone-server/database/events"
)

// Table schema

const taskSchema = `
CREATE TABLE IF NOT EXISTS task_v1 (
	id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    task_list_id INTEGER NOT NULL,
    title TEXT NOT NULL,
    due_date DATETIME,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at DATETIME,
    FOREIGN KEY(task_list_id) REFERENCES task_list_v1(id)
);
`

// Events

type AddTaskEvent struct {
	events.GenericEvent

	TaskListId int       `db:"task_list_id"`
	Title      string    `db:"title"`
	DueDate    time.Time `db:"due_date"`
}

type UpdateTaskTitleEvent struct {
	events.GenericEvent

	TaskId int    `db:"task_id"`
	Title  string `db:"title"`
}

type UpdateTaskCompletedEvent struct {
	events.GenericEvent

	TaskId      int       `db:"task_id"`
	Completed   bool      `db:"completed"`
	CompletedAt time.Time `db:"completed_at"`
}

// Event handler

const insertTaskV1Sql = `
INSERT INTO task_v1 (task_list_id, title, due_date)
VALUES (:task_list_id, :title, :due_date);
`

const updateTaskTitleV1Sql = `
UPDATE task_v1
SET title = :title
WHERE id = :task_id;
`

const updateTaskCompletedV1Sql = `
UPDATE task_v1
SET completed = :completed,
    completed_at = :completed_at
WHERE id = :task_id;
`

func TaskDBHandleEvent(tx *sqlx.Tx, event events.Event) (bool, error) {
	switch evt := event.(type) {
	case *events.DBInitEvent:
		fmt.Printf("Initializing Task v1\n")
		_, err := tx.Exec(taskSchema)
		return true, err

	case *AddTaskEvent:
		fmt.Printf("Task v1: AddTaskEvent %d %v\n", evt.Id, evt.Title)
		_, err := tx.NamedExec(
			insertTaskV1Sql,
			*evt,
		)
		return true, err

	case *UpdateTaskTitleEvent:
		fmt.Printf("Task v1: UpdateTaskTitleEvent %d %d %v\n", evt.Id, evt.TaskId, evt.Title)
		_, err := tx.NamedExec(
			updateTaskTitleV1Sql,
			*evt,
		)
		return true, err

	case *UpdateTaskCompletedEvent:
		fmt.Printf("Task v1: UpdateTaskCompletedEvent %d %d %v\n", evt.Id, evt.TaskId, evt.Completed)
		_, err := tx.NamedExec(
			updateTaskCompletedV1Sql,
			*evt,
		)
		return true, err
	}
	return false, nil
}

// State queries

const getTaskByIdV1Sql = `
SELECT id, task_list_id, title, due_date, completed, completed_at
FROM task_v1 WHERE id = $1;
`

const getTasksForListV1Sql = `
SELECT id, task_list_id, title, due_date, completed, completed_at
FROM task_v1 WHERE task_list_id = $1
ORDER BY id;
`

type TaskV1 struct {
	Id          int
	TaskListId  int       `db:"task_list_id"`
	Title       string
	DueDate     time.Time `db:"due_date"`
	Completed   bool
	CompletedAt time.Time `db:"completed_at"`
}

type TaskV1Response struct {
	Tasks []TaskV1
}

func taskDBById(db *sqlx.DB, id int) (TaskV1, error) {
	var task TaskV1
	err := db.Get(&task, getTaskByIdV1Sql, id)
	return task, err
}

func taskDBForList(db *sqlx.DB, taskListId int) (TaskV1Response, error) {
	var tasks []TaskV1 = make([]TaskV1, 0)
	err := db.Select(&tasks, getTasksForListV1Sql, taskListId)
	return TaskV1Response{Tasks: tasks}, err
}

func InitTaskHandlers(db *database.Database) {
	http.HandleFunc("/task/get", func(w http.ResponseWriter, r *http.Request) {
		idStr := r.URL.Query().Get("id")
		if idStr == "" {
			http.Error(w, "Missing id parameter", http.StatusBadRequest)
			return
		}
		
		id, err := strconv.Atoi(idStr)
		if err != nil {
			http.Error(w, "Invalid id parameter", http.StatusBadRequest)
			return
		}

		resp, err := taskDBById(db.GetDB(), id)
		database.HandleAPIResponse(w, resp, err)
	})

	http.HandleFunc("/task/list", func(w http.ResponseWriter, r *http.Request) {
		taskListIdStr := r.URL.Query().Get("taskListId")
		if taskListIdStr == "" {
			http.Error(w, "Missing taskListId parameter", http.StatusBadRequest)
			return
		}
		
		taskListId, err := strconv.Atoi(taskListIdStr)
		if err != nil {
			http.Error(w, "Invalid taskListId parameter", http.StatusBadRequest)
			return
		}

		resp, err := taskDBForList(db.GetDB(), taskListId)
		database.HandleAPIResponse(w, resp, err)
	})
}
