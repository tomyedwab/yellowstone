package state

import (
	"fmt"
	"net/http"
	"strconv"
	"time"

	"github.com/jmoiron/sqlx"

	"github.com/tomyedwab/yesterday/applib/database"
	"github.com/tomyedwab/yesterday/applib/httputils"
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

// Events

const AddTaskEventType = "Task:Add"
const UpdateTaskTitleEventType = "Task:UpdateTitle"
const UpdateTaskCompletedEventType = "Task:UpdateCompleted"
const UpdateTaskDueDateEventType = "Task:UpdateDueDate"
const DeleteTaskEventType = "Task:Delete"

type AddTaskEvent struct {
	Title      string     `db:"title"`
	DueDate    *time.Time `db:"due_date"`
	TaskListId int        `db:"task_list_id"`
}

type UpdateTaskTitleEvent struct {
	TaskId int    `db:"task_id"`
	Title  string `db:"title"`
}

type UpdateTaskCompletedEvent struct {
	TaskId      int        `db:"task_id"`
	CompletedAt *time.Time `db:"completed_at"`
}

type UpdateTaskDueDateEvent struct {
	TaskId  int        `db:"task_id"`
	DueDate *time.Time `db:"due_date"`
}

type DeleteTaskEvent struct {
	TaskId int `db:"task_id"`
}

// Event handler

const insertTaskV1Sql = `
INSERT INTO task_v1 (title, due_date)
VALUES (:title, :due_date);
`

const updateTaskTitleV1Sql = `
UPDATE task_v1
SET title = :title
WHERE id = :task_id;
`

const updateTaskCompletedV1Sql = `
UPDATE task_v1
SET completed_at = :completed_at
WHERE id = :task_id;
`

const updateTaskDueDateV1Sql = `
UPDATE task_v1
SET due_date = :due_date
WHERE id = :task_id;
`

const deleteTaskV1Sql = `
DELETE FROM task_v1
WHERE id = :task_id;
`

func InitTask(db *database.Database, tx *sqlx.Tx) error {
	database.AddEventHandler(db, AddTaskEventType, handleAddTaskEvent)
	database.AddEventHandler(db, UpdateTaskTitleEventType, handleUpdateTaskTitleEvent)
	database.AddEventHandler(db, UpdateTaskCompletedEventType, handleUpdateTaskCompletedEvent)
	database.AddEventHandler(db, UpdateTaskDueDateEventType, handleUpdateTaskDueDateEvent)
	database.AddEventHandler(db, DeleteTaskEventType, handleDeleteTaskEvent)
	InitTaskHandlers(db)
	fmt.Printf("Initializing Task v1\n")
	_, err := tx.Exec(taskSchema)
	return err
}

func handleAddTaskEvent(tx *sqlx.Tx, event *AddTaskEvent) (bool, error) {
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
	addToList := AddTaskToListEvent{
		TaskId: int(taskId),
		ListId: event.TaskListId,
	}
	_, err = tx.NamedExec(insertTaskToListV1Sql, addToList)
	return true, err
}

func handleUpdateTaskTitleEvent(tx *sqlx.Tx, event *UpdateTaskTitleEvent) (bool, error) {
	fmt.Printf("Task v1: UpdateTaskTitleEvent %d %v\n", event.TaskId, event.Title)
	_, err := tx.NamedExec(
		updateTaskTitleV1Sql,
		*event,
	)
	return true, err
}

func handleUpdateTaskCompletedEvent(tx *sqlx.Tx, event *UpdateTaskCompletedEvent) (bool, error) {
	fmt.Printf("Task v1: UpdateTaskCompletedEvent %d %v\n", event.TaskId, event.CompletedAt)
	_, err := tx.NamedExec(
		updateTaskCompletedV1Sql,
		*event,
	)
	return true, err
}

func handleUpdateTaskDueDateEvent(tx *sqlx.Tx, event *UpdateTaskDueDateEvent) (bool, error) {
	fmt.Printf("Task v1: UpdateTaskDueDateEvent %d %v\n", event.TaskId, event.DueDate)
	_, err := tx.NamedExec(
		updateTaskDueDateV1Sql,
		*event,
	)
	return true, err
}

func handleDeleteTaskEvent(tx *sqlx.Tx, event *DeleteTaskEvent) (bool, error) {
	fmt.Printf("Task v1: DeleteTaskEvent %d\n", event.TaskId)
	_, err := tx.NamedExec(
		deleteTaskV1Sql,
		*event,
	)
	return true, err
}

// State queries

const getTaskByIdV1Sql = `
SELECT id, title, due_date, completed_at
FROM task_v1 WHERE id = $1;
`

const getTaskTitleV1Sql = `
SELECT title
FROM task_v1
WHERE id = $1;
`

type TaskV1 struct {
	Id          int
	Title       string
	DueDate     *time.Time `db:"due_date"`
	CompletedAt *time.Time `db:"completed_at"`
}

type TaskV1Response struct {
	Tasks []TaskV1
}

func taskDBById(db *sqlx.DB, id int) (TaskV1, error) {
	var task TaskV1
	err := db.Get(&task, getTaskByIdV1Sql, id)
	return task, err
}

func InitTaskHandlers(db *database.Database) {
	http.HandleFunc("/api/task/list", func(w http.ResponseWriter, r *http.Request) {
		listIdStr := r.URL.Query().Get("listId")
		if listIdStr == "" {
			http.Error(w, "Missing listId parameter", http.StatusBadRequest)
			return
		}

		listId, err := strconv.Atoi(listIdStr)
		if err != nil {
			http.Error(w, "Invalid listId parameter", http.StatusBadRequest)
			return
		}

		resp, err := taskDBForList(db.GetDB(), listId)
		httputils.HandleAPIResponse(w, r, resp, err, http.StatusInternalServerError)
	})

	http.HandleFunc("/api/task/get", func(w http.ResponseWriter, r *http.Request) {
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
		httputils.HandleAPIResponse(w, r, resp, err, http.StatusInternalServerError)
	})

	http.HandleFunc("/api/task/history", func(w http.ResponseWriter, r *http.Request) {
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

		resp, err := taskHistoryDBForTask(db.GetDB(), id)

		httputils.HandleAPIResponse(w, r, resp, err, http.StatusInternalServerError)
	})
}
