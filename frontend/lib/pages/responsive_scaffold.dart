import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:package_info_plus/package_info_plus.dart';
import '../services/responsive_service.dart';
import '../services/rest_data_service.dart';

class ResponsiveScaffold extends StatelessWidget {
  final List<Widget> children;
  final int selectedIndex;
  final RestDataService restDataService;
  final ResponsiveService responsiveService;

  const ResponsiveScaffold({super.key, required this.children, required this.selectedIndex, required this.restDataService, required this.responsiveService});

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final listIcon = GestureDetector(
          onLongPress: () async {
            PackageInfo packageInfo = await PackageInfo.fromPlatform();
            showDialog(
              context: context,
              builder: (context) => AlertDialog(
                title: const Text('Yellowstone'),
                content: Text('Client version ${packageInfo.version}\nServer version ${restDataService.serverVersion}'),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.pop(context),
                    child: const Text('OK'),
                  ),
                ],
              ),
            );
          },
          child: const Icon(Icons.list),
        );
        if (responsiveService.layoutType == LayoutType.vertical) {
          return Scaffold(
            body: children[children.length - 1],
            bottomNavigationBar: NavigationBar(
              indicatorColor: const Color(0xff7faad0),
              onDestinationSelected: (int index) {
                if (index == 0) {
                  context.go('/');
                } else if (index == 1) {
                  context.go('/labels');
                } else if (index == 2) {
                  context.go('/templates');
                } else if (index == 3) {
                  context.go('/archived');
                }
              },
              selectedIndex: selectedIndex,
              destinations: <NavigationDestination>[
                NavigationDestination(
                  icon: listIcon,
                  label: 'Lists',
                ),
                const NavigationDestination(
                  icon: Icon(Icons.label),
                  label: 'Labels',
                ),
                const NavigationDestination(
                  icon: Icon(Icons.copy_all),
                  label: 'Templates',
                ),
                const NavigationDestination(
                  icon: Icon(Icons.archive),
                  label: 'Archived',
                ),
              ],
            ),
          );
        }
        
        // Tablet/Desktop layout
        return Scaffold(
          body: Row(
            children: [
              NavigationRail(
                indicatorColor: const Color(0xff7faad0),
                labelType: NavigationRailLabelType.all,
                groupAlignment: 0.0,
                onDestinationSelected: (int index) {
                  if (index == 0) {
                    context.go('/');
                  } else if (index == 1) {
                    context.go('/labels');
                  } else if (index == 2) {
                    context.go('/templates');
                  } else if (index == 3) {
                    context.go('/archived');
                  }
                },
                selectedIndex: selectedIndex,
                destinations: <NavigationRailDestination>[
                  NavigationRailDestination(
                    padding: EdgeInsets.symmetric(vertical: 8),
                    icon: listIcon,
                    label: Text('Lists'),
                  ),
                  const NavigationRailDestination(
                    padding: EdgeInsets.symmetric(vertical: 8),
                    icon: Icon(Icons.label),
                    label: Text('Labels'),
                  ),
                  const NavigationRailDestination(
                    padding: EdgeInsets.symmetric(vertical: 8),
                    icon: Icon(Icons.copy_all),
                    label: Text('Templates'),
                  ),
                  const NavigationRailDestination(
                    padding: EdgeInsets.symmetric(vertical: 8),
                    icon: Icon(Icons.archive),
                    label: Text('Archived'),
                  ),
                ],
              ),
              // First column
              children.length > 1 ? SizedBox(
                width: 300,
                child: Scaffold(
                  body: children[0],
                ),
              ) : Expanded(child: children[0]),
              if (children.length > 1) ...[
                const VerticalDivider(
                  width: 1,
                  thickness: 2,
                  color: Color(0xff182631),
                ),
                children.length > 2 ? SizedBox(
                  width: 350,
                  child: children[1],
                ) : Expanded(child: children[1]),
              ],
              if (children.length > 2) ...[
                const VerticalDivider(
                  width: 1,
                  thickness: 2,
                  color: Color(0xff182631),
                ),
                Expanded(
                  child: children[2],
                ),
              ],
            ],
          ),
        );
      },
    );
  }
}
