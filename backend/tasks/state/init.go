package state

import (
	"tomyedwab.com/yellowstone-server/tasks/generated"
)

type StateResolver struct {
}

type StateEventHandler struct {
}

func NewResolver() generated.Resolver {
	return &StateResolver{}
}

func NewEventHandler() generated.EventHandler {
	return &StateEventHandler{}
}
