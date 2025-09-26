package state

import (
	"fmt"

	"github.com/jmoiron/sqlx"

	"tomyedwab.com/yellowstone-server/tasks/generated"
)

// Table schema
const taskToListSchema = `
CREATE TABLE IF NOT EXISTS task_to_list_v1 (
    task_id INTEGER NOT NULL,
    list_id INTEGER NOT NULL,
    position INTEGER NOT NULL,
    PRIMARY KEY (task_id, list_id),
    FOREIGN KEY (task_id) REFERENCES task_v1(id),
    FOREIGN KEY (list_id) REFERENCES task_list_v1(id)
);
`

func InitTaskToList(tx *sqlx.Tx) error {
	fmt.Printf("Initializing TaskToList v1\n")
	_, err := tx.Exec(taskToListSchema)
	return err
}

// Event handler
const insertTaskToListV1Sql = `
INSERT INTO task_to_list_v1 (task_id, list_id, position)
SELECT :taskid, :listid, COALESCE(MAX(position), 0) + 1
FROM task_to_list_v1 WHERE list_id = :listid
ON CONFLICT (task_id, list_id) DO NOTHING;
`

const deleteTaskToListV1Sql = `
DELETE FROM task_to_list_v1
WHERE task_id = :taskid;
`

const moveTaskFromListV1Sql = `
DELETE FROM task_to_list_v1
WHERE task_id = :taskid AND list_id = :oldlistid;
`

const moveTaskToListV1Sql = `
INSERT INTO task_to_list_v1 (task_id, list_id, position)
SELECT :taskid, :newlistid, COALESCE(MAX(position), 0) + 1
FROM task_to_list_v1 WHERE list_id = :newlistid
ON CONFLICT (task_id, list_id) DO NOTHING;
`

// We can get into situations where multiple tasks have the same position in the
// list. Repairing just means ordering the tasks by position and then updating
// the position to be the row number.
const repairTaskOrderV1Sql = `
UPDATE task_to_list_v1
SET position = (
    SELECT row_num
    FROM (
        SELECT task_id, ROW_NUMBER() OVER (ORDER BY position) as row_num
        FROM task_to_list_v1
        WHERE list_id = :tasklistid
    ) numbered
    WHERE numbered.task_id = task_to_list_v1.task_id
)
WHERE list_id = :tasklistid;
`

const reorderTasksV1Sql = `
WITH old_task AS (
  SELECT position FROM task_to_list_v1 WHERE task_id = :oldtaskid AND list_id = :tasklistid
), new_task AS (
  SELECT position + 1 AS position FROM task_to_list_v1 WHERE task_id = :aftertaskid AND list_id = :tasklistid
)
UPDATE task_to_list_v1
SET position = CASE
  WHEN position = (SELECT position FROM old_task) THEN (SELECT position FROM new_task)
  WHEN position > (SELECT position FROM old_task) AND position < (SELECT position FROM new_task) THEN position - 1
  WHEN position < (SELECT position FROM old_task) AND position >= (SELECT position FROM new_task) THEN position + 1
  ELSE position
END
WHERE list_id = :tasklistid;
`

const reorderTaskToFrontV1Sql = `
UPDATE task_to_list_v1
SET position = CASE
  WHEN task_id = :oldtaskid THEN 1
  ELSE position + 1
END
WHERE list_id = :tasklistid;
`

const duplicateTaskV1Sql = `
INSERT INTO task_v1 (title, due_date, completed_at)
SELECT title, due_date, NULL
FROM task_v1
WHERE id = $1
RETURNING id;
`

const addDuplicatedTaskToListV1Sql = `
INSERT INTO task_to_list_v1 (task_id, list_id, position)
SELECT :taskid, :listid, COALESCE(MAX(position), 0) + 1
FROM task_to_list_v1 WHERE list_id = :listid;
`

func (h *StateEventHandler) HandleTaskListAddTaskEvent(tx *sqlx.Tx, event *generated.TaskListAddTaskEvent) (bool, error) {
	fmt.Printf("TaskToList v1: AddTaskToListEvent %d %d\n", event.TaskId, event.ListId)
	_, err := tx.NamedExec(insertTaskToListV1Sql, event)
	return true, err
}

func (h *StateEventHandler) HandleTaskListMoveTasksEvent(tx *sqlx.Tx, event *generated.TaskListMoveTasksEvent) (bool, error) {
	fmt.Printf("TaskToList v1: MoveTasksEvent %v from %d to %d\n", event.TaskIds, event.OldListId, event.NewListId)
	for _, taskId := range event.TaskIds {
		// First remove from old list
		_, err := tx.NamedExec(moveTaskFromListV1Sql, map[string]interface{}{
			"taskid":    taskId,
			"oldlistid": event.OldListId,
		})
		if err != nil {
			return true, err
		}

		// Then add to new list
		_, err = tx.NamedExec(moveTaskToListV1Sql, map[string]interface{}{
			"taskid":    taskId,
			"newlistid": event.NewListId,
		})
		if err != nil {
			return true, err
		}
	}
	return true, nil
}

func (h *StateEventHandler) HandleTaskListCopyTasksEvent(tx *sqlx.Tx, event *generated.TaskListCopyTasksEvent) (bool, error) {
	fmt.Printf("TaskToList v1: CopyTasksEvent %v to %d\n", event.TaskIds, event.NewListId)
	for _, taskId := range event.TaskIds {
		_, err := tx.NamedExec(insertTaskToListV1Sql, map[string]interface{}{
			"taskid": taskId,
			"listid": event.NewListId,
		})
		if err != nil {
			return true, err
		}
	}
	return true, nil
}

func (h *StateEventHandler) HandleTaskListReorderTasksEvent(tx *sqlx.Tx, event *generated.TaskListReorderTasksEvent) (bool, error) {
	_, err := tx.NamedExec(repairTaskOrderV1Sql, map[string]interface{}{
		"tasklistid": event.TaskListId,
	})
	if err != nil {
		return true, err
	}

	if event.AfterTaskId == nil {
		fmt.Printf("TaskToList v1: ReorderTasksEvent %d %d -> front\n", event.TaskListId, event.OldTaskId)
		_, err := tx.NamedExec(reorderTaskToFrontV1Sql, event)
		return true, err
	} else {
		fmt.Printf("TaskToList v1: ReorderTasksEvent %d %d -> %d\n", event.TaskListId, event.OldTaskId, *event.AfterTaskId)
		_, err := tx.NamedExec(reorderTasksV1Sql, event)
		return true, err
	}
}

func (h *StateEventHandler) HandleTaskListDuplicateTasksEvent(tx *sqlx.Tx, event *generated.TaskListDuplicateTasksEvent) (bool, error) {
	fmt.Printf("TaskToList v1: DuplicateTasksEvent %v to %d\n", event.TaskIds, event.NewListId)
	for _, sourceTaskId := range event.TaskIds {
		// First duplicate the task
		var newTaskId int
		err := tx.Get(&newTaskId, duplicateTaskV1Sql, sourceTaskId)
		if err != nil {
			return true, err
		}

		// Then add the new task to the target list
		_, err = tx.NamedExec(addDuplicatedTaskToListV1Sql, map[string]interface{}{
			"taskid": newTaskId,
			"listid": event.NewListId,
		})
		if err != nil {
			return true, err
		}
	}
	return true, nil
}

// State queries
const getTasksForListV1Sql = `
SELECT t.id, t.title, t.due_date AS duedate, t.completed_at AS completedat
FROM task_v1 t
JOIN task_to_list_v1 ttl ON t.id = ttl.task_id
WHERE ttl.list_id = $1
ORDER BY ttl.position;
`

// Returns the number of total & completed tasks for every list
const getTaskMetadataV1Sql = `
SELECT list_id AS listid, COUNT(*) AS total, SUM(CASE WHEN task_v1.completed_at IS NOT NULL THEN 1 ELSE 0 END) AS completed
FROM task_to_list_v1
LEFT JOIN task_v1 ON task_to_list_v1.task_id = task_v1.id
GROUP BY list_id;
`

const getRecentCommentsForListV1Sql = `
WITH latest_comments AS (
    SELECT task_id, user_comment, created_at,
           ROW_NUMBER() OVER (PARTITION BY task_id ORDER BY created_at DESC) as rn
    FROM task_history_v1
    WHERE user_comment IS NOT NULL
)
SELECT ttl.list_id AS listid, ttl.task_id AS taskid, lc.user_comment AS usercomment, lc.created_at AS createdat
FROM task_to_list_v1 ttl
LEFT JOIN latest_comments lc ON ttl.task_id = lc.task_id AND lc.rn = 1
WHERE ttl.list_id = $1 AND lc.user_comment IS NOT NULL
ORDER BY ttl.position;
`

const getTaskLabelsForListV1Sql = `
SELECT ttl1.task_id AS taskid, ttl2.list_id AS listid, tl.title AS label
FROM task_to_list_v1 ttl1
LEFT JOIN task_to_list_v1 ttl2 ON ttl1.task_id = ttl2.task_id
LEFT JOIN task_list_v1 tl ON ttl2.list_id = tl.id
WHERE ttl1.list_id = $1 AND tl.category = 'label'
`

func (r *StateResolver) GetApiTaskList(db *sqlx.DB, listId int) (generated.TaskResponse, error) {
	var tasks []generated.Task = make([]generated.Task, 0)
	err := db.Select(&tasks, getTasksForListV1Sql, listId)
	return generated.TaskResponse{Tasks: tasks}, err
}

func (r *StateResolver) GetApiTasklistMetadata(db *sqlx.DB) (generated.TaskListMetadataResponse, error) {
	var metadata []generated.TaskListMetadata = make([]generated.TaskListMetadata, 0)
	err := db.Select(&metadata, getTaskMetadataV1Sql)
	return generated.TaskListMetadataResponse{Metadata: metadata}, err
}

func (r *StateResolver) GetApiTasklistRecent_comments(db *sqlx.DB, listId int) (generated.TaskRecentCommentResponse, error) {
	var comments []generated.TaskRecentComment = make([]generated.TaskRecentComment, 0)
	err := db.Select(&comments, getRecentCommentsForListV1Sql, listId)
	return generated.TaskRecentCommentResponse{Comments: comments}, err
}

func (r *StateResolver) GetApiTasklistLabels(db *sqlx.DB, listId int) (generated.TaskLabelsResponse, error) {
	var labels []generated.TaskLabels = make([]generated.TaskLabels, 0)
	err := db.Select(&labels, getTaskLabelsForListV1Sql, listId)
	return generated.TaskLabelsResponse{Labels: labels}, err
}
