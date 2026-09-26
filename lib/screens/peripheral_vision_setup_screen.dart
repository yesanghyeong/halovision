import 'package:flutter/material.dart';

import '../models/vision_profile.dart';
import '../widgets/halo_primary_button.dart';
import 'central_layout_setup_screen.dart';
import 'peripheral_region_editor_screen.dart';

class PeripheralVisionSetupScreen extends StatefulWidget {
  const PeripheralVisionSetupScreen({super.key});

  @override
  State<PeripheralVisionSetupScreen> createState() =>
      _PeripheralVisionSetupScreenState();
}

class _PeripheralVisionSetupScreenState
    extends State<PeripheralVisionSetupScreen> {
  RegionShape _region = const RegionShape(
    type: RegionShapeType.ellipse,
    x: 0.28,
    y: 0.30,
    width: 0.44,
    height: 0.32,
  );

  Future<void> _openRegionEditor() async {
    final result =
        await Navigator.push<RegionShape>(
      context,
      MaterialPageRoute(
        builder: (_) =>
            PeripheralRegionEditorScreen(
          initialRegion: _region,
        ),
      ),
    );

    if (!mounted || result == null) {
      return;
    }

    setState(() {
      _region = result;
    });
  }

  VisionProfile _buildProfile() {
    return VisionProfile(
      visionType:
          VisionType.peripheralLoss,
      visibleRegions: [
        _region,
      ],
    );
  }

  Future<void> _goNext() async {
    final profile = _buildProfile();

    final completedProfile =
        await Navigator.push<VisionProfile>(
      context,
      MaterialPageRoute(
        builder: (_) =>
            CentralLayoutSetupScreen(
          profile: profile,
        ),
      ),
    );

    if (!mounted ||
        completedProfile == null) {
      return;
    }

    Navigator.pop(
      context,
      completedProfile,
    );
  }

  void _reset() {
    setState(() {
      _region = const RegionShape(
        type: RegionShapeType.ellipse,
        x: 0.28,
        y: 0.30,
        width: 0.44,
        height: 0.32,
      );
    });
  }

  String _regionTypeLabel() {
    switch (_region.type) {
      case RegionShapeType.circle:
        return '원형';

      case RegionShapeType.ellipse:
        return '타원형';

      case RegionShapeType.custom:
        return '직접 그린 영역';

      case RegionShapeType.rectangle:
        return '사각형';
    }
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

            Expanded(
              child: SingleChildScrollView(
                padding:
                    const EdgeInsets.fromLTRB(
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
                      '잘 보이는 영역을\n설정해주세요.',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 29,
                        fontWeight:
                            FontWeight.w900,
                        height: 1.25,
                        letterSpacing: -0.7,
                      ),
                    ),

                    const SizedBox(height: 12),

                    Text(
                      '주변부가 잘 보이지 않는 경우, 정보를 집중해서 보고 싶은 영역을 지정할 수 있어요.',
                      style: TextStyle(
                        color:
                            Colors.white.withValues(
                          alpha: 0.65,
                        ),
                        fontSize: 15,
                        height: 1.5,
                      ),
                    ),

                    const SizedBox(height: 24),

                    GestureDetector(
                      onTap: _openRegionEditor,
                      child: _buildPreview(),
                    ),

                    const SizedBox(height: 16),

                    SizedBox(
                      width: double.infinity,
                      child:
                          OutlinedButton.icon(
                        onPressed:
                            _openRegionEditor,
                        icon: const Icon(
                          Icons
                              .open_in_full_rounded,
                        ),
                        label: const Text(
                          '전체 화면에서 잘 보이는 영역 편집',
                        ),
                        style:
                            OutlinedButton.styleFrom(
                          foregroundColor: yellow,
                          side: BorderSide(
                            color:
                                yellow.withValues(
                              alpha: 0.45,
                            ),
                          ),
                          padding:
                              const EdgeInsets
                                  .symmetric(
                            vertical: 15,
                          ),
                          shape:
                              RoundedRectangleBorder(
                            borderRadius:
                                BorderRadius
                                    .circular(
                              16,
                            ),
                          ),
                        ),
                      ),
                    ),

                    const SizedBox(height: 22),

                    Container(
                      width: double.infinity,
                      padding:
                          const EdgeInsets.all(
                        17,
                      ),
                      decoration: BoxDecoration(
                        color:
                            const Color(
                          0xFF111111,
                        ),
                        borderRadius:
                            BorderRadius.circular(
                          18,
                        ),
                        border: Border.all(
                          color: Colors.white
                              .withValues(
                            alpha: 0.08,
                          ),
                        ),
                      ),
                      child: Column(
                        crossAxisAlignment:
                            CrossAxisAlignment.start,
                        children: [
                          const Row(
                            children: [
                              Icon(
                                Icons
                                    .center_focus_strong_rounded,
                                color: yellow,
                                size: 23,
                              ),
                              SizedBox(width: 10),
                              Text(
                                '현재 설정',
                                style: TextStyle(
                                  color:
                                      Colors.white,
                                  fontSize: 16,
                                  fontWeight:
                                      FontWeight
                                          .w800,
                                ),
                              ),
                            ],
                          ),

                          const SizedBox(
                            height: 14,
                          ),

                          Row(
                            children: [
                              Expanded(
                                child: Text(
                                  '영역 모양',
                                  style: TextStyle(
                                    color: Colors
                                        .white
                                        .withValues(
                                      alpha: 0.62,
                                    ),
                                    fontSize: 13,
                                  ),
                                ),
                              ),
                              Text(
                                _regionTypeLabel(),
                                style:
                                    const TextStyle(
                                  color: yellow,
                                  fontSize: 14,
                                  fontWeight:
                                      FontWeight
                                          .w800,
                                ),
                              ),
                            ],
                          ),

                          const SizedBox(
                            height: 10,
                          ),

                          Row(
                            children: [
                              Expanded(
                                child: Text(
                                  '영역 너비',
                                  style: TextStyle(
                                    color: Colors
                                        .white
                                        .withValues(
                                      alpha: 0.62,
                                    ),
                                    fontSize: 13,
                                  ),
                                ),
                              ),
                              Text(
                                '${(_region.width * 100).round()}%',
                                style:
                                    const TextStyle(
                                  color:
                                      Colors.white,
                                  fontSize: 14,
                                  fontWeight:
                                      FontWeight
                                          .w700,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),

                    const SizedBox(height: 18),

                    Container(
                      width: double.infinity,
                      padding:
                          const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color:
                            yellow.withValues(
                          alpha: 0.07,
                        ),
                        borderRadius:
                            BorderRadius.circular(
                          18,
                        ),
                        border: Border.all(
                          color:
                              yellow.withValues(
                            alpha: 0.18,
                          ),
                        ),
                      ),
                      child: Row(
                        crossAxisAlignment:
                            CrossAxisAlignment.start,
                        children: [
                          const Icon(
                            Icons
                                .info_outline_rounded,
                            color: yellow,
                            size: 23,
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Text(
                              '이 영역 안쪽에 텍스트와 그림·표가 우선 배치되도록 설정합니다.',
                              style: TextStyle(
                                color: Colors.white
                                    .withValues(
                                  alpha: 0.78,
                                ),
                                fontSize: 14,
                                height: 1.5,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),

                    const SizedBox(height: 10),

                    Center(
                      child:
                          TextButton.icon(
                        onPressed: _reset,
                        icon: const Icon(
                          Icons
                              .restart_alt_rounded,
                        ),
                        label: const Text(
                          '기본 영역으로 되돌리기',
                        ),
                        style:
                            TextButton.styleFrom(
                          foregroundColor: Colors
                              .white
                              .withValues(
                            alpha: 0.70,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),

            Container(
              padding:
                  const EdgeInsets.fromLTRB(
                24,
                12,
                24,
                22,
              ),
              decoration: BoxDecoration(
                color: Colors.black,
                border: Border(
                  top: BorderSide(
                    color: Colors.white
                        .withValues(
                      alpha: 0.07,
                    ),
                  ),
                ),
              ),
              child: HaloPrimaryButton(
                text: '다음',
                icon:
                    Icons.arrow_forward_rounded,
                onPressed: _goNext,
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
              '주변부 시야 설정',
              style: TextStyle(
                color: Colors.white,
                fontSize: 21,
                fontWeight:
                    FontWeight.w800,
              ),
            ),
          ),

          const Text(
            '1 / 4',
            style: TextStyle(
              color: Color(
                0xFFF4D35E,
              ),
              fontSize: 14,
              fontWeight:
                  FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPreview() {
    return AspectRatio(
      aspectRatio: 0.78,
      child: LayoutBuilder(
        builder: (
          context,
          constraints,
        ) {
          final width =
              constraints.maxWidth;

          final height =
              constraints.maxHeight;

          return Container(
            decoration: BoxDecoration(
              color:
                  const Color(
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
                  top: 28,
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
                      SizedBox(height: 20),
                      _PreviewLine(
                        widthFactor: 1,
                      ),
                      SizedBox(height: 10),
                      _PreviewLine(
                        widthFactor:
                            0.82,
                      ),
                      SizedBox(height: 10),
                      _PreviewLine(
                        widthFactor:
                            0.94,
                      ),
                      SizedBox(height: 10),
                      _PreviewLine(
                        widthFactor:
                            0.70,
                      ),
                    ],
                  ),
                ),

                Positioned(
                  left: 24,
                  right: 24,
                  bottom: 28,
                  child: Container(
                    height: 90,
                    decoration:
                        BoxDecoration(
                      color:
                          Colors.black
                              .withValues(
                        alpha: 0.07,
                      ),
                      borderRadius:
                          BorderRadius
                              .circular(
                        14,
                      ),
                    ),
                    child:
                        const Center(
                      child: Icon(
                        Icons
                            .image_outlined,
                        color:
                            Colors.black38,
                        size: 38,
                      ),
                    ),
                  ),
                ),

                if (_region.type ==
                    RegionShapeType.custom)
                  Positioned(
                    left:
                        _region.x * width,
                    top:
                        _region.y * height,
                    width:
                        _region.width *
                            width,
                    height:
                        _region.height *
                            height,
                    child: IgnorePointer(
                      child: CustomPaint(
                        painter:
                            _VisibleCustomPainter(
                          points:
                              _region.points,
                        ),
                      ),
                    ),
                  )
                else
                  Positioned(
                    left:
                        _region.x * width,
                    top:
                        _region.y * height,
                    width:
                        _region.width *
                            width,
                    height:
                        _region.height *
                            height,
                    child: IgnorePointer(
                      child: Container(
                        decoration:
                            BoxDecoration(
                          color: const Color(
                            0xFF63C174,
                          ).withValues(
                            alpha: 0.26,
                          ),
                          shape: _region.type ==
                                  RegionShapeType
                                      .circle
                              ? BoxShape.circle
                              : BoxShape
                                  .rectangle,
                          borderRadius:
                              _region.type ==
                                      RegionShapeType
                                          .ellipse
                                  ? BorderRadius
                                      .all(
                                      Radius
                                          .elliptical(
                                        _region
                                                .width *
                                            width,
                                        _region
                                                .height *
                                            height,
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
                  ),

                Positioned.fill(
                  child: Center(
                    child: Container(
                      padding:
                          const EdgeInsets
                              .symmetric(
                        horizontal: 14,
                        vertical: 10,
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
                          12,
                        ),
                      ),
                      child:
                          const Row(
                        mainAxisSize:
                            MainAxisSize.min,
                        children: [
                          Icon(
                            Icons
                                .open_in_full_rounded,
                            color:
                                Colors.white,
                            size: 18,
                          ),
                          SizedBox(width: 8),
                          Text(
                            '눌러서 잘 보이는 영역 편집',
                            style:
                                TextStyle(
                              color:
                                  Colors.white,
                              fontSize: 12,
                              fontWeight:
                                  FontWeight
                                      .w800,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _VisibleCustomPainter
    extends CustomPainter {
  final List<Offset> points;

  const _VisibleCustomPainter({
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
        alpha: 0.45,
      )
      ..style =
          PaintingStyle.stroke
      ..strokeWidth = 24
      ..strokeCap =
          StrokeCap.round
      ..strokeJoin =
          StrokeJoin.round;

    final borderPaint = Paint()
      ..color =
          const Color(
        0xFF63C174,
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

    canvas.drawPath(
      path,
      borderPaint,
    );
  }

  @override
  bool shouldRepaint(
    covariant _VisibleCustomPainter
        oldDelegate,
  ) {
    return true;
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