import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'pages/responsive_scaffold.dart';
import 'pages/todo_lists_page.dart';
import 'pages/task_list_view.dart';
import 'pages/task_history_page.dart';
import 'pages/templates_page.dart';
import 'pages/archived_lists_page.dart';
import 'services/rest_data_service.dart';

List<GoRoute> createSubRoutes(RestDataService dataService, int selectedIndex, Widget Function(int?) mainPage, String taskListPrefix) {
  return [
    GoRoute(
      path: 'list/:listId',
      builder: (context, state) => ResponsiveScaffold(
        selectedIndex: selectedIndex,
        children: [
          mainPage(int.parse(state.pathParameters['listId']!)),
          TaskListView(
            key: ValueKey('taskListView-${state.pathParameters['listId']}-${state.pathParameters['taskId']}'),
            dataService: dataService,
            taskListId: int.parse(state.pathParameters['listId']!),
            taskListPrefix: taskListPrefix,
            selectedTaskId: null,
          ),
        ],
      ),
      routes: [
        GoRoute(
          path: 'task/:taskId/history',
          builder: (context, state) => ResponsiveScaffold(
            selectedIndex: selectedIndex,
            children: [
              mainPage(int.parse(state.pathParameters['listId']!)),
              TaskListView(
                key: ValueKey('taskListView-${state.pathParameters['listId']}-${state.pathParameters['taskId']}'),
                dataService: dataService,
                taskListId: int.parse(state.pathParameters['listId']!),
                taskListPrefix: taskListPrefix,
                selectedTaskId: int.parse(state.pathParameters['taskId']!),
              ),
              TaskHistoryPage(
                key: ValueKey('taskHistoryPage-${state.pathParameters['taskId']}'),
                taskId: int.parse(state.pathParameters['taskId']!),
              ),
            ],
          ),
        ),
      ]
    ),
  ];
}

GoRouter createRouter(RestDataService dataService) {
  return GoRouter(
    initialLocation: '/',
    routes: [
      GoRoute(
        path: '/',
        builder: (context, state) => ResponsiveScaffold(
          selectedIndex: 0,
          children: [
            ToDoListsPage(dataService: dataService),
          ],
        ),
        routes: createSubRoutes(dataService, 0, (selectedListId) => ToDoListsPage(dataService: dataService, selectedListId: selectedListId), '/'),
      ),
      GoRoute(
        path: '/templates',
        builder: (context, state) => ResponsiveScaffold(
          selectedIndex: 1,
          children: [
            TemplatesPage(dataService: dataService),
          ],
        ),
        routes: createSubRoutes(dataService, 1, (selectedListId) => TemplatesPage(dataService: dataService, selectedListId: selectedListId), '/templates/'),
      ),
      GoRoute(
        path: '/archived',
        builder: (context, state) => ResponsiveScaffold(
          selectedIndex: 2,
          children: [
            ArchivedListsPage(dataService: dataService),
          ],
        ),
        routes: createSubRoutes(dataService, 2, (selectedListId) => ArchivedListsPage(dataService: dataService, selectedListId: selectedListId), '/archived/'),
      ),
    ],
  );
}
