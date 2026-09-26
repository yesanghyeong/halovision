import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import '../models/vision_profile.dart';
import 'document_question_screen.dart';
import '../services/layout_resolver.dart';

class DocumentResultScreen extends StatelessWidget {
  static const bool _showLayoutGuides = false;

  final VisionProfile profile;

  const DocumentResultScreen({
    super.key,
    required this.profile,
  });

  @override
  Widget build(BuildContext context) {
    const yellow = Color(0xFFF4D35E);

    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context),

            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(
                  20,
                  14,
                  20,
                  30,
                ),
                child: Column(
                  crossAxisAlignment:
                      CrossAxisAlignment.start,
                  children: [
                    const Text(
                      '문서 분석 결과',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 28,
                        fontWeight: FontWeight.w900,
                        letterSpacing: -0.6,
                      ),
                    ),

                    const SizedBox(height: 8),

                    Text(
                      '설정한 시야 영역과 정보 배치를 기준으로 문서를 재구성한 예시 화면입니다.',
                      style: TextStyle(
                        color: Colors.white.withValues(
                          alpha: 0.62,
                        ),
                        fontSize: 14,
                        height: 1.5,
                      ),
                    ),

                    const SizedBox(height: 22),

                    _buildAdaptiveDocument(context),

                    const SizedBox(height: 22),

                    SizedBox(
                      width: double.infinity,
                      child: FilledButton.icon(
                        onPressed: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) =>
                                  const DocumentQuestionScreen(),
                            ),
                          );
                        },
                        icon: const Icon(
                          Icons.record_voice_over_rounded,
                        ),
                        label: const Text(
                          '이 문서에 대해 질문하기',
                        ),
                        style: FilledButton.styleFrom(
                          backgroundColor: yellow,
                          foregroundColor: Colors.black,
                          padding: const EdgeInsets.symmetric(
                            vertical: 17,
                          ),
                          textStyle: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(
    BuildContext context,
  ) {
    const yellow = Color(0xFFF4D35E);

    return Padding(
      padding: const EdgeInsets.fromLTRB(
        10,
        10,
        14,
        8,
      ),
      child: Row(
        children: [
          IconButton(
            onPressed: () {
              Navigator.pop(context);
            },
            icon: const Icon(
              Icons.arrow_back_rounded,
              color: Colors.white,
              size: 28,
            ),
          ),

          const SizedBox(width: 4),

          const Expanded(
            child: Text(
              '분석 결과',
              style: TextStyle(
                color: Colors.white,
                fontSize: 20,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),

          IconButton(
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) =>
                      const DocumentQuestionScreen(),
                ),
              );
            },
            icon: const Icon(
              Icons.mic_none_rounded,
              color: yellow,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAdaptiveDocument(
    BuildContext context,
  ) {
    return AspectRatio(
      aspectRatio: 0.72,
      child: LayoutBuilder(
        builder: (
          context,
          constraints,
        ) {
          final width = constraints.maxWidth;
          final height = constraints.maxHeight;

          final resolved =
              LayoutResolver.resolve(
            profile,
          );

          final textRegion =
              resolved.textRegion;

          final mediaRegion =
              resolved.mediaRegion;

          return _applyVisualAdjustments(
            child: Container(
              decoration: BoxDecoration(
                color: _documentBackgroundColor(),
                borderRadius:
                    BorderRadius.circular(24),
                border: Border.all(
                  color: Colors.white.withValues(
                    alpha: 0.10,
                  ),
                ),
              ),
              clipBehavior: Clip.antiAlias,
              child: Stack(
                children: [
                  Positioned(
                    left: 18,
                    top: 16,
                    right: 18,
                    child: Text(
                      '시각 정보 접근성 향상을 위한 문서 예시',
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: _documentTextColor(),
                        fontSize:
                            19 * profile.fontScale,
                        fontWeight: profile.boldText
                            ? FontWeight.w900
                            : FontWeight.w800,
                        height:
                            profile.lineSpacing,
                      ),
                    ),
                  ),

                  if (_showLayoutGuides)
                    ..._buildAvoidRegions(
                      width,
                      height,
                    ),

                  if (_showLayoutGuides)
                    ..._buildVisibleRegions(
                      width,
                      height,
                    ),

                  _buildTextRegion(
                    textRegion,
                    width,
                    height,
                  ),

                  _buildMediaRegion(
                    context,
                    mediaRegion,
                    width,
                    height,
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _applyVisualAdjustments({
    required Widget child,
  }) {
    final brightness = profile.brightness;
    final sharpness = profile.sharpness;

    double contrast = profile.contrast;

    if (sharpness > 1.0) {
      contrast *=
          1.0 +
          ((sharpness - 1.0) * 0.18);
    }

    final contrastOffset =
        128.0 * (1.0 - contrast);

    Widget result = ColorFiltered(
      colorFilter: ColorFilter.matrix(
        [
          contrast,
          0,
          0,
          0,
          contrastOffset,
          0,
          contrast,
          0,
          0,
          contrastOffset,
          0,
          0,
          contrast,
          0,
          contrastOffset,
          0,
          0,
          0,
          1,
          0,
        ],
      ),
      child: child,
    );

    result = ColorFiltered(
      colorFilter: ColorFilter.matrix(
        [
          brightness,
          0,
          0,
          0,
          0,
          0,
          brightness,
          0,
          0,
          0,
          0,
          0,
          brightness,
          0,
          0,
          0,
          0,
          0,
          1,
          0,
        ],
      ),
      child: result,
    );

    if (sharpness < 1.0) {
      final blurAmount =
          (1.0 - sharpness) * 4.0;

      result = ImageFiltered(
        imageFilter: ui.ImageFilter.blur(
          sigmaX: blurAmount,
          sigmaY: blurAmount,
        ),
        child: result,
      );
    }

    return result;
  }

  Widget _buildTextRegion(
    LayoutRegion region,
    double width,
    double height,
  ) {
    return Positioned(
      left: region.x * width,
      top: region.y * height,
      width: region.width * width,
      height: region.height * height,
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: _contentBackgroundColor(),
          borderRadius:
              BorderRadius.circular(18),
          border: Border.all(
            color: _contentBorderColor(),
            width: 2,
          ),
        ),
        child: SingleChildScrollView(
          child: Text(
            'HaloVision은 문서의 텍스트, 그림, 표를 구분하고 사용자의 시야 특성에 맞게 다시 배치하는 것을 목표로 합니다.\n\n'
            '현재 화면은 백엔드 연결 전 프론트엔드 동작 확인을 위한 임시 분석 결과입니다.',
            style: TextStyle(
              color: _contentTextColor(),
              fontSize:
                  16 * profile.fontScale,
              fontWeight: profile.boldText
                  ? FontWeight.w900
                  : FontWeight.w500,
              height: profile.lineSpacing,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildMediaRegion(
    BuildContext context,
    LayoutRegion region,
    double width,
    double height,
  ) {
    return Positioned(
      left: region.x * width,
      top: region.y * height,
      width: region.width * width,
      height: region.height * height,
      child: Column(
        children: [
          Expanded(
            child: InkWell(
              onTap: () {
                _showFigureDetail(
                  context,
                );
              },
              borderRadius:
                  BorderRadius.circular(16),
              child: Container(
                width: double.infinity,
                decoration: BoxDecoration(
                  color: const Color(
                    0xFFECECEC,
                  ),
                  borderRadius:
                      BorderRadius.circular(16),
                  border: Border.all(
                    color: const Color(
                      0xFF63C174,
                    ),
                    width: 2,
                  ),
                ),
                child: const Center(
                  child: Icon(
                    Icons.image_outlined,
                    size: 52,
                    color: Colors.black45,
                  ),
                ),
              ),
            ),
          ),

          const SizedBox(height: 8),

          Expanded(
            child: InkWell(
              onTap: () {
                _showTableDetail(
                  context,
                );
              },
              borderRadius:
                  BorderRadius.circular(16),
              child: Container(
                width: double.infinity,
                padding:
                    const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: const Color(
                    0xFFF2F2F2,
                  ),
                  borderRadius:
                      BorderRadius.circular(16),
                  border: Border.all(
                    color: const Color(
                      0xFF63C174,
                    ),
                    width: 2,
                  ),
                ),
                child: const _MockTable(
                  compact: true,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  List<Widget> _buildAvoidRegions(
    double width,
    double height,
  ) {
    return profile.avoidRegions.map(
      (region) {
        final regionWidth =
            region.width * width;

        final regionHeight =
            region.height * height;

        if (region.type ==
            RegionShapeType.custom) {
          return Positioned(
            left: region.x * width,
            top: region.y * height,
            width: regionWidth,
            height: regionHeight,
            child: IgnorePointer(
              child: CustomPaint(
                painter:
                    _RegionPainter(
                  points: region.points,
                  color: const Color(
                    0xFFF4D35E,
                  ),
                ),
              ),
            ),
          );
        }

        return Positioned(
          left: region.x * width,
          top: region.y * height,
          width: regionWidth,
          height: regionHeight,
          child: IgnorePointer(
            child: Container(
              decoration: BoxDecoration(
                color: const Color(
                  0xFFF4D35E,
                ).withValues(
                  alpha: 0.32,
                ),
                shape: region.type ==
                        RegionShapeType.circle
                    ? BoxShape.circle
                    : BoxShape.rectangle,
                borderRadius:
                    region.type ==
                            RegionShapeType.ellipse
                        ? BorderRadius.all(
                            Radius.elliptical(
                              regionWidth,
                              regionHeight,
                            ),
                          )
                        : null,
                border: Border.all(
                  color: const Color(
                    0xFFF4D35E,
                  ),
                  width: 2.5,
                ),
              ),
            ),
          ),
        );
      },
    ).toList();
  }

  List<Widget> _buildVisibleRegions(
    double width,
    double height,
  ) {
    return profile.visibleRegions.map(
      (region) {
        final regionWidth =
            region.width * width;

        final regionHeight =
            region.height * height;

        if (region.type ==
            RegionShapeType.custom) {
          return Positioned(
            left: region.x * width,
            top: region.y * height,
            width: regionWidth,
            height: regionHeight,
            child: IgnorePointer(
              child: CustomPaint(
                painter:
                    _RegionPainter(
                  points: region.points,
                  color: const Color(
                    0xFF63C174,
                  ),
                ),
              ),
            ),
          );
        }

        return Positioned(
          left: region.x * width,
          top: region.y * height,
          width: regionWidth,
          height: regionHeight,
          child: IgnorePointer(
            child: Container(
              decoration: BoxDecoration(
                color: const Color(
                  0xFF63C174,
                ).withValues(
                  alpha: 0.14,
                ),
                shape: region.type ==
                        RegionShapeType.circle
                    ? BoxShape.circle
                    : BoxShape.rectangle,
                borderRadius:
                    region.type ==
                            RegionShapeType.ellipse
                        ? BorderRadius.all(
                            Radius.elliptical(
                              regionWidth,
                              regionHeight,
                            ),
                          )
                        : null,
                border: Border.all(
                  color: const Color(
                    0xFF63C174,
                  ),
                  width: 3,
                ),
              ),
            ),
          ),
        );
      },
    ).toList();
  }

  void _showFigureDetail(
    BuildContext context,
  ) {
    _showDetailSheet(
      context,
      title: '그림 상세 보기',
      child: const Icon(
        Icons.image_outlined,
        size: 110,
        color: Colors.black45,
      ),
    );
  }

  void _showTableDetail(
    BuildContext context,
  ) {
    _showDetailSheet(
      context,
      title: '표 상세 보기',
      child: const _MockTable(
        compact: false,
      ),
    );
  }

  void _showDetailSheet(
    BuildContext context, {
    required String title,
    required Widget child,
  }) {
    showModalBottomSheet(
      context: context,
      backgroundColor:
          const Color(0xFF111111),
      isScrollControlled: true,
      builder: (
        sheetContext,
      ) {
        return SafeArea(
          child: Padding(
            padding:
                const EdgeInsets.all(24),
            child: Column(
              mainAxisSize:
                  MainAxisSize.min,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        title,
                        style:
                            const TextStyle(
                          color:
                              Colors.white,
                          fontSize: 20,
                          fontWeight:
                              FontWeight.w900,
                        ),
                      ),
                    ),

                    IconButton(
                      onPressed: () {
                        Navigator.pop(
                          sheetContext,
                        );
                      },
                      icon: const Icon(
                        Icons.close_rounded,
                        color:
                            Colors.white,
                      ),
                    ),
                  ],
                ),

                const SizedBox(
                  height: 18,
                ),

                Container(
                  width:
                      double.infinity,
                  constraints:
                      const BoxConstraints(
                    minHeight: 240,
                    maxHeight: 460,
                  ),
                  padding:
                      const EdgeInsets.all(
                    20,
                  ),
                  decoration:
                      BoxDecoration(
                    color:
                        const Color(
                      0xFFF2F2F2,
                    ),
                    borderRadius:
                        BorderRadius.circular(
                      20,
                    ),
                  ),
                  child: Center(
                    child: child,
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Color _documentBackgroundColor() {
    if (profile.colorCorrectionMode ==
        ColorCorrectionMode.highContrast) {
      return Colors.black;
    }

    return const Color(
      0xFFF4F4F4,
    );
  }

  Color _documentTextColor() {
    if (profile.colorCorrectionMode ==
        ColorCorrectionMode.highContrast) {
      return Colors.white;
    }

    return Colors.black87;
  }

  Color _contentBackgroundColor() {
    switch (
        profile.colorCorrectionMode) {
      case ColorCorrectionMode.highContrast:
        return Colors.black;

      case ColorCorrectionMode.redGreenAssist:
        return const Color(
          0xFFFFF7E6,
        );

      case ColorCorrectionMode.blueYellowAssist:
        return const Color(
          0xFFF2F5FF,
        );

      case ColorCorrectionMode.normal:
        return Colors.white;
    }
  }

  Color _contentTextColor() {
    switch (
        profile.colorCorrectionMode) {
      case ColorCorrectionMode.highContrast:
        return Colors.white;

      case ColorCorrectionMode.redGreenAssist:
      case ColorCorrectionMode.blueYellowAssist:
      case ColorCorrectionMode.normal:
        return Colors.black87;
    }
  }

  Color _contentBorderColor() {
    switch (
        profile.colorCorrectionMode) {
      case ColorCorrectionMode.highContrast:
        return Colors.white;

      case ColorCorrectionMode.redGreenAssist:
        return const Color(
          0xFFE7A84E,
        );

      case ColorCorrectionMode.blueYellowAssist:
        return const Color(
          0xFF7086CE,
        );

      case ColorCorrectionMode.normal:
        return const Color(
          0xFF5AA9FF,
        );
    }
  }
}

class _RegionPainter
    extends CustomPainter {
  final List<Offset> points;
  final Color color;

  const _RegionPainter({
    required this.points,
    required this.color,
  });

  @override
  void paint(
    Canvas canvas,
    Size size,
  ) {
    if (points.isEmpty) {
      return;
    }

    final paint = Paint()
      ..color = color.withValues(
        alpha: 0.35,
      )
      ..style =
          PaintingStyle.stroke
      ..strokeWidth = 22
      ..strokeCap =
          StrokeCap.round
      ..strokeJoin =
          StrokeJoin.round;

    final path = Path();

    for (
      int i = 0;
      i < points.length;
      i++
    ) {
      final point = Offset(
        points[i].dx *
            size.width,
        points[i].dy *
            size.height,
      );

      if (i == 0) {
        path.moveTo(
          point.dx,
          point.dy,
        );
      } else {
        path.lineTo(
          point.dx,
          point.dy,
        );
      }
    }

    canvas.drawPath(
      path,
      paint,
    );
  }

  @override
  bool shouldRepaint(
    covariant _RegionPainter
        oldDelegate,
  ) {
    return true;
  }
}

class _MockTable
    extends StatelessWidget {
  final bool compact;

  const _MockTable({
    required this.compact,
  });

  @override
  Widget build(
    BuildContext context,
  ) {
    final fontSize =
        compact ? 10.0 : 14.0;

    final padding =
        compact ? 4.0 : 12.0;

    return Table(
      border: TableBorder.all(
        color: Colors.black38,
      ),
      children: [
        TableRow(
          decoration:
              const BoxDecoration(
            color: Color(
              0xFFE0E0E0,
            ),
          ),
          children: [
            _TableCell(
              text: '항목',
              bold: true,
              fontSize: fontSize,
              padding: padding,
            ),
            _TableCell(
              text: '결과',
              bold: true,
              fontSize: fontSize,
              padding: padding,
            ),
          ],
        ),
        TableRow(
          children: [
            _TableCell(
              text: '텍스트',
              fontSize: fontSize,
              padding: padding,
            ),
            _TableCell(
              text: '감지',
              fontSize: fontSize,
              padding: padding,
            ),
          ],
        ),
        TableRow(
          children: [
            _TableCell(
              text: '그림',
              fontSize: fontSize,
              padding: padding,
            ),
            _TableCell(
              text: '1개',
              fontSize: fontSize,
              padding: padding,
            ),
          ],
        ),
        TableRow(
          children: [
            _TableCell(
              text: '표',
              fontSize: fontSize,
              padding: padding,
            ),
            _TableCell(
              text: '1개',
              fontSize: fontSize,
              padding: padding,
            ),
          ],
        ),
      ],
    );
  }
}

class _TableCell
    extends StatelessWidget {
  final String text;
  final bool bold;
  final double fontSize;
  final double padding;

  const _TableCell({
    required this.text,
    required this.fontSize,
    required this.padding,
    this.bold = false,
  });

  @override
  Widget build(
    BuildContext context,
  ) {
    return Padding(
      padding:
          EdgeInsets.all(
        padding,
      ),
      child: Text(
        text,
        textAlign:
            TextAlign.center,
        style: TextStyle(
          color: Colors.black87,
          fontSize: fontSize,
          fontWeight: bold
              ? FontWeight.w800
              : FontWeight.w500,
        ),
      ),
    );
  }
}