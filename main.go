package main

import (
	"net/http"

	_ "github.com/mattn/go-sqlite3"

	"tomyedwab.com/yellowstone-server/database"
	"tomyedwab.com/yellowstone-server/state"
)

const Version = "0.0.0"

func main() {
	db, err := database.Connect("sqlite3", "yellowstone.db", Version, map[string]database.EventUpdateHandler{
		"task_list_v1":    state.TaskListDBHandleEvent,
		"task_v1":         state.TaskDBHandleEvent,
		"task_to_list_v1": state.TaskToListDBHandleEvent,
		"task_history_v1": state.TaskHistoryDBHandleEvent,
	})
	if err != nil {
		panic(err)
	}

	db.InitHandlers(state.EventMapper)
	state.InitTaskListHandlers(db)
	state.InitTaskHandlers(db)

	err = http.ListenAndServe("0.0.0.0:8334", nil)
	if err != nil {
		panic(err)
	}
}
