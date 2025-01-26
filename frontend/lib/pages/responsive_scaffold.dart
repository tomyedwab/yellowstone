import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class ResponsiveScaffold extends StatelessWidget {
  final List<Widget> children;
  final int selectedIndex;

  const ResponsiveScaffold({super.key, required this.children, required this.selectedIndex});

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 860) {
          return Scaffold(
            body: children[children.length - 1],
            bottomNavigationBar: NavigationBar(
              indicatorColor: const Color(0xff7faad0),
              onDestinationSelected: (int index) {
                if (index == 0) {
                  context.go('/');
                } else if (index == 1) {
                  context.go('/templates');
                } else if (index == 2) {
                  context.go('/archived');
                }
              },
              selectedIndex: selectedIndex,
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
                    context.go('/templates');
                  } else if (index == 2) {
                    context.go('/archived');
                  }
                },
                selectedIndex: selectedIndex,
                destinations: const <NavigationRailDestination>[
                  NavigationRailDestination(
                    padding: EdgeInsets.symmetric(vertical: 8),
                    icon: Icon(Icons.list),
                    label: Text('Lists'),
                  ),
                  NavigationRailDestination(
                    padding: EdgeInsets.symmetric(vertical: 8),
                    icon: Icon(Icons.copy_all),
                    label: Text('Templates'),
                  ),
                  NavigationRailDestination(
                    padding: EdgeInsets.symmetric(vertical: 8),
                    icon: Icon(Icons.archive),
                    label: Text('Archived'),
                  ),
                ],
              ),
              // First column
              SizedBox(
                width: 250,
                child: Scaffold(
                  body: children[0],
                ),
              ),
              if (children.length > 1) ...[
                const VerticalDivider(
                  width: 1,
                  thickness: 1,
                  color: Color(0xff182631),
                ),
                SizedBox(
                  width: 350,
                  child: children[1],
                ),
              ],
              if (children.length > 2) ...[
                const VerticalDivider(
                  width: 1,
                  thickness: 1,
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
