import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

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
}

Widget withResponsiveService(
  BuildContext context,
  Widget Function(ResponsiveService responsiveService) builder,
) {
  final ResponsiveService responsiveService = ResponsiveService(MediaQuery.of(context).size.width, MediaQuery.of(context).size.height);
  return builder(responsiveService);
}
