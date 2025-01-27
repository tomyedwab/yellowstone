import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

enum LayoutType {
  vertical,
  horizontal,
}

class ResponsiveService {
  final double width;
  final double height;
  LayoutType _layoutType = LayoutType.vertical;

  ResponsiveService(this.width, this.height) {
    if (width >= 860) {
      _layoutType = LayoutType.horizontal;
    } else {
      _layoutType = LayoutType.vertical;
    }
  }

  LayoutType get layoutType => _layoutType;

  double get listsViewBottomBoxSize => _layoutType == LayoutType.horizontal ? (
    kIsWeb ? 80 : 88
  ) : (
    kIsWeb ? 160 : 192
  );

  double get tasksViewBottomBoxSize => _layoutType == LayoutType.horizontal ? (
    kIsWeb ? 144 : 144
  ) : (
    kIsWeb ? 280 : 328
  );

  bool get reorderableHandlesVisible => kIsWeb;
}

Widget withResponsiveService(
  BuildContext context,
  Widget Function(ResponsiveService responsiveService) builder,
) {
  final ResponsiveService responsiveService = ResponsiveService(MediaQuery.of(context).size.width, MediaQuery.of(context).size.height);
  return builder(responsiveService);
}
