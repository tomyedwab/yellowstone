import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'pages/archived_lists_page.dart';
import 'pages/responsive_scaffold.dart';
import 'pages/todo_lists_page.dart';
import 'pages/task_list_page.dart';
import 'pages/task_history_page.dart';
import 'pages/templates_page.dart';
import 'pages/labels_page.dart';
import 'pages/login_page.dart';
import 'services/rest_data_service.dart';
import 'services/responsive_service.dart';

List<GoRoute> createSubRoutes(
  RestDataService dataService,
  int selectedIndex,
  Widget Function(ResponsiveService, int?) mainPage,
  String taskListPrefix,
) {
  return [
    GoRoute(
      path: 'list/:listId',
      builder: (context, state) => withResponsiveService(context, (responsiveService) => ResponsiveScaffold(
        restDataService: dataService,
        responsiveService: responsiveService,
        selectedIndex: selectedIndex,
        children: [
          mainPage(responsiveService, int.parse(state.pathParameters['listId']!)),
          TaskListPage(
            key: ValueKey('taskListPage-${state.pathParameters['listId']}-${state.pathParameters['taskId']}'),
            dataService: dataService,
            responsiveService: responsiveService,
            taskListId: int.parse(state.pathParameters['listId']!),
            taskListPrefix: taskListPrefix,
            selectedTaskId: null,
          ),
        ],
      )),
      routes: [
        GoRoute(
          path: 'task/:taskId/history',
          builder: (context, state) => withResponsiveService(context, (responsiveService) => ResponsiveScaffold(
            restDataService: dataService,
            responsiveService: responsiveService,
            selectedIndex: selectedIndex,
            children: [
              mainPage(responsiveService, int.parse(state.pathParameters['listId']!)),
              TaskListPage(
                key: ValueKey('taskListPage-${state.pathParameters['listId']}-${state.pathParameters['taskId']}'),
                dataService: dataService,
                responsiveService: responsiveService,
                taskListId: int.parse(state.pathParameters['listId']!),
                taskListPrefix: taskListPrefix,
                selectedTaskId: int.parse(state.pathParameters['taskId']!),
              ),
              TaskHistoryPage(
                key: ValueKey('taskHistoryPage-${state.pathParameters['taskId']}'),
                taskId: int.parse(state.pathParameters['taskId']!),
                responsiveService: responsiveService,
              ),
            ],
          )),
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
        builder: (context, state) => withResponsiveService(context, (responsiveService) => ResponsiveScaffold(
          restDataService: dataService,
          responsiveService: responsiveService,
          selectedIndex: 0,
          children: [
            ToDoListsPage(dataService: dataService, responsiveService: responsiveService),
          ],
        )),
        routes: createSubRoutes(
          dataService,
          0,
          (responsiveService, selectedListId) => ToDoListsPage(dataService: dataService, selectedListId: selectedListId, responsiveService: responsiveService),
          '/'),
      ),
      GoRoute(
        path: '/labels',
        builder: (context, state) => withResponsiveService(context, (responsiveService) => ResponsiveScaffold(
          restDataService: dataService,
          responsiveService: responsiveService,
          selectedIndex: 1,
          children: [
            LabelsPage(dataService: dataService, responsiveService: responsiveService),
          ],
        )),
        routes: createSubRoutes(
          dataService,
          1,
          (responsiveService, selectedListId) => LabelsPage(dataService: dataService, selectedListId: selectedListId, responsiveService: responsiveService),
          '/labels/'),
      ),
      GoRoute(
        path: '/templates',
        builder: (context, state) => withResponsiveService(context, (responsiveService) => ResponsiveScaffold(
          restDataService: dataService,
          responsiveService: responsiveService,
          selectedIndex: 2,
          children: [
            TemplatesPage(dataService: dataService, responsiveService: responsiveService),
          ],
        )),
        routes: createSubRoutes(
          dataService,
          2,
          (responsiveService, selectedListId) => TemplatesPage(dataService: dataService, selectedListId: selectedListId, responsiveService: responsiveService),
          '/templates/'),
      ),
      GoRoute(
        path: '/archived',
        builder: (context, state) => withResponsiveService(context, (responsiveService) => ResponsiveScaffold(
          restDataService: dataService,
          responsiveService: responsiveService,
          selectedIndex: 3,
          children: [
            ArchivedListsPage(dataService: dataService, responsiveService: responsiveService),
          ],
        )),
        routes: createSubRoutes(
          dataService,
          3,
          (responsiveService, selectedListId) => ArchivedListsPage(dataService: dataService, selectedListId: selectedListId, responsiveService: responsiveService),
          '/archived/'),
      ),
      GoRoute(
        path: '/login',
        builder: (context, state) => LoginPage(
          loginUrl: state.uri.queryParameters['loginUrl']!,
          onLoginSuccess: () {
            context.go('/');
          },
        ),
      ),
    ],
  );
}
