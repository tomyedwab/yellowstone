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

	case "yellowstone:updateTaskListTitle":
		var event UpdateTaskListTitleEvent
		err := json.Unmarshal(*message, &event)
		if err != nil {
			return nil, fmt.Errorf("error parsing yellowstone:updateTaskListTitle %d: %w", generic.Id, err)
		}
		return &event, nil

	case "yellowstone:updateTaskListArchived":
		var event UpdateTaskListArchivedEvent
		err := json.Unmarshal(*message, &event)
		if err != nil {
			return nil, fmt.Errorf("error parsing yellowstone:updateTaskListArchived %d: %w", generic.Id, err)
		}
		fmt.Printf("EventMapper: yellowstone:updateTaskListArchived %d %v\n", event.ListId, event.Archived) // donotcheckin
		return &event, nil

	case "yellowstone:addTask":
		var event AddTaskEvent
		err := json.Unmarshal(*message, &event)
		if err != nil {
			return nil, fmt.Errorf("error parsing yellowstone:addTask %d: %w", generic.Id, err)
		}
		return &event, nil

	case "yellowstone:updateTaskTitle":
		var event UpdateTaskTitleEvent
		err := json.Unmarshal(*message, &event)
		if err != nil {
			return nil, fmt.Errorf("error parsing yellowstone:updateTaskTitle %d: %w", generic.Id, err)
		}
		return &event, nil

	case "yellowstone:updateTaskCompleted":
		var event UpdateTaskCompletedEvent
		err := json.Unmarshal(*message, &event)
		if err != nil {
			return nil, fmt.Errorf("error parsing yellowstone:updateTaskCompleted %d: %w", generic.Id, err)
		}
		return &event, nil

	default:
		return generic, nil
	}
}
