package state

import (
	"fmt"

	"github.com/jmoiron/sqlx"
	"tomyedwab.com/yellowstone-server/tasks/generated"
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

func InitTaskList(tx *sqlx.Tx) error {
	fmt.Printf("Initializing TaskList v1\n")
	_, err := tx.Exec(taskListSchema)
	return err
}

// Event handler

const insertTaskListV1Sql = `
INSERT INTO task_list_v1 (title, category, archived, position)
VALUES (:title, :category, :archived, (SELECT IFNULL(MAX(position), 0) + 1 FROM task_list_v1));
`

const updateTaskListTitleV1Sql = `
UPDATE task_list_v1
SET title = :title
WHERE id = :listid;
`

const updateTaskListArchivedV1Sql = `
UPDATE task_list_v1
SET archived = :archived
WHERE id = :listid;
`

const reorderTaskListV1Sql = `
WITH old_list AS (
  SELECT position FROM task_list_v1 WHERE id = :listid
), new_list AS (
  SELECT position + 1 AS position FROM task_list_v1 WHERE id = :afterlistid
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
  WHEN id = :listid THEN 1
  ELSE position + 1
END
`

func (h *StateEventHandler) HandleTaskListAddEvent(tx *sqlx.Tx, event *generated.TaskListAddEvent) (bool, error) {
	fmt.Printf("TaskList v1: AddTaskListEvent %v %v %v\n", event.Title, event.Category, event.Archived)
	_, err := tx.NamedExec(
		insertTaskListV1Sql,
		*event,
	)
	return true, err
}

func (h *StateEventHandler) HandleTaskListUpdateTitleEvent(tx *sqlx.Tx, event *generated.TaskListUpdateTitleEvent) (bool, error) {
	fmt.Printf("TaskList v1: UpdateTaskListTitleEvent %d %v\n", event.ListId, event.Title)
	_, err := tx.NamedExec(
		updateTaskListTitleV1Sql,
		*event,
	)
	return true, err
}

func (h *StateEventHandler) HandleTaskListUpdateArchivedEvent(tx *sqlx.Tx, event *generated.TaskListUpdateArchivedEvent) (bool, error) {
	fmt.Printf("TaskList v1: UpdateTaskListArchivedEvent %d %v\n", event.ListId, event.Archived)
	_, err := tx.NamedExec(
		updateTaskListArchivedV1Sql,
		*event,
	)
	return true, err
}

func (h *StateEventHandler) HandleTaskListReorderEvent(tx *sqlx.Tx, event *generated.TaskListReorderEvent) (bool, error) {
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

func (r *StateResolver) GetApiTasklistGet(db *sqlx.DB, id int) (generated.TaskList, error) {
	var taskList generated.TaskList
	err := db.Get(&taskList, getTaskListByIdV1Sql, id)
	return taskList, err
}

func (r *StateResolver) GetApiTasklistAll(db *sqlx.DB) (generated.TaskListResponse, error) {
	var taskLists []generated.TaskList = make([]generated.TaskList, 0)
	err := db.Select(&taskLists, getAllTaskListsV1Sql)
	return generated.TaskListResponse{TaskLists: taskLists}, err
}

func (r *StateResolver) GetApiTasklistTodo(db *sqlx.DB) (generated.TaskListResponse, error) {
	var taskLists []generated.TaskList = make([]generated.TaskList, 0)
	err := db.Select(&taskLists, getCategoryTaskListsV1Sql, "toDoList")
	return generated.TaskListResponse{TaskLists: taskLists}, err
}

func (r *StateResolver) GetApiTasklistTemplate(db *sqlx.DB) (generated.TaskListResponse, error) {
	var taskLists []generated.TaskList = make([]generated.TaskList, 0)
	err := db.Select(&taskLists, getCategoryTaskListsV1Sql, "template")
	return generated.TaskListResponse{TaskLists: taskLists}, err
}

func (r *StateResolver) GetApiTasklistArchived(db *sqlx.DB) (generated.TaskListResponse, error) {
	var taskLists []generated.TaskList = make([]generated.TaskList, 0)
	err := db.Select(&taskLists, getArchivedTaskListsV1Sql)
	return generated.TaskListResponse{TaskLists: taskLists}, err
}
