import 'package:flutter/material.dart';

import '../models/vision_profile.dart';
import '../widgets/halo_primary_button.dart';

class LayoutEditorResult {
  final LayoutRegion textRegion;
  final LayoutRegion mediaRegion;

  const LayoutEditorResult({
    required this.textRegion,
    required this.mediaRegion,
  });
}

class LayoutEditorScreen extends StatefulWidget {
  final VisionProfile profile;

  const LayoutEditorScreen({
    super.key,
    required this.profile,
  });

  @override
  State<LayoutEditorScreen> createState() =>
      _LayoutEditorScreenState();
}

class _LayoutEditorScreenState
    extends State<LayoutEditorScreen> {
  double _textX = 0.08;
  double _textY = 0.62;
  double _textWidth = 0.84;
  double _textHeight = 0.20;

  double _mediaX = 0.58;
  double _mediaY = 0.12;
  double _mediaWidth = 0.32;
  double _mediaHeight = 0.24;

  double _textStartWidth = 0.84;
  double _textStartHeight = 0.20;

  double _mediaStartWidth = 0.32;
  double _mediaStartHeight = 0.24;

  String? _selectedBox;

  bool _textHorizontal = true;

  @override
  void initState() {
    super.initState();

    final text = widget.profile.textRegion;
    final media = widget.profile.mediaRegion;

    if (text != null) {
      _textX = text.x;
      _textY = text.y;
      _textWidth = text.width;
      _textHeight = text.height;

      _textHorizontal =
          text.width >= text.height;
    }

    if (media != null) {
      _mediaX = media.x;
      _mediaY = media.y;
      _mediaWidth = media.width;
      _mediaHeight = media.height;
    }
  }

  void _resetLayout() {
    setState(() {
      _textX = 0.08;
      _textY = 0.62;
      _textWidth = 0.84;
      _textHeight = 0.20;

      _mediaX = 0.58;
      _mediaY = 0.12;
      _mediaWidth = 0.32;
      _mediaHeight = 0.24;

      _textHorizontal = true;
      _selectedBox = null;
    });
  }

  bool _hasPlacementWarning(
  Rect boxRect,
  double previewWidth,
  double previewHeight,
) {
  final visionType = widget.profile.visionType;

  // 1. 중심부 시야
  // 회피 영역과 겹치면 경고
  if (visionType == VisionType.centralLoss) {
    return _overlapsAvoidRegion(
      boxRect,
      previewWidth,
      previewHeight,
    );
  }

  // 2. 주변부 시야
  // 잘 보이는 영역 안에 충분히 들어오지 않으면 경고
  if (visionType == VisionType.peripheralLoss) {
    return !_isInsideVisibleRegion(
      boxRect,
      previewWidth,
      previewHeight,
    );
  }

  // 3. 직접 설정
  if (visionType == VisionType.custom) {
    // 사용자가 "잘 보이지 않는 영역 피하기"를 선택한 경우
    if (widget.profile.avoidRegions.isNotEmpty) {
      return _overlapsAvoidRegion(
        boxRect,
        previewWidth,
        previewHeight,
      );
    }

    // 사용자가 "잘 보이는 영역에 모아 보기"를 선택한 경우
    if (widget.profile.visibleRegions.isNotEmpty) {
      return !_isInsideVisibleRegion(
        boxRect,
        previewWidth,
        previewHeight,
      );
    }
  }

  return false;
}

bool _overlapsAvoidRegion(
  Rect boxRect,
  double previewWidth,
  double previewHeight,
) {
  for (final region in widget.profile.avoidRegions) {
    final avoidRect = Rect.fromLTWH(
      region.x * previewWidth,
      region.y * previewHeight,
      region.width * previewWidth,
      region.height * previewHeight,
    );

    if (boxRect.overlaps(avoidRect)) {
      return true;
    }
  }

  return false;
}

bool _isInsideVisibleRegion(
  Rect boxRect,
  double previewWidth,
  double previewHeight,
) {
  if (widget.profile.visibleRegions.isEmpty) {
    return true;
  }

  for (final region in widget.profile.visibleRegions) {
    final visibleRect = Rect.fromLTWH(
      region.x * previewWidth,
      region.y * previewHeight,
      region.width * previewWidth,
      region.height * previewHeight,
    );

    final intersection = boxRect.intersect(
      visibleRect,
    );

    if (intersection.isEmpty) {
      continue;
    }

    final boxArea =
        boxRect.width * boxRect.height;

    if (boxArea <= 0) {
      continue;
    }

    final insideArea =
        intersection.width *
        intersection.height;

    final insideRatio =
        insideArea / boxArea;

    // 상자의 75% 이상이 잘 보이는 영역 안에 있으면 정상
    if (insideRatio >= 0.75) {
      return true;
    }
  }

  return false;
}

  void _complete() {
    Navigator.pop(
      context,
      LayoutEditorResult(
        textRegion: LayoutRegion(
          x: _textX,
          y: _textY,
          width: _textWidth,
          height: _textHeight,
        ),
        mediaRegion: LayoutRegion(
          x: _mediaX,
          y: _mediaY,
          width: _mediaWidth,
          height: _mediaHeight,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    const yellow =
        Color(0xFFF4D35E);

    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(),

            Padding(
              padding:
                  const EdgeInsets.fromLTRB(
                22,
                4,
                22,
                12,
              ),
              child: Row(
                crossAxisAlignment:
                    CrossAxisAlignment.start,
                children: [
                  const Icon(
                    Icons
                        .touch_app_rounded,
                    color: yellow,
                    size: 21,
                  ),
                  const SizedBox(
                    width: 10,
                  ),
                  Expanded(
                    child: Text(
                      '텍스트 또는 그림·표 상자를 누른 뒤 이동하거나 두 손가락으로 크기를 조절하세요.',
                      style: TextStyle(
                        color: Colors.white
                            .withValues(
                          alpha: 0.72,
                        ),
                        fontSize: 13,
                        height: 1.4,
                      ),
                    ),
                  ),
                ],
              ),
            ),

            Expanded(
              child: Padding(
                padding:
                    const EdgeInsets
                        .symmetric(
                  horizontal: 18,
                ),
                child:
                    _buildEditor(),
              ),
            ),

            const SizedBox(
              height: 12,
            ),

            Padding(
              padding:
                  const EdgeInsets
                      .symmetric(
                horizontal: 20,
              ),
              child: SizedBox(
                width: double.infinity,
                child:
                    OutlinedButton.icon(
                  onPressed: () {
                    setState(() {
                      _textHorizontal =
                          !_textHorizontal;

                      if (_textHorizontal) {
                        _textWidth =
                            0.84;
                        _textHeight =
                            0.20;
                      } else {
                        _textWidth =
                            0.34;
                        _textHeight =
                            0.46;
                      }

                      _textX =
                          _textX.clamp(
                        0.0,
                        1.0 -
                            _textWidth,
                      );

                      _textY =
                          _textY.clamp(
                        0.0,
                        1.0 -
                            _textHeight,
                      );
                    });
                  },
                  icon: const Icon(
                    Icons
                        .swap_horiz_rounded,
                  ),
                  label: Text(
                    _textHorizontal
                        ? '텍스트 가로형'
                        : '텍스트 세로형',
                  ),
                  style:
                      OutlinedButton
                          .styleFrom(
                    foregroundColor:
                        yellow,
                    side: BorderSide(
                      color: yellow
                          .withValues(
                        alpha: 0.45,
                      ),
                    ),
                    padding:
                        const EdgeInsets
                            .symmetric(
                      vertical: 13,
                    ),
                    shape:
                        RoundedRectangleBorder(
                      borderRadius:
                          BorderRadius
                              .circular(
                        15,
                      ),
                    ),
                  ),
                ),
              ),
            ),

            Padding(
              padding:
                  const EdgeInsets
                      .symmetric(
                horizontal: 20,
              ),
              child: Align(
                alignment:
                    Alignment.centerRight,
                child: TextButton.icon(
                  onPressed:
                      _resetLayout,
                  icon: const Icon(
                    Icons
                        .restart_alt_rounded,
                  ),
                  label: const Text(
                    '기본 배치로 되돌리기',
                  ),
                  style:
                      TextButton.styleFrom(
                    foregroundColor:
                        Colors.white
                            .withValues(
                      alpha: 0.65,
                    ),
                  ),
                ),
              ),
            ),

            Padding(
              padding:
                  const EdgeInsets
                      .fromLTRB(
                20,
                4,
                20,
                20,
              ),
              child:
                  HaloPrimaryButton(
                text: '편집 완료',
                icon:
                    Icons.check_rounded,
                onPressed: _complete,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Padding(
      padding:
          const EdgeInsets.fromLTRB(
        12,
        10,
        18,
        4,
      ),
      child: Row(
        children: [
          IconButton(
            onPressed: () {
              Navigator.pop(context);
            },
            tooltip: '뒤로가기',
            icon: const Icon(
              Icons
                  .arrow_back_rounded,
              color: Colors.white,
              size: 28,
            ),
          ),
          const SizedBox(width: 4),
          const Expanded(
            child: Text(
              '정보 배치 편집',
              style: TextStyle(
                color: Colors.white,
                fontSize: 20,
                fontWeight:
                    FontWeight.w800,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildEditor() {
    return LayoutBuilder(
      builder: (
        context,
        constraints,
      ) {
        final width =
            constraints.maxWidth;

        final height =
            constraints.maxHeight;

        final textRect =
            Rect.fromLTWH(
          _textX * width,
          _textY * height,
          _textWidth * width,
          _textHeight * height,
        );

        final mediaRect =
            Rect.fromLTWH(
          _mediaX * width,
          _mediaY * height,
          _mediaWidth * width,
          _mediaHeight * height,
        );

        final textWarning =
            _hasPlacementWarning(
          textRect,
          width,
          height,
        );

        final mediaWarning =
            _hasPlacementWarning(
          mediaRect,
          width,
          height,
        );

        return Container(
          width: double.infinity,
          height: double.infinity,
          decoration: BoxDecoration(
            color: const Color(
              0xFFF4F4F4,
            ),
            borderRadius:
                BorderRadius.circular(
              24,
            ),
          ),
          clipBehavior:
              Clip.antiAlias,
          child: Stack(
            children: [
              const Positioned(
                left: 22,
                right: 22,
                top: 24,
                child: Column(
                  crossAxisAlignment:
                      CrossAxisAlignment
                          .start,
                  children: [
                    Text(
                      '문서 미리보기',
                      style: TextStyle(
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
                          0.93,
                    ),
                    SizedBox(
                      height: 10,
                    ),
                    _PreviewLine(
                      widthFactor:
                          0.68,
                    ),
                  ],
                ),
              ),

              _buildAvoidRegions(
                width,
                height,
              ),

              _buildVisibleRegions(
                width,
                height,
              ),

              _buildTextBox(
                width,
                height,
                textWarning,
              ),

              _buildMediaBox(
                width,
                height,
                mediaWarning,
              ),

              Positioned(
                left: 12,
                bottom: 12,
                child: Container(
                  padding:
                      const EdgeInsets
                          .symmetric(
                    horizontal: 10,
                    vertical: 7,
                  ),
                  decoration:
                      BoxDecoration(
                    color: Colors.black
                        .withValues(
                      alpha: 0.72,
                    ),
                    borderRadius:
                        BorderRadius
                            .circular(
                      10,
                    ),
                  ),
                  child: Text(
                    _selectedBox == null
                        ? '상자를 먼저 선택하세요'
                        : _selectedBox ==
                                'text'
                            ? '텍스트 상자 선택됨'
                            : '그림 · 표 상자 선택됨',
                    style:
                        const TextStyle(
                      color:
                          Colors.white,
                      fontSize: 11,
                      fontWeight:
                          FontWeight
                              .w700,
                    ),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildTextBox(
    double width,
    double height,
    bool warning,
  ) {
    const blue =
        Color(0xFF5AA9FF);

    return Positioned(
      left: _textX * width,
      top: _textY * height,
      width:
          _textWidth * width,
      height:
          _textHeight * height,
      child: GestureDetector(
        behavior:
            HitTestBehavior.opaque,

        onTap: () {
          setState(() {
            _selectedBox =
                'text';
          });
        },

        onScaleStart: (_) {
          setState(() {
            _selectedBox =
                'text';
          });

          _textStartWidth =
              _textWidth;

          _textStartHeight =
              _textHeight;
        },

        onScaleUpdate:
            (details) {
          setState(() {
            _selectedBox =
                'text';

            if (details
                    .pointerCount ==
                1) {
              _textX += details
                      .focalPointDelta
                      .dx /
                  width;

              _textY += details
                      .focalPointDelta
                      .dy /
                  height;

              _textX =
                  _textX.clamp(
                0.0,
                1.0 -
                    _textWidth,
              );

              _textY =
                  _textY.clamp(
                0.0,
                1.0 -
                    _textHeight,
              );
            }

            if (details
                    .pointerCount >=
                2) {
              _textWidth =
                  (_textStartWidth *
                          details
                              .scale)
                      .clamp(
                0.20,
                0.95,
              );

              _textHeight =
                  (_textStartHeight *
                          details
                              .scale)
                      .clamp(
                0.10,
                0.65,
              );

              _textX =
                  _textX.clamp(
                0.0,
                1.0 -
                    _textWidth,
              );

              _textY =
                  _textY.clamp(
                0.0,
                1.0 -
                    _textHeight,
              );
            }
          });
        },

        child: Container(
          padding:
              const EdgeInsets.all(
            10,
          ),
          decoration:
              BoxDecoration(
            color:
                blue.withValues(
              alpha: 0.18,
            ),
            borderRadius:
                BorderRadius
                    .circular(
              16,
            ),
            border:
                Border.all(
              color: warning
                  ? Colors
                      .redAccent
                  : _selectedBox ==
                          'text'
                      ? Colors
                          .blueAccent
                      : blue,
              width: warning ||
                      _selectedBox ==
                          'text'
                  ? 3
                  : 2,
            ),
          ),
          child: Stack(
            children: [
              const Center(
                child: Text(
                  '텍스트 영역\n가나다 ABC 123',
                  textAlign:
                      TextAlign
                          .center,
                  style:
                      TextStyle(
                    color:
                        Colors
                            .black87,
                    fontSize: 15,
                    fontWeight:
                        FontWeight
                            .w800,
                    height: 1.4,
                  ),
                ),
              ),

              Positioned(
                left: 0,
                top: 0,
                child:
                    _LabelChip(
                  text: '텍스트',
                  color: warning
                      ? Colors
                          .redAccent
                      : blue,
                ),
              ),

              if (warning)
                const Positioned(
                  right: 0,
                  top: 0,
                  child: Icon(
                    Icons
                        .warning_amber_rounded,
                    color: Colors
                        .redAccent,
                    size: 24,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildMediaBox(
    double width,
    double height,
    bool warning,
  ) {
    const green =
        Color(0xFF63C174);

    return Positioned(
      left: _mediaX * width,
      top: _mediaY * height,
      width:
          _mediaWidth * width,
      height:
          _mediaHeight * height,
      child: GestureDetector(
        behavior:
            HitTestBehavior.opaque,

        onTap: () {
          setState(() {
            _selectedBox =
                'media';
          });
        },

        onScaleStart: (_) {
          setState(() {
            _selectedBox =
                'media';
          });

          _mediaStartWidth =
              _mediaWidth;

          _mediaStartHeight =
              _mediaHeight;
        },

        onScaleUpdate:
            (details) {
          setState(() {
            _selectedBox =
                'media';

            if (details
                    .pointerCount ==
                1) {
              _mediaX += details
                      .focalPointDelta
                      .dx /
                  width;

              _mediaY += details
                      .focalPointDelta
                      .dy /
                  height;

              _mediaX =
                  _mediaX.clamp(
                0.0,
                1.0 -
                    _mediaWidth,
              );

              _mediaY =
                  _mediaY.clamp(
                0.0,
                1.0 -
                    _mediaHeight,
              );
            }

            if (details
                    .pointerCount >=
                2) {
              _mediaWidth =
                  (_mediaStartWidth *
                          details
                              .scale)
                      .clamp(
                0.16,
                0.85,
              );

              _mediaHeight =
                  (_mediaStartHeight *
                          details
                              .scale)
                      .clamp(
                0.12,
                0.65,
              );

              _mediaX =
                  _mediaX.clamp(
                0.0,
                1.0 -
                    _mediaWidth,
              );

              _mediaY =
                  _mediaY.clamp(
                0.0,
                1.0 -
                    _mediaHeight,
              );
            }
          });
        },

        child: Container(
          decoration:
              BoxDecoration(
            color:
                green.withValues(
              alpha: 0.18,
            ),
            borderRadius:
                BorderRadius
                    .circular(
              16,
            ),
            border:
                Border.all(
              color: warning
                  ? Colors
                      .redAccent
                  : _selectedBox ==
                          'media'
                      ? Colors.green
                      : green,
              width: warning ||
                      _selectedBox ==
                          'media'
                  ? 3
                  : 2,
            ),
          ),
          child: Stack(
            children: [
              const Center(
                child: Icon(
                  Icons
                      .image_outlined,
                  color:
                      Colors
                          .black54,
                  size: 42,
                ),
              ),

              Positioned(
                left: 8,
                top: 8,
                child:
                    _LabelChip(
                  text:
                      '그림 · 표',
                  color: warning
                      ? Colors
                          .redAccent
                      : green,
                ),
              ),

              if (warning)
                const Positioned(
                  right: 8,
                  top: 8,
                  child: Icon(
                    Icons
                        .warning_amber_rounded,
                    color: Colors
                        .redAccent,
                    size: 24,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildAvoidRegions(
    double width,
    double height,
  ) {
    if (widget
        .profile.avoidRegions.isEmpty) {
      return const SizedBox.shrink();
    }

    return Stack(
      children:
          widget.profile.avoidRegions.map(
        (region) {
          final regionWidth =
              region.width * width;

          final regionHeight =
              region.height * height;

          if (region.type ==
              RegionShapeType.custom) {
            return Positioned(
              left:
                  region.x * width,
              top:
                  region.y * height,
              width: regionWidth,
              height: regionHeight,
              child: IgnorePointer(
                child: CustomPaint(
                  painter:
                      _CustomRegionPainter(
                    points:
                        region.points,
                  ),
                ),
              ),
            );
          }

          return Positioned(
            left:
                region.x * width,
            top:
                region.y * height,
            width: regionWidth,
            height: regionHeight,
            child: IgnorePointer(
              child: Container(
                decoration:
                    BoxDecoration(
                  color: const Color(
                    0xFFF4D35E,
                  ).withValues(
                    alpha: 0.42,
                  ),
                  shape: region.type ==
                          RegionShapeType
                              .circle
                      ? BoxShape.circle
                      : BoxShape
                          .rectangle,
                  borderRadius:
                      region.type ==
                              RegionShapeType
                                  .ellipse
                          ? BorderRadius
                              .all(
                              Radius
                                  .elliptical(
                                regionWidth,
                                regionHeight,
                              ),
                            )
                          : null,
                  border:
                      Border.all(
                    color:
                        const Color(
                      0xFFFFE681,
                    ),
                    width: 2,
                  ),
                ),
              ),
            ),
          );
        },
      ).toList(),
    );
  }

  Widget _buildVisibleRegions(
    double width,
    double height,
  ) {
    if (widget.profile
        .visibleRegions.isEmpty) {
      return const SizedBox.shrink();
    }

    return Stack(
      children:
          widget.profile.visibleRegions.map(
        (region) {
          final regionWidth =
              region.width * width;

          final regionHeight =
              region.height * height;

          if (region.type ==
              RegionShapeType.custom) {
            return Positioned(
              left:
                  region.x * width,
              top:
                  region.y * height,
              width: regionWidth,
              height: regionHeight,
              child: IgnorePointer(
                child: CustomPaint(
                  painter:
                      _VisibleCustomRegionPainter(
                    points:
                        region.points,
                  ),
                ),
              ),
            );
          }

          return Positioned(
            left:
                region.x * width,
            top:
                region.y * height,
            width: regionWidth,
            height: regionHeight,
            child: IgnorePointer(
              child: Container(
                decoration:
                    BoxDecoration(
                  color: const Color(
                    0xFF63C174,
                  ).withValues(
                    alpha: 0.14,
                  ),
                  shape: region.type ==
                          RegionShapeType
                              .circle
                      ? BoxShape.circle
                      : BoxShape
                          .rectangle,
                  borderRadius:
                      region.type ==
                              RegionShapeType
                                  .ellipse
                          ? BorderRadius
                              .all(
                              Radius
                                  .elliptical(
                                regionWidth,
                                regionHeight,
                              ),
                            )
                          : null,
                  border:
                      Border.all(
                    color:
                        const Color(
                      0xFF63C174,
                    ),
                    width: 3,
                  ),
                ),
              ),
            ),
          );
        },
      ).toList(),
    );
  }
}

class _CustomRegionPainter
    extends CustomPainter {
  final List<Offset> points;

  const _CustomRegionPainter({
    required this.points,
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
      ..color = const Color(
        0xFFF4D35E,
      ).withValues(
        alpha: 0.46,
      )
      ..style =
          PaintingStyle.stroke
      ..strokeWidth = 24
      ..strokeCap =
          StrokeCap.round
      ..strokeJoin =
          StrokeJoin.round;

    final path = Path();

    for (int i = 0;
        i < points.length;
        i++) {
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
    covariant _CustomRegionPainter
        oldDelegate,
  ) {
    return true;
  }
}

class _VisibleCustomRegionPainter
    extends CustomPainter {
  final List<Offset> points;

  const _VisibleCustomRegionPainter({
    required this.points,
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
      ..color = const Color(
        0xFF63C174,
      ).withValues(
        alpha: 0.35,
      )
      ..style =
          PaintingStyle.stroke
      ..strokeWidth = 24
      ..strokeCap =
          StrokeCap.round
      ..strokeJoin =
          StrokeJoin.round;

    final path = Path();

    for (int i = 0;
        i < points.length;
        i++) {
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
    covariant _VisibleCustomRegionPainter
        oldDelegate,
  ) {
    return true;
  }
}

class _LabelChip
    extends StatelessWidget {
  final String text;
  final Color color;

  const _LabelChip({
    required this.text,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding:
          const EdgeInsets.symmetric(
        horizontal: 8,
        vertical: 5,
      ),
      decoration: BoxDecoration(
        color: color,
        borderRadius:
            BorderRadius.circular(
          9,
        ),
      ),
      child: Text(
        text,
        style: const TextStyle(
          color: Colors.white,
          fontSize: 11,
          fontWeight:
              FontWeight.w800,
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
        decoration:
            BoxDecoration(
          color:
              Colors.black.withValues(
            alpha: 0.18,
          ),
          borderRadius:
              BorderRadius.circular(
            6,
          ),
        ),
      ),
    );
  }
}