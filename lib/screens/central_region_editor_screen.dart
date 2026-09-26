import 'package:flutter/material.dart';

import '../models/vision_profile.dart';
import '../widgets/halo_primary_button.dart';
import 'custom_region_draw_screen.dart';

class CentralRegionEditorScreen extends StatefulWidget {
  final RegionShape? initialRegion;

  const CentralRegionEditorScreen({
    super.key,
    this.initialRegion,
  });

  @override
  State<CentralRegionEditorScreen> createState() =>
      _CentralRegionEditorScreenState();
}

class _CentralRegionEditorScreenState
    extends State<CentralRegionEditorScreen> {
  double _centerX = 0.5;
  double _centerY = 0.5;

  double _regionSize = 0.32;
  double _startRegionSize = 0.32;

  bool _isEllipse = false;

  RegionShape? _customRegion;

  bool get _isCustomMode => _customRegion != null;

  @override
  void initState() {
    super.initState();

    final initialRegion = widget.initialRegion;

    if (initialRegion == null) {
      return;
    }

    if (initialRegion.type == RegionShapeType.custom) {
      _customRegion = initialRegion;
      return;
    }

    _isEllipse =
        initialRegion.type == RegionShapeType.ellipse;

    _regionSize = initialRegion.width;

    _centerX =
        initialRegion.x + initialRegion.width / 2;

    _centerY =
        initialRegion.y + initialRegion.height / 2;
  }

  void _reset() {
    setState(() {
      _centerX = 0.5;
      _centerY = 0.5;

      _regionSize = 0.32;
      _startRegionSize = 0.32;

      _isEllipse = false;
      _customRegion = null;
    });
  }

  void _selectCircle() {
    setState(() {
      _customRegion = null;
      _isEllipse = false;
    });
  }

  void _selectEllipse() {
    setState(() {
      _customRegion = null;
      _isEllipse = true;
    });
  }

  Future<void> _openCustomDraw() async {
    final region =
        await Navigator.push<RegionShape>(
      context,
      MaterialPageRoute(
        builder: (_) =>
            const CustomRegionDrawScreen(),
      ),
    );

    if (!mounted || region == null) {
      return;
    }

    setState(() {
      _customRegion = region;
    });
  }

  RegionShape _buildRegion() {
    if (_customRegion != null) {
      return _customRegion!;
    }

    final width = _regionSize;

    final height = _isEllipse
        ? _regionSize * 0.72
        : _regionSize;

    final left =
        (_centerX - width / 2).clamp(
      0.0,
      1.0 - width,
    );

    final top =
        (_centerY - height / 2).clamp(
      0.0,
      1.0 - height,
    );

    return RegionShape(
      type: _isEllipse
          ? RegionShapeType.ellipse
          : RegionShapeType.circle,
      x: left,
      y: top,
      width: width,
      height: height,
    );
  }

  void _complete() {
    Navigator.pop(
      context,
      _buildRegion(),
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

            Padding(
              padding: const EdgeInsets.fromLTRB(
                22,
                6,
                22,
                14,
              ),
              child: Row(
                children: [
                  const Icon(
                    Icons.gesture_rounded,
                    color: yellow,
                    size: 21,
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      _isCustomMode
                          ? '직접 그린 영역이 적용되어 있습니다.'
                          : '화면을 드래그해 이동하고 두 손가락으로 크기를 조절하세요.',
                      style: TextStyle(
                        color: Colors.white.withValues(
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
                    const EdgeInsets.symmetric(
                  horizontal: 18,
                ),
                child: _buildEditorCanvas(),
              ),
            ),

            const SizedBox(height: 14),

            Padding(
              padding:
                  const EdgeInsets.symmetric(
                horizontal: 20,
              ),
              child: Row(
                children: [
                  Expanded(
                    child: _ShapeButton(
                      text: '원형',
                      icon:
                          Icons.circle_outlined,
                      selected:
                          !_isCustomMode &&
                              !_isEllipse,
                      onTap: _selectCircle,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: _ShapeButton(
                      text: '타원형',
                      icon: Icons
                          .panorama_fish_eye_rounded,
                      selected:
                          !_isCustomMode &&
                              _isEllipse,
                      onTap: _selectEllipse,
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 10),

            Padding(
              padding:
                  const EdgeInsets.symmetric(
                horizontal: 20,
              ),
              child: SizedBox(
                width: double.infinity,
                child: OutlinedButton.icon(
                  onPressed: _openCustomDraw,
                  icon: const Icon(
                    Icons.draw_rounded,
                  ),
                  label: Text(
                    _isCustomMode
                        ? '직접 그린 모양 다시 설정'
                        : '모양 직접 그리기',
                  ),
                  style:
                      OutlinedButton.styleFrom(
                    foregroundColor: yellow,
                    side: BorderSide(
                      color: yellow.withValues(
                        alpha: 0.45,
                      ),
                    ),
                    padding:
                        const EdgeInsets.symmetric(
                      vertical: 14,
                    ),
                    shape:
                        RoundedRectangleBorder(
                      borderRadius:
                          BorderRadius.circular(
                        16,
                      ),
                    ),
                  ),
                ),
              ),
            ),

            Padding(
              padding:
                  const EdgeInsets.symmetric(
                horizontal: 20,
              ),
              child: Align(
                alignment:
                    Alignment.centerRight,
                child: TextButton.icon(
                  onPressed: _reset,
                  icon: const Icon(
                    Icons.restart_alt_rounded,
                  ),
                  label: const Text(
                    '초기화',
                  ),
                  style:
                      TextButton.styleFrom(
                    foregroundColor:
                        Colors.white.withValues(
                      alpha: 0.65,
                    ),
                  ),
                ),
              ),
            ),

            Container(
              padding:
                  const EdgeInsets.fromLTRB(
                20,
                8,
                20,
                20,
              ),
              child: HaloPrimaryButton(
                text: '편집 완료',
                icon: Icons.check_rounded,
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
      padding: const EdgeInsets.fromLTRB(
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
              Icons.arrow_back_rounded,
              color: Colors.white,
              size: 28,
            ),
          ),
          const SizedBox(width: 4),
          const Expanded(
            child: Text(
              '시야 영역 편집',
              style: TextStyle(
                color: Colors.white,
                fontSize: 20,
                fontWeight:
                    FontWeight.w800,
              ),
            ),
          ),
          if (!_isCustomMode)
            Text(
              '${(_regionSize * 100).round()}%',
              style: const TextStyle(
                color: Color(
                  0xFFF4D35E,
                ),
                fontSize: 15,
                fontWeight:
                    FontWeight.w800,
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildEditorCanvas() {
    return LayoutBuilder(
      builder: (
        context,
        constraints,
      ) {
        final width =
            constraints.maxWidth;

        final height =
            constraints.maxHeight;

        return GestureDetector(
          behavior:
              HitTestBehavior.opaque,

          onScaleStart: (_) {
            if (_isCustomMode) {
              return;
            }

            _startRegionSize =
                _regionSize;
          },

          onScaleUpdate: (details) {
            if (_isCustomMode) {
              return;
            }

            setState(() {
              if (details.pointerCount == 1) {
                _centerX +=
                    details
                            .focalPointDelta
                            .dx /
                        width;

                _centerY +=
                    details
                            .focalPointDelta
                            .dy /
                        height;

                final regionWidth =
                    _regionSize;

                final regionHeight =
                    _isEllipse
                        ? (_regionSize *
                                width *
                                0.72) /
                            height
                        : (_regionSize *
                                width) /
                            height;

                _centerX =
                    _centerX.clamp(
                  regionWidth / 2,
                  1 - regionWidth / 2,
                );

                _centerY =
                    _centerY.clamp(
                  regionHeight / 2,
                  1 - regionHeight / 2,
                );
              }

              if (details.pointerCount >=
                  2) {
                _regionSize =
                    (_startRegionSize *
                            details.scale)
                        .clamp(
                  0.12,
                  0.75,
                );
              }
            });
          },

          child: Container(
            width: double.infinity,
            height: double.infinity,
            decoration: BoxDecoration(
              color:
                  const Color(
                0xFFF4F4F4,
              ),
              borderRadius:
                  BorderRadius.circular(
                24,
              ),
              border: Border.all(
                color:
                    Colors.white.withValues(
                  alpha: 0.12,
                ),
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
                              FontWeight.w900,
                        ),
                      ),
                      SizedBox(height: 20),
                      _PreviewLine(
                        widthFactor: 1,
                      ),
                      SizedBox(height: 11),
                      _PreviewLine(
                        widthFactor:
                            0.82,
                      ),
                      SizedBox(height: 11),
                      _PreviewLine(
                        widthFactor:
                            0.93,
                      ),
                      SizedBox(height: 11),
                      _PreviewLine(
                        widthFactor:
                            0.66,
                      ),
                      SizedBox(height: 32),
                      _PreviewLine(
                        widthFactor:
                            0.88,
                      ),
                      SizedBox(height: 11),
                      _PreviewLine(
                        widthFactor: 1,
                      ),
                    ],
                  ),
                ),

                Positioned(
                  left: 22,
                  right: 22,
                  bottom: 28,
                  child: Container(
                    height: 105,
                    decoration:
                        BoxDecoration(
                      color: Colors.black
                          .withValues(
                        alpha: 0.07,
                      ),
                      borderRadius:
                          BorderRadius.circular(
                        15,
                      ),
                    ),
                    child:
                        const Center(
                      child: Icon(
                        Icons
                            .image_outlined,
                        color:
                            Colors.black38,
                        size: 42,
                      ),
                    ),
                  ),
                ),

                if (_customRegion != null)
                  _buildCustomRegion(
                    width,
                    height,
                  )
                else
                  _buildStandardRegion(
                    width,
                    height,
                  ),

                if (!_isCustomMode)
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
                      child:
                          const Text(
                        '화면 전체에서 드래그 · 핀치 가능',
                        style:
                            TextStyle(
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
          ),
        );
      },
    );
  }

  Widget _buildStandardRegion(
    double width,
    double height,
  ) {
    const yellow =
        Color(0xFFF4D35E);

    final regionWidth =
        width * _regionSize;

    final regionHeight =
        _isEllipse
            ? regionWidth * 0.72
            : regionWidth;

    final centerX =
        width * _centerX;

    final centerY =
        height * _centerY;

    return Positioned(
      left:
          centerX - regionWidth / 2,
      top:
          centerY - regionHeight / 2,
      width: regionWidth,
      height: regionHeight,
      child: IgnorePointer(
        child: Container(
          decoration: BoxDecoration(
            shape: _isEllipse
                ? BoxShape.rectangle
                : BoxShape.circle,
            borderRadius: _isEllipse
                ? BorderRadius.all(
                    Radius.elliptical(
                      regionWidth,
                      regionHeight,
                    ),
                  )
                : null,
            color: yellow.withValues(
              alpha: 0.68,
            ),
            border: Border.all(
              color: const Color(
                0xFFFFE681,
              ),
              width: 3,
            ),
            boxShadow: [
              BoxShadow(
                color:
                    Colors.black.withValues(
                  alpha: 0.18,
                ),
                blurRadius: 12,
              ),
            ],
          ),
          child: const Center(
            child: Icon(
              Icons.open_with_rounded,
              color:
                  Colors.black87,
              size: 32,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildCustomRegion(
    double width,
    double height,
  ) {
    final region =
        _customRegion!;

    return Positioned(
      left: region.x * width,
      top: region.y * height,
      width:
          region.width * width,
      height:
          region.height * height,
      child: IgnorePointer(
        child: CustomPaint(
          painter:
              _CustomPreviewPainter(
            points: region.points,
          ),
        ),
      ),
    );
  }
}

class _CustomPreviewPainter
    extends CustomPainter {
  final List<Offset> points;

  const _CustomPreviewPainter({
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

    final mainPaint = Paint()
      ..color =
          const Color(
        0xFFF4D35E,
      ).withValues(
        alpha: 0.68,
      )
      ..style =
          PaintingStyle.stroke
      ..strokeWidth = 26
      ..strokeCap =
          StrokeCap.round
      ..strokeJoin =
          StrokeJoin.round;

    final borderPaint = Paint()
      ..color =
          const Color(
        0xFFFFE681,
      )
      ..style =
          PaintingStyle.stroke
      ..strokeWidth = 3
      ..strokeCap =
          StrokeCap.round
      ..strokeJoin =
          StrokeJoin.round;

    final path = Path();

    for (int i = 0;
        i < points.length;
        i++) {
      final point = Offset(
        points[i].dx * size.width,
        points[i].dy * size.height,
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
      mainPaint,
    );

    canvas.drawPath(
      path,
      borderPaint,
    );
  }

  @override
  bool shouldRepaint(
    covariant _CustomPreviewPainter
        oldDelegate,
  ) {
    return true;
  }
}

class _ShapeButton
    extends StatelessWidget {
  final String text;
  final IconData icon;
  final bool selected;
  final VoidCallback onTap;

  const _ShapeButton({
    required this.text,
    required this.icon,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    const yellow =
        Color(0xFFF4D35E);

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius:
            BorderRadius.circular(14),
        child: Container(
          padding:
              const EdgeInsets.symmetric(
            vertical: 13,
          ),
          decoration:
              BoxDecoration(
            color: selected
                ? yellow.withValues(
                    alpha: 0.14,
                  )
                : const Color(
                    0xFF111111,
                  ),
            borderRadius:
                BorderRadius.circular(
              14,
            ),
            border: Border.all(
              color: selected
                  ? yellow
                  : Colors.white
                      .withValues(
                    alpha: 0.1,
                  ),
              width:
                  selected ? 2 : 1,
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
                size: 21,
              ),
              const SizedBox(width: 7),
              Text(
                text,
                style: TextStyle(
                  color: selected
                      ? yellow
                      : Colors.white,
                  fontSize: 14,
                  fontWeight:
                      FontWeight.w800,
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