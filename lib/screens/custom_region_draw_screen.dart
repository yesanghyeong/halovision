import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../models/vision_profile.dart';
import '../widgets/halo_primary_button.dart';

enum _DrawTool {
  draw,
  erase,
}

class CustomRegionDrawScreen extends StatefulWidget {
  const CustomRegionDrawScreen({
    super.key,
  });

  @override
  State<CustomRegionDrawScreen> createState() =>
      _CustomRegionDrawScreenState();
}

class _CustomRegionDrawScreenState
    extends State<CustomRegionDrawScreen> {
  final List<List<Offset>> _strokes = [];

  List<Offset> _currentStroke = [];

  _DrawTool _tool = _DrawTool.draw;

  Size _canvasSize = Size.zero;

  static const double _eraseRadius = 28;

  void _startGesture(Offset point) {
    if (_tool == _DrawTool.draw) {
      setState(() {
        _currentStroke = [point];
      });
    } else {
      _eraseAt(point);
    }
  }

  void _updateGesture(Offset point) {
    if (_tool == _DrawTool.draw) {
      if (_currentStroke.isEmpty) {
        return;
      }

      setState(() {
        _currentStroke.add(point);
      });
    } else {
      _eraseAt(point);
    }
  }

  void _endGesture() {
    if (_tool != _DrawTool.draw) {
      return;
    }

    if (_currentStroke.length < 2) {
      setState(() {
        _currentStroke = [];
      });

      return;
    }

    setState(() {
      _strokes.add(
        List<Offset>.from(_currentStroke),
      );

      _currentStroke = [];
    });
  }

  void _eraseAt(Offset point) {
    if (_strokes.isEmpty) {
      return;
    }

    setState(() {
      for (int strokeIndex = _strokes.length - 1;
          strokeIndex >= 0;
          strokeIndex--) {
        final stroke = _strokes[strokeIndex];

        stroke.removeWhere(
          (strokePoint) {
            final distance =
                (strokePoint - point).distance;

            return distance <= _eraseRadius;
          },
        );

        if (stroke.length < 2) {
          _strokes.removeAt(strokeIndex);
        }
      }
    });
  }

  void _undo() {
    if (_currentStroke.isNotEmpty) {
      setState(() {
        _currentStroke = [];
      });

      return;
    }

    if (_strokes.isEmpty) {
      return;
    }

    setState(() {
      _strokes.removeLast();
    });
  }

  void _clear() {
    setState(() {
      _strokes.clear();
      _currentStroke = [];
    });
  }

  void _selectTool(
    _DrawTool tool,
  ) {
    setState(() {
      _tool = tool;
      _currentStroke = [];
    });
  }

  bool get _hasDrawing {
    return _strokes.any(
      (stroke) => stroke.length >= 2,
    );
  }

  RegionShape? _buildRegion() {
    if (_canvasSize.width <= 0 ||
        _canvasSize.height <= 0) {
      return null;
    }

    final allPoints = <Offset>[];

    for (final stroke in _strokes) {
      allPoints.addAll(stroke);
    }

    if (allPoints.isEmpty) {
      return null;
    }

    double minX = double.infinity;
    double minY = double.infinity;
    double maxX = -double.infinity;
    double maxY = -double.infinity;

    for (final point in allPoints) {
      minX = math.min(minX, point.dx);
      minY = math.min(minY, point.dy);

      maxX = math.max(maxX, point.dx);
      maxY = math.max(maxY, point.dy);
    }

    final normalizedMinX =
        (minX / _canvasSize.width)
            .clamp(0.0, 1.0);

    final normalizedMinY =
        (minY / _canvasSize.height)
            .clamp(0.0, 1.0);

    final normalizedMaxX =
        (maxX / _canvasSize.width)
            .clamp(0.0, 1.0);

    final normalizedMaxY =
        (maxY / _canvasSize.height)
            .clamp(0.0, 1.0);

    /*
      RegionShape.points에는
      bounding box 내부 기준 좌표를 저장한다.

      예:
      x = 0.2
      y = 0.3
      width = 0.4
      height = 0.2

      points는 이 박스 내부에서
      다시 0~1 값으로 정규화된다.
    */

    final regionWidth = math.max(
      normalizedMaxX - normalizedMinX,
      0.01,
    );

    final regionHeight = math.max(
      normalizedMaxY - normalizedMinY,
      0.01,
    );

    final points = allPoints.map(
      (point) {
        final screenX =
            point.dx / _canvasSize.width;

        final screenY =
            point.dy / _canvasSize.height;

        final localX =
            (screenX - normalizedMinX) /
                regionWidth;

        final localY =
            (screenY - normalizedMinY) /
                regionHeight;

        return Offset(
          localX.clamp(0.0, 1.0),
          localY.clamp(0.0, 1.0),
        );
      },
    ).toList();

    return RegionShape(
      type: RegionShapeType.custom,
      x: normalizedMinX,
      y: normalizedMinY,
      width: regionWidth,
      height: regionHeight,
      points: points,
    );
  }

  void _completeDrawing() {
    final region = _buildRegion();

    if (region == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            '먼저 잘 보이지 않는 영역을 그려주세요.',
          ),
        ),
      );

      return;
    }

    Navigator.pop(
      context,
      region,
    );
  }

  @override
  Widget build(BuildContext context) {
    const yellow = Color(0xFFF4D35E);

    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(),

            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(
                  24,
                  12,
                  24,
                  28,
                ),
                child: Column(
                  crossAxisAlignment:
                      CrossAxisAlignment.start,
                  children: [
                    const Text(
                      '잘 보이지 않는 영역을\n직접 그려주세요.',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 29,
                        fontWeight: FontWeight.w900,
                        height: 1.25,
                        letterSpacing: -0.7,
                      ),
                    ),

                    const SizedBox(height: 12),

                    Text(
                      '실제로 잘 보이지 않는 부분을 손가락으로 칠하듯 그려주세요.',
                      style: TextStyle(
                        color: Colors.white.withValues(
                          alpha: 0.65,
                        ),
                        fontSize: 15,
                        height: 1.5,
                      ),
                    ),

                    const SizedBox(height: 24),

                    AspectRatio(
                      aspectRatio: 0.78,
                      child: LayoutBuilder(
                        builder: (
                          context,
                          constraints,
                        ) {
                          _canvasSize = Size(
                            constraints.maxWidth,
                            constraints.maxHeight,
                          );

                          return GestureDetector(
                            behavior:
                                HitTestBehavior.opaque,
                            onPanStart: (details) {
                              _startGesture(
                                details.localPosition,
                              );
                            },
                            onPanUpdate: (details) {
                              _updateGesture(
                                details.localPosition,
                              );
                            },
                            onPanEnd: (_) {
                              _endGesture();
                            },
                            child: ClipRRect(
                              borderRadius:
                                  BorderRadius.circular(
                                24,
                              ),
                              child: CustomPaint(
                                painter: _RegionPainter(
                                  strokes: _strokes,
                                  currentStroke:
                                      _currentStroke,
                                  tool: _tool,
                                ),
                                foregroundPainter:
                                    _RegionPainter(
                                  strokes: _strokes,
                                  currentStroke:
                                      _currentStroke,
                                  tool: _tool,
                                ),
                                child: Container(
                                  decoration:
                                      const BoxDecoration(
                                    color: Color(
                                      0xFFF4F4F4,
                                    ),
                                  ),
                                  child: const Stack(
                                    children: [
                                      Positioned(
                                        left: 22,
                                        right: 22,
                                        top: 28,
                                        child: Column(
                                          crossAxisAlignment:
                                              CrossAxisAlignment
                                                  .start,
                                          children: [
                                            Text(
                                              '문서 미리보기',
                                              style:
                                                  TextStyle(
                                                color:
                                                    Colors.black,
                                                fontSize: 21,
                                                fontWeight:
                                                    FontWeight
                                                        .w900,
                                              ),
                                            ),
                                            SizedBox(
                                              height: 20,
                                            ),
                                            _PreviewLine(
                                              widthFactor: 1,
                                            ),
                                            SizedBox(
                                              height: 10,
                                            ),
                                            _PreviewLine(
                                              widthFactor:
                                                  0.82,
                                            ),
                                            SizedBox(
                                              height: 10,
                                            ),
                                            _PreviewLine(
                                              widthFactor:
                                                  0.94,
                                            ),
                                            SizedBox(
                                              height: 10,
                                            ),
                                            _PreviewLine(
                                              widthFactor:
                                                  0.72,
                                            ),
                                            SizedBox(
                                              height: 28,
                                            ),
                                            _PreviewLine(
                                              widthFactor:
                                                  0.88,
                                            ),
                                            SizedBox(
                                              height: 10,
                                            ),
                                            _PreviewLine(
                                              widthFactor:
                                                  1,
                                            ),
                                          ],
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                            ),
                          );
                        },
                      ),
                    ),

                    const SizedBox(height: 18),

                    Row(
                      children: [
                        Expanded(
                          child: _ToolButton(
                            icon: Icons.draw_rounded,
                            text: '그리기',
                            selected:
                                _tool ==
                                    _DrawTool.draw,
                            onTap: () {
                              _selectTool(
                                _DrawTool.draw,
                              );
                            },
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: _ToolButton(
                            icon:
                                Icons.auto_fix_off_rounded,
                            text: '지우기',
                            selected:
                                _tool ==
                                    _DrawTool.erase,
                            onTap: () {
                              _selectTool(
                                _DrawTool.erase,
                              );
                            },
                          ),
                        ),
                      ],
                    ),

                    const SizedBox(height: 10),

                    Row(
                      children: [
                        Expanded(
                          child: _ToolButton(
                            icon: Icons.undo_rounded,
                            text: '실행 취소',
                            selected: false,
                            onTap: _undo,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: _ToolButton(
                            icon:
                                Icons.delete_outline_rounded,
                            text: '전체 삭제',
                            selected: false,
                            onTap: _clear,
                          ),
                        ),
                      ],
                    ),

                    const SizedBox(height: 20),

                    Container(
                      width: double.infinity,
                      padding:
                          const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color:
                            const Color(0xFF111111),
                        borderRadius:
                            BorderRadius.circular(18),
                        border: Border.all(
                          color: Colors.white
                              .withValues(
                            alpha: 0.08,
                          ),
                        ),
                      ),
                      child: Row(
                        crossAxisAlignment:
                            CrossAxisAlignment.start,
                        children: [
                          const Icon(
                            Icons.info_outline_rounded,
                            color: yellow,
                            size: 23,
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Text(
                              '원형이나 타원형으로 표현하기 어려운 시야 영역을 자유롭게 지정할 수 있습니다.',
                              style: TextStyle(
                                color: Colors.white
                                    .withValues(
                                  alpha: 0.75,
                                ),
                                fontSize: 14,
                                height: 1.45,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),

            Container(
              padding: const EdgeInsets.fromLTRB(
                24,
                12,
                24,
                22,
              ),
              decoration: BoxDecoration(
                color: Colors.black,
                border: Border(
                  top: BorderSide(
                    color: Colors.white.withValues(
                      alpha: 0.07,
                    ),
                  ),
                ),
              ),
              child: HaloPrimaryButton(
                text: '이 모양 사용하기',
                icon: Icons.check_rounded,
                onPressed:
                    _hasDrawing
                        ? _completeDrawing
                        : null,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        18,
        14,
        18,
        8,
      ),
      child: Row(
        children: [
          IconButton(
            onPressed: () {
              Navigator.pop(context);
            },
            tooltip: '뒤로가기',
            icon: const Icon(
              Icons.arrow_back_rounded,
              color: Colors.white,
              size: 28,
            ),
          ),
          const SizedBox(width: 4),
          const Expanded(
            child: Text(
              '영역 직접 그리기',
              style: TextStyle(
                color: Colors.white,
                fontSize: 21,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _RegionPainter
    extends CustomPainter {
  final List<List<Offset>> strokes;
  final List<Offset> currentStroke;
  final _DrawTool tool;

  const _RegionPainter({
    required this.strokes,
    required this.currentStroke,
    required this.tool,
  });

  @override
  void paint(
    Canvas canvas,
    Size size,
  ) {
    final mainPaint = Paint()
      ..color = const Color(
        0xFFF4D35E,
      ).withValues(alpha: 0.62)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 36
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    final borderPaint = Paint()
      ..color = const Color(
        0xFFFFE681,
      )
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    for (final stroke in strokes) {
      _drawStroke(
        canvas,
        stroke,
        mainPaint,
      );

      _drawStroke(
        canvas,
        stroke,
        borderPaint,
      );
    }

    if (tool == _DrawTool.draw) {
      _drawStroke(
        canvas,
        currentStroke,
        mainPaint,
      );

      _drawStroke(
        canvas,
        currentStroke,
        borderPaint,
      );
    }
  }

  void _drawStroke(
    Canvas canvas,
    List<Offset> points,
    Paint paint,
  ) {
    if (points.isEmpty) {
      return;
    }

    if (points.length == 1) {
      canvas.drawCircle(
        points.first,
        paint.strokeWidth / 2,
        paint,
      );

      return;
    }

    final path = Path()
      ..moveTo(
        points.first.dx,
        points.first.dy,
      );

    for (int i = 1;
        i < points.length;
        i++) {
      path.lineTo(
        points[i].dx,
        points[i].dy,
      );
    }

    canvas.drawPath(
      path,
      paint,
    );
  }

  @override
  bool shouldRepaint(
    covariant _RegionPainter oldDelegate,
  ) {
    return true;
  }
}

class _ToolButton
    extends StatelessWidget {
  final IconData icon;
  final String text;
  final bool selected;
  final VoidCallback onTap;

  const _ToolButton({
    required this.icon,
    required this.text,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    const yellow = Color(0xFFF4D35E);

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius:
            BorderRadius.circular(14),
        child: Container(
          padding:
              const EdgeInsets.symmetric(
            vertical: 14,
            horizontal: 8,
          ),
          decoration: BoxDecoration(
            color: selected
                ? yellow.withValues(
                    alpha: 0.14,
                  )
                : const Color(
                    0xFF111111,
                  ),
            borderRadius:
                BorderRadius.circular(14),
            border: Border.all(
              color: selected
                  ? yellow
                  : Colors.white
                      .withValues(
                    alpha: 0.1,
                  ),
              width: selected ? 2 : 1,
            ),
          ),
          child: Row(
            mainAxisAlignment:
                MainAxisAlignment.center,
            children: [
              Icon(
                icon,
                color: selected
                    ? yellow
                    : Colors.white70,
                size: 22,
              ),
              const SizedBox(width: 7),
              Text(
                text,
                style: TextStyle(
                  color: selected
                      ? yellow
                      : Colors.white,
                  fontSize: 13,
                  fontWeight:
                      FontWeight.w700,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _PreviewLine
    extends StatelessWidget {
  final double widthFactor;

  const _PreviewLine({
    required this.widthFactor,
  });

  @override
  Widget build(BuildContext context) {
    return FractionallySizedBox(
      widthFactor: widthFactor,
      child: Container(
        height: 11,
        decoration: BoxDecoration(
          color: Colors.black
              .withValues(
            alpha: 0.18,
          ),
          borderRadius:
              BorderRadius.circular(6),
        ),
      ),
    );
  }
}