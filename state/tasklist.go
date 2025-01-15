package state

import (
	"fmt"
	"net/http"
	"strconv"

	"github.com/jmoiron/sqlx"

	"tomyedwab.com/yellowstone-server/database"
	"tomyedwab.com/yellowstone-server/database/events"
)

// Table schema

const taskListSchema = `
CREATE TABLE IF NOT EXISTS task_list_v1 (
	id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    title TEXT NOT NULL,
    category TEXT NOT NULL,
    archived BOOLEAN NOT NULL
);
`

// Events

type AddTaskListEvent struct {
	events.GenericEvent

	Title    string
	Category string
	Archived bool
}

type UpdateTaskListTitleEvent struct {
	events.GenericEvent

	ListId int    `db:"list_id"`
	Title  string `db:"title"`
}

type UpdateTaskListArchivedEvent struct {
	events.GenericEvent

	ListId   int  `db:"list_id"`
	Archived bool `db:"archived"`
}

// Event handler

const insertTaskListV1Sql = `
INSERT INTO task_list_v1 (title, category, archived)
VALUES (:title, :category, :archived);
`

const updateTaskListTitleV1Sql = `
UPDATE task_list_v1
SET title = :title
WHERE id = :list_id;
`

const updateTaskListArchivedV1Sql = `
UPDATE task_list_v1
SET archived = :archived
WHERE id = :list_id;
`

func TaskListDBHandleEvent(tx *sqlx.Tx, event events.Event) (bool, error) {
	switch evt := event.(type) {
	case *events.DBInitEvent:
		fmt.Printf("Initializing TaskList v1\n")
		_, err := tx.Exec(taskListSchema)
		return true, err

	case *AddTaskListEvent:
		fmt.Printf("TaskList v1: AddTaskListEvent %d %v %v %v\n", evt.Id, evt.Title, evt.Category, evt.Archived)
		_, err := tx.NamedExec(
			insertTaskListV1Sql,
			*evt,
		)
		return true, err

	case *UpdateTaskListTitleEvent:
		fmt.Printf("TaskList v1: UpdateTaskListTitleEvent %d %d %v\n", evt.Id, evt.ListId, evt.Title)
		_, err := tx.NamedExec(
			updateTaskListTitleV1Sql,
			*evt,
		)
		return true, err

	case *UpdateTaskListArchivedEvent:
		fmt.Printf("TaskList v1: UpdateTaskListArchivedEvent %d %d %v\n", evt.Id, evt.ListId, evt.Archived)
		_, err := tx.NamedExec(
			updateTaskListArchivedV1Sql,
			*evt,
		)
		return true, err
	}
	return false, nil
}

// State queries

const getAllTaskListsV1Sql = `
SELECT id, title, category, archived FROM task_list_v1;
`

const getCategoryTaskListsV1Sql = `
SELECT id, title, category, archived FROM task_list_v1 WHERE archived = false AND category = $1;
`

const getArchivedTaskListsV1Sql = `
SELECT id, title, category, archived FROM task_list_v1 WHERE archived = true;
`

const getTaskListByIdV1Sql = `
SELECT id, title, category, archived FROM task_list_v1 WHERE id = $1;
`

type TaskListV1 struct {
	Id       int
	Title    string
	Category string
	Archived bool
}

type TaskListV1Response struct {
	TaskLists []TaskListV1
}

func taskListDBAll(db *sqlx.DB) (TaskListV1Response, error) {
	var taskLists []TaskListV1 = make([]TaskListV1, 0)
	err := db.Select(&taskLists, getAllTaskListsV1Sql)
	return TaskListV1Response{TaskLists: taskLists}, err
}

func taskListDBToDo(db *sqlx.DB) (TaskListV1Response, error) {
	var taskLists []TaskListV1 = make([]TaskListV1, 0)
	err := db.Select(&taskLists, getCategoryTaskListsV1Sql, "toDoList")
	return TaskListV1Response{TaskLists: taskLists}, err
}

func taskListDBTemplate(db *sqlx.DB) (TaskListV1Response, error) {
	var taskLists []TaskListV1 = make([]TaskListV1, 0)
	err := db.Select(&taskLists, getCategoryTaskListsV1Sql, "template")
	return TaskListV1Response{TaskLists: taskLists}, err
}

func taskListDBArchived(db *sqlx.DB) (TaskListV1Response, error) {
	var taskLists []TaskListV1 = make([]TaskListV1, 0)
	err := db.Select(&taskLists, getArchivedTaskListsV1Sql)
	return TaskListV1Response{TaskLists: taskLists}, err
}

func taskListDBById(db *sqlx.DB, id int) (TaskListV1, error) {
	var taskList TaskListV1
	err := db.Get(&taskList, getTaskListByIdV1Sql, id)
	return taskList, err
}

func InitTaskListHandlers(db *database.Database) {
	http.HandleFunc("/tasklist/get", func(w http.ResponseWriter, r *http.Request) {
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

		resp, err := taskListDBById(db.GetDB(), id)
		database.HandleAPIResponse(w, resp, err)
	})
	http.HandleFunc("/tasklist/all", func(w http.ResponseWriter, r *http.Request) {
		resp, err := taskListDBAll(db.GetDB())
		database.HandleAPIResponse(w, resp, err)
	})

	http.HandleFunc("/tasklist/todo", func(w http.ResponseWriter, r *http.Request) {
		resp, err := taskListDBToDo(db.GetDB())
		database.HandleAPIResponse(w, resp, err)
	})

	http.HandleFunc("/tasklist/template", func(w http.ResponseWriter, r *http.Request) {
		resp, err := taskListDBTemplate(db.GetDB())
		database.HandleAPIResponse(w, resp, err)
	})

	http.HandleFunc("/tasklist/archived", func(w http.ResponseWriter, r *http.Request) {
		resp, err := taskListDBArchived(db.GetDB())
		database.HandleAPIResponse(w, resp, err)
	})
}
