package state

import (
	"encoding/json"
	"fmt"

	"tomyedwab.com/yellowstone-server/database/events"
)

func EventMapper(message *json.RawMessage, generic *events.GenericEvent) (events.Event, error) {
	switch generic.Type {
	case "yellowstone:addTaskList":
		var event AddTaskListEvent
		err := json.Unmarshal(*message, &event)
		if err != nil {
			return nil, fmt.Errorf("error parsing yellowstone:addTaskList %d: %w", generic.Id, err)
		}
		return &event, nil

	case "yellowstone:updateTaskList":
		var event UpdateTaskListEvent
		err := json.Unmarshal(*message, &event)
		if err != nil {
			return nil, fmt.Errorf("error parsing yellowstone:updateTaskList %d: %w", generic.Id, err)
		}
		return &event, nil

	default:
		return generic, nil
	}
}
