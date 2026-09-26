import 'package:flutter/material.dart';

import '../models/vision_profile.dart';
import '../widgets/halo_primary_button.dart';

class PeripheralRegionEditorScreen extends StatefulWidget {
  final RegionShape? initialRegion;

  const PeripheralRegionEditorScreen({
    super.key,
    this.initialRegion,
  });

  @override
  State<PeripheralRegionEditorScreen> createState() =>
      _PeripheralRegionEditorScreenState();
}

class _PeripheralRegionEditorScreenState
    extends State<PeripheralRegionEditorScreen> {
  double _centerX = 0.5;
  double _centerY = 0.46;

  double _regionSize = 0.44;
  double _startRegionSize = 0.44;

  bool _isEllipse = true;

  @override
  void initState() {
    super.initState();

    final initial = widget.initialRegion;

    if (initial == null) {
      return;
    }

    _isEllipse =
        initial.type == RegionShapeType.ellipse;

    _regionSize = initial.width;

    _centerX =
        initial.x + initial.width / 2;

    _centerY =
        initial.y + initial.height / 2;
  }

  void _selectCircle() {
    setState(() {
      _isEllipse = false;
    });
  }

  void _selectEllipse() {
    setState(() {
      _isEllipse = true;
    });
  }

  void _reset() {
    setState(() {
      _centerX = 0.5;
      _centerY = 0.46;

      _regionSize = 0.44;
      _startRegionSize = 0.44;

      _isEllipse = true;
    });
  }

  RegionShape _buildRegion() {
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
    const green = Color(0xFF63C174);

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
                crossAxisAlignment:
                    CrossAxisAlignment.start,
                children: [
                  const Icon(
                    Icons.gesture_rounded,
                    color: green,
                    size: 21,
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      '화면 어디에서든 한 손가락으로 영역을 이동하고, 두 손가락으로 크기를 조절하세요.',
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
                padding: const EdgeInsets.symmetric(
                  horizontal: 18,
                ),
                child: _buildEditorCanvas(),
              ),
            ),

            const SizedBox(height: 14),

            Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: 20,
              ),
              child: Row(
                children: [
                  Expanded(
                    child: _ShapeButton(
                      text: '원형',
                      icon: Icons.circle_outlined,
                      selected: !_isEllipse,
                      onTap: _selectCircle,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: _ShapeButton(
                      text: '타원형',
                      icon: Icons
                          .panorama_fish_eye_rounded,
                      selected: _isEllipse,
                      onTap: _selectEllipse,
                    ),
                  ),
                ],
              ),
            ),

            Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: 20,
              ),
              child: Align(
                alignment: Alignment.centerRight,
                child: TextButton.icon(
                  onPressed: _reset,
                  icon: const Icon(
                    Icons.restart_alt_rounded,
                  ),
                  label: const Text(
                    '초기화',
                  ),
                  style: TextButton.styleFrom(
                    foregroundColor:
                        Colors.white.withValues(
                      alpha: 0.65,
                    ),
                  ),
                ),
              ),
            ),

            Padding(
              padding: const EdgeInsets.fromLTRB(
                20,
                6,
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
              '잘 보이는 영역 편집',
              style: TextStyle(
                color: Colors.white,
                fontSize: 20,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          Text(
            '${(_regionSize * 100).round()}%',
            style: const TextStyle(
              color: Color(0xFF63C174),
              fontSize: 15,
              fontWeight: FontWeight.w800,
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
          behavior: HitTestBehavior.opaque,

          onScaleStart: (_) {
            _startRegionSize =
                _regionSize;
          },

          onScaleUpdate: (details) {
            setState(() {
              if (details.pointerCount == 1) {
                _centerX +=
                    details.focalPointDelta.dx /
                        width;

                _centerY +=
                    details.focalPointDelta.dy /
                        height;

                final visibleWidth =
                    _regionSize;

                final visibleHeight =
                    _isEllipse
                        ? (_regionSize *
                                width *
                                0.72) /
                            height
                        : (_regionSize *
                                width) /
                            height;

                _centerX = _centerX.clamp(
                  visibleWidth / 2,
                  1 - visibleWidth / 2,
                );

                _centerY = _centerY.clamp(
                  visibleHeight / 2,
                  1 - visibleHeight / 2,
                );
              }

              if (details.pointerCount >= 2) {
                _regionSize =
                    (_startRegionSize *
                            details.scale)
                        .clamp(
                  0.18,
                  0.90,
                );
              }
            });
          },

          child: Container(
            width: double.infinity,
            height: double.infinity,
            decoration: BoxDecoration(
              color: const Color(
                0xFF202020,
              ),
              borderRadius:
                  BorderRadius.circular(24),
              border: Border.all(
                color: Colors.white.withValues(
                  alpha: 0.12,
                ),
              ),
            ),
            clipBehavior: Clip.antiAlias,
            child: Stack(
              children: [
                const Positioned(
                  left: 22,
                  right: 22,
                  top: 24,
                  child: Column(
                    crossAxisAlignment:
                        CrossAxisAlignment.start,
                    children: [
                      Text(
                        '문서 미리보기',
                        style: TextStyle(
                          color: Colors.white70,
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
                        widthFactor: 0.82,
                      ),
                      SizedBox(height: 11),
                      _PreviewLine(
                        widthFactor: 0.93,
                      ),
                      SizedBox(height: 11),
                      _PreviewLine(
                        widthFactor: 0.66,
                      ),
                      SizedBox(height: 32),
                      _PreviewLine(
                        widthFactor: 0.88,
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
                      color: Colors.white
                          .withValues(
                        alpha: 0.06,
                      ),
                      borderRadius:
                          BorderRadius.circular(
                        15,
                      ),
                    ),
                    child: const Center(
                      child: Icon(
                        Icons.image_outlined,
                        color: Colors.white38,
                        size: 42,
                      ),
                    ),
                  ),
                ),

                _buildVisibleRegion(
                  width,
                  height,
                ),

                Positioned(
                  left: 12,
                  bottom: 12,
                  child: Container(
                    padding:
                        const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 7,
                    ),
                    decoration: BoxDecoration(
                      color: Colors.black.withValues(
                        alpha: 0.76,
                      ),
                      borderRadius:
                          BorderRadius.circular(
                        10,
                      ),
                    ),
                    child: const Text(
                      '밝은 영역 = 잘 보이는 영역',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 11,
                        fontWeight:
                            FontWeight.w700,
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

  Widget _buildVisibleRegion(
    double width,
    double height,
  ) {
    const green =
        Color(0xFF63C174);

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
            color: const Color(
              0xFFF4F4F4,
            ),
            border: Border.all(
              color: green,
              width: 4,
            ),
            boxShadow: [
              BoxShadow(
                color: green.withValues(
                  alpha: 0.22,
                ),
                blurRadius: 20,
                spreadRadius: 3,
              ),
            ],
          ),
          child: const Stack(
            children: [
              Positioned(
                left: 16,
                right: 16,
                top: 18,
                child: Column(
                  crossAxisAlignment:
                      CrossAxisAlignment.start,
                  children: [
                    _VisibleLine(
                      widthFactor: 1,
                    ),
                    SizedBox(height: 9),
                    _VisibleLine(
                      widthFactor: 0.80,
                    ),
                    SizedBox(height: 9),
                    _VisibleLine(
                      widthFactor: 0.92,
                    ),
                  ],
                ),
              ),
              Center(
                child: Icon(
                  Icons
                      .center_focus_strong_rounded,
                  color: green,
                  size: 34,
                ),
              ),
            ],
          ),
        ),
      ),
    );
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
    const green =
        Color(0xFF63C174);

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
          decoration: BoxDecoration(
            color: selected
                ? green.withValues(
                    alpha: 0.14,
                  )
                : const Color(
                    0xFF111111,
                  ),
            borderRadius:
                BorderRadius.circular(14),
            border: Border.all(
              color: selected
                  ? green
                  : Colors.white.withValues(
                      alpha: 0.10,
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
                    ? green
                    : Colors.white70,
                size: 21,
              ),
              const SizedBox(width: 7),
              Text(
                text,
                style: TextStyle(
                  color: selected
                      ? green
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
          color: Colors.white.withValues(
            alpha: 0.12,
          ),
          borderRadius:
              BorderRadius.circular(6),
        ),
      ),
    );
  }
}

class _VisibleLine
    extends StatelessWidget {
  final double widthFactor;

  const _VisibleLine({
    required this.widthFactor,
  });

  @override
  Widget build(BuildContext context) {
    return FractionallySizedBox(
      widthFactor: widthFactor,
      child: Container(
        height: 8,
        decoration: BoxDecoration(
          color: Colors.black.withValues(
            alpha: 0.17,
          ),
          borderRadius:
              BorderRadius.circular(5),
        ),
      ),
    );
  }
}