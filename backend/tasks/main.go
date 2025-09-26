package main

import (
	"log"

	_ "github.com/mattn/go-sqlite3"

	"github.com/tomyedwab/yesterday/applib"
	"tomyedwab.com/yellowstone-server/tasks/generated"
	"tomyedwab.com/yellowstone-server/tasks/state"
)

const Version = "1.0.9"

func initApplication(application *applib.Application) error {
	var err error

	db := application.GetDatabase()

	if err = db.Initialize(); err != nil {
		return err
	}

	tx := db.GetDB().MustBegin()
	defer tx.Rollback()
	if err = state.InitTask(tx); err != nil {
		return err
	}
	if err = state.InitTaskList(tx); err != nil {
		return err
	}
	if err = state.InitTaskHistory(tx); err != nil {
		return err
	}
	if err = state.InitTaskToList(tx); err != nil {
		return err
	}
	if err = tx.Commit(); err != nil {
		return err
	}

	generated.InitHandlers(db, state.NewResolver(), state.NewEventHandler())
	return nil
}

func main() {
	application, err := applib.Init()
	if err != nil {
		log.Fatal(err)
	}

	err = initApplication(application)
	if err != nil {
		log.Fatal(err)
	}

	application.Serve()
}
