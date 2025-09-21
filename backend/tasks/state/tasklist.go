package state

import (
	"fmt"
	"net/http"
	"strconv"

	"github.com/jmoiron/sqlx"

	"github.com/tomyedwab/yesterday/applib/database"
	"github.com/tomyedwab/yesterday/applib/httputils"
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

const AddTaskListEventType = "TaskList:Add"
const UpdateTaskListTitleEventType = "TaskList:UpdateTitle"
const UpdateTaskListArchivedEventType = "TaskList:UpdateArchived"
const ReorderTaskListEventType = "TaskList:Reorder"

type AddTaskListEvent struct {
	Title    string
	Category string
	Archived bool
}

type UpdateTaskListTitleEvent struct {
	ListId int    `db:"list_id"`
	Title  string `db:"title"`
}

type UpdateTaskListArchivedEvent struct {
	ListId   int  `db:"list_id"`
	Archived bool `db:"archived"`
}

type ReorderTaskListEvent struct {
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

func InitTaskList(db *database.Database, tx *sqlx.Tx) error {
	database.AddEventHandler(db, AddTaskListEventType, handleAddTaskListEvent)
	database.AddEventHandler(db, UpdateTaskListTitleEventType, handleUpdateTaskListTitleEvent)
	database.AddEventHandler(db, UpdateTaskListArchivedEventType, handleUpdateTaskListArchivedEvent)
	database.AddEventHandler(db, ReorderTaskListEventType, handleReorderTaskListEvent)
	InitTaskListHandlers(db)

	fmt.Printf("Initializing TaskList v1\n")
	_, err := tx.Exec(taskListSchema)
	return err
}

func handleAddTaskListEvent(tx *sqlx.Tx, event *AddTaskListEvent) (bool, error) {
	fmt.Printf("TaskList v1: AddTaskListEvent %v %v %v\n", event.Title, event.Category, event.Archived)
	_, err := tx.NamedExec(
		insertTaskListV1Sql,
		*event,
	)
	return true, err
}

func handleUpdateTaskListTitleEvent(tx *sqlx.Tx, event *UpdateTaskListTitleEvent) (bool, error) {
	fmt.Printf("TaskList v1: UpdateTaskListTitleEvent %d %v\n", event.ListId, event.Title)
	_, err := tx.NamedExec(
		updateTaskListTitleV1Sql,
		*event,
	)
	return true, err
}

func handleUpdateTaskListArchivedEvent(tx *sqlx.Tx, event *UpdateTaskListArchivedEvent) (bool, error) {
	fmt.Printf("TaskList v1: UpdateTaskListArchivedEvent %d %v\n", event.ListId, event.Archived)
	_, err := tx.NamedExec(
		updateTaskListArchivedV1Sql,
		*event,
	)
	return true, err
}

func handleReorderTaskListEvent(tx *sqlx.Tx, event *ReorderTaskListEvent) (bool, error) {
	fmt.Printf("TaskList v1: ReorderTaskListEvent %d %d\n", event.ListId, event.AfterListId)
	if event.AfterListId == nil {
		_, err := tx.NamedExec(
			reorderTaskListToFrontV1Sql,
			*event,
		)
		return true, err
	}
	_, err := tx.NamedExec(
		reorderTaskListV1Sql,
		*event,
	)
	return true, err
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
	http.HandleFunc("/api/tasklist/get", func(w http.ResponseWriter, r *http.Request) {
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
		httputils.HandleAPIResponse(w, r, resp, err, http.StatusInternalServerError)
	})

	http.HandleFunc("/api/tasklist/all", func(w http.ResponseWriter, r *http.Request) {
		resp, err := taskListDBAll(db.GetDB())
		httputils.HandleAPIResponse(w, r, resp, err, http.StatusInternalServerError)
	})

	http.HandleFunc("/api/tasklist/todo", func(w http.ResponseWriter, r *http.Request) {
		resp, err := taskListDBToDo(db.GetDB())
		httputils.HandleAPIResponse(w, r, resp, err, http.StatusInternalServerError)
	})

	http.HandleFunc("/api/tasklist/template", func(w http.ResponseWriter, r *http.Request) {
		resp, err := taskListDBTemplate(db.GetDB())
		httputils.HandleAPIResponse(w, r, resp, err, http.StatusInternalServerError)
	})

	http.HandleFunc("/api/tasklist/archived", func(w http.ResponseWriter, r *http.Request) {
		resp, err := taskListDBArchived(db.GetDB())
		httputils.HandleAPIResponse(w, r, resp, err, http.StatusInternalServerError)
	})

	http.HandleFunc("/api/tasklist/metadata", func(w http.ResponseWriter, r *http.Request) {
		resp, err := taskListDBMetadata(db.GetDB())
		httputils.HandleAPIResponse(w, r, map[string][]TaskListMetadataV1{
			"Metadata": resp,
		}, err, http.StatusInternalServerError)
	})

	http.HandleFunc("/api/tasklist/recent_comments", func(w http.ResponseWriter, r *http.Request) {
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
		httputils.HandleAPIResponse(w, r, resp, err, http.StatusInternalServerError)
	})

	http.HandleFunc("/api/tasklist/labels", func(w http.ResponseWriter, r *http.Request) {
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
		httputils.HandleAPIResponse(w, r, resp, err, http.StatusInternalServerError)
	})
}
