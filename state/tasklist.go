package state

import (
	"fmt"
	"net/http"
	"strconv"

	"github.com/jmoiron/sqlx"

	"tomyedwab.com/yellowstone-server/database"
	"tomyedwab.com/yellowstone-server/database/events"
	"tomyedwab.com/yellowstone-server/database/middleware"
)

// Table schema

const taskListSchema = `
CREATE TABLE IF NOT EXISTS task_list_v1 (
	id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    title TEXT NOT NULL,
    category TEXT NOT NULL,
    archived BOOLEAN NOT NULL,
	position INTEGER NOT NULL
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

type ReorderTaskListEvent struct {
	events.GenericEvent

	ListId      int  `db:"list_id"`
	AfterListId *int `db:"after_list_id"`
}

// Event handler

const insertTaskListV1Sql = `
INSERT INTO task_list_v1 (title, category, archived, position)
VALUES (:title, :category, :archived, (SELECT IFNULL(MAX(position), 0) + 1 FROM task_list_v1));
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

const reorderTaskListV1Sql = `
WITH old_list AS (
  SELECT position FROM task_list_v1 WHERE id = :list_id
), new_list AS (
  SELECT position + 1 AS position FROM task_list_v1 WHERE id = :after_list_id
)
UPDATE task_list_v1
SET position = CASE 
  WHEN position = (SELECT position FROM old_list) THEN (SELECT position FROM new_list)
  WHEN position > (SELECT position FROM old_list) AND position < (SELECT position FROM new_list) THEN position - 1
  WHEN position < (SELECT position FROM old_list) AND position >= (SELECT position FROM new_list) THEN position + 1
  ELSE position
END
`

const reorderTaskListToFrontV1Sql = `
UPDATE task_list_v1
SET position = CASE 
  WHEN id = :list_id THEN 1
  ELSE position + 1
END
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

	case *ReorderTaskListEvent:
		fmt.Printf("TaskList v1: ReorderTaskListEvent %d %d\n", evt.ListId, evt.AfterListId)
		if evt.AfterListId == nil {
			_, err := tx.NamedExec(
				reorderTaskListToFrontV1Sql,
				*evt,
			)
			return true, err
		}
		_, err := tx.NamedExec(
			reorderTaskListV1Sql,
			*evt,
		)
		return true, err
	}
	return false, nil
}

// State queries

const getAllTaskListsV1Sql = `
SELECT id, title, category, archived FROM task_list_v1 ORDER BY position;
`

const getCategoryTaskListsV1Sql = `
SELECT id, title, category, archived FROM task_list_v1 WHERE archived = false AND category = $1 ORDER BY position;
`

const getArchivedTaskListsV1Sql = `
SELECT id, title, category, archived FROM task_list_v1 WHERE archived = true ORDER BY position;
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
	http.HandleFunc("/api/tasklist/get", middleware.ApplyDefault(
		func(w http.ResponseWriter, r *http.Request) {
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
			database.HandleAPIResponse(w, r, resp, err)
		},
	))
	http.HandleFunc("/api/tasklist/all", middleware.ApplyDefault(
		func(w http.ResponseWriter, r *http.Request) {
			resp, err := taskListDBAll(db.GetDB())
			database.HandleAPIResponse(w, r, resp, err)
		},
	))

	http.HandleFunc("/api/tasklist/todo", middleware.ApplyDefault(
		func(w http.ResponseWriter, r *http.Request) {
			resp, err := taskListDBToDo(db.GetDB())
			database.HandleAPIResponse(w, r, resp, err)
		},
	))

	http.HandleFunc("/api/tasklist/template", middleware.ApplyDefault(
		func(w http.ResponseWriter, r *http.Request) {
			resp, err := taskListDBTemplate(db.GetDB())
			database.HandleAPIResponse(w, r, resp, err)
		},
	))

	http.HandleFunc("/api/tasklist/archived", middleware.ApplyDefault(
		func(w http.ResponseWriter, r *http.Request) {
			resp, err := taskListDBArchived(db.GetDB())
			database.HandleAPIResponse(w, r, resp, err)
		},
	))

	http.HandleFunc("/api/tasklist/metadata", middleware.ApplyDefault(
		func(w http.ResponseWriter, r *http.Request) {
			resp, err := taskListDBMetadata(db.GetDB())
			database.HandleAPIResponse(w, r, resp, err)
		},
	))

	http.HandleFunc("/api/tasklist/recent_comments", middleware.ApplyDefault(
		func(w http.ResponseWriter, r *http.Request) {
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

			resp, err := taskListDBRecentComments(db.GetDB(), listId)
			database.HandleAPIResponse(w, r, resp, err)
		},
	))

	http.HandleFunc("/api/tasklist/labels", middleware.ApplyDefault(
		func(w http.ResponseWriter, r *http.Request) {
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

			resp, err := taskListDBGetAllTaskLabels(db.GetDB(), listId)
			database.HandleAPIResponse(w, r, resp, err)
		},
	))
}
