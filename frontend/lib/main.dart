import 'package:flutter/material.dart';
import 'package:flutter_web_plugins/url_strategy.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:go_router/go_router.dart';

import 'services/rest_data_service.dart';
import 'services/responsive_service.dart';
import 'router.dart';

void main() {
  usePathUrlStrategy();
  runApp(YellowstoneApp());
}

class YellowstoneApp extends StatefulWidget {
  YellowstoneApp({super.key});

  @override
  State<YellowstoneApp> createState() => _YellowstoneAppState();
}

class _YellowstoneAppState extends State<YellowstoneApp> {
  final RestDataService _dataService = RestDataService();
  late final GoRouter _router;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _router = createRouter(_dataService);
    _dataService.setNavigateToLoginHandler(() {
      _router.go('/login');
    });

    // Check initial server version
    if (_dataService.serverVersion.isNotEmpty) {
      setState(() {
        _isLoading = false;
      });
    }

    // Listen for changes in RestDataService
    _dataService.addListener(_handleDataServiceChange);
  }

  void _handleDataServiceChange() {
    if (_dataService.serverVersion.isNotEmpty && _isLoading) {
      setState(() {
        _isLoading = false;
      });
    }
  }

  @override
  void dispose() {
    _dataService.removeListener(_handleDataServiceChange);
    super.dispose();
  }

  // Global navigator key to enable navigation from outside of build context
  final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return MaterialApp(
        theme: ThemeData(
          useMaterial3: true,
          brightness: Brightness.dark,
          scaffoldBackgroundColor: const Color(0xff111e2a),
          colorScheme: const ColorScheme.dark(
            primary: Color(0xfff6fbff),
            surface: Color(0xff111e2a),
          ),
          textTheme: TextTheme(
            bodyMedium: GoogleFonts.roboto(color: const Color(0xfff6fbff)),
          ),
        ),
        home: const Scaffold(
          body: Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                CircularProgressIndicator(),
                SizedBox(height: 16),
                Text('Connecting to server...'),
              ],
            ),
          ),
        ),
      );
    }

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
