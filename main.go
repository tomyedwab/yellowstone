package main

import (
	"net/http"

	_ "github.com/mattn/go-sqlite3"

	"github.com/tomyedwab/yesterday/database"
	"tomyedwab.com/yellowstone-server/state"
)

const Version = "1.0.7"

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

	// Serve static files
	fs := http.FileServer(http.Dir("frontend/build/web"))
	handler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "*")
		if r.Method == "OPTIONS" {
			return
		}
		fs.ServeHTTP(w, r)
	})
	http.Handle("/", handler)

	err = http.ListenAndServe("0.0.0.0:8334", nil)
	if err != nil {
		panic(err)
	}
}
