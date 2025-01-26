import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_web_plugins/url_strategy.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:go_router/go_router.dart';

import 'pages/todo_lists_page.dart';
import 'pages/templates_page.dart';
import 'pages/archived_lists_page.dart';
import 'pages/login_page.dart';
import 'services/rest_data_service.dart';
import 'router.dart';

void main() {
  usePathUrlStrategy();
  runApp(YellowstoneApp());
}

class YellowstoneApp extends StatelessWidget {
  final RestDataService _dataService = RestDataService();
  late final GoRouter _router;

  YellowstoneApp({super.key}) {
    _router = createRouter(_dataService);
    _dataService.setLoginRedirectHandler((url) {
      // Navigate to login page when a redirect is received
      navigatorKey.currentState?.push(
        MaterialPageRoute(
          builder: (context) => LoginPage(
            loginUrl: url,
            onLoginSuccess: () {
              // Pop the login page and return to root
              navigatorKey.currentState?.pop();
            },
          ),
        ),
      );
    });
  }

  // Global navigator key to enable navigation from outside of build context
  final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      routerConfig: _router,
      title: 'Yellowstone',
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xff111e2a),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xfff6fbff),
          surface: Color(0xff111e2a),
        ),
        appBarTheme: AppBarTheme(
          backgroundColor: const Color(0xff111e2a),
          surfaceTintColor: const Color(0xff111e2a),
          elevation: 0,
          iconTheme: const IconThemeData(color: Color(0xfff6fbff)),
          titleTextStyle: GoogleFonts.archivo(
            color: const Color(0xfff6fbff),
            fontSize: 28,
            fontWeight: FontWeight.w500,
          ),
        ),
        listTileTheme: const ListTileThemeData(
          iconColor: Color(0xfff6fbff),
          textColor: Color(0xfff6fbff),
          contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        ),
        dividerTheme: const DividerThemeData(
          color: Colors.transparent,
        ),
        textTheme: TextTheme(
          bodyLarge: GoogleFonts.roboto(color: const Color(0xfff6fbff)),
          bodyMedium: GoogleFonts.roboto(color: const Color(0xfff6fbff)),
          bodySmall: GoogleFonts.roboto(color: const Color(0xfff6fbff)),
          titleMedium: GoogleFonts.roboto(color: const Color(0xfff6fbff)),
        ),
        iconTheme: const IconThemeData(
          color: Color(0xfff6fbff),
        ),
      ),
    );
  }
}