import 'package:flutter/material.dart';
import 'pages/todo_lists_page.dart';
import 'pages/templates_page.dart';
import 'pages/archived_lists_page.dart';
import 'services/rest_data_service.dart';

void main() {
  runApp(const YellowstoneApp());
}

class YellowstoneApp extends StatelessWidget {
  YellowstoneApp({super.key}) {
    _dataService.setLoginRedirectHandler((url) {
      // TODO: Implement login redirect
      print('Login redirect to: $url');
    });
  }

  final RestDataService _dataService = RestDataService();

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Yellowstone',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: RootPage(dataService: _dataService),
    );
  }
}

class RootPage extends StatefulWidget {
  final RestDataService dataService;

  const RootPage({
    super.key,
    required this.dataService,
  });

  @override
  State<RootPage> createState() => _RootPageState();
}

class _RootPageState extends State<RootPage> {
  int _selectedIndex = 0;

  late final List<Widget> _pages = [
    ToDoListsPage(dataService: widget.dataService),
    TemplatesPage(dataService: widget.dataService),
    ArchivedListsPage(dataService: widget.dataService),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: _pages[_selectedIndex],
      bottomNavigationBar: NavigationBar(
        onDestinationSelected: (int index) {
          setState(() {
            _selectedIndex = index;
          });
        },
        selectedIndex: _selectedIndex,
        destinations: const <NavigationDestination>[
          NavigationDestination(
            icon: Icon(Icons.list),
            label: 'Lists',
          ),
          NavigationDestination(
            icon: Icon(Icons.copy_all),
            label: 'Templates',
          ),
          NavigationDestination(
            icon: Icon(Icons.archive),
            label: 'Archived',
          ),
        ],
      ),
    );
  }
}

