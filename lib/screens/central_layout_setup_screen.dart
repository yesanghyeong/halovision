import 'package:flutter/material.dart';
import 'display_setup_screen.dart';
import '../models/vision_profile.dart';
import '../widgets/halo_primary_button.dart';
import 'central_preview_screen.dart';
import 'layout_editor_screen.dart';

class CentralLayoutSetupScreen extends StatefulWidget {
  final VisionProfile profile;

  const CentralLayoutSetupScreen({
    super.key,
    required this.profile,
  });

  @override
  State<CentralLayoutSetupScreen> createState() =>
      _CentralLayoutSetupScreenState();
}

class _CentralLayoutSetupScreenState
    extends State<CentralLayoutSetupScreen> {
  late VisionProfile _profile;

  @override
  void initState() {
    super.initState();

    _profile = widget.profile;

    if (_profile.textRegion == null ||
        _profile.mediaRegion == null) {
      _profile = _profile.copyWith(
        textRegion: _profile.textRegion ??
            const LayoutRegion(
              x: 0.08,
              y: 0.62,
              width: 0.84,
              height: 0.20,
            ),
        mediaRegion: _profile.mediaRegion ??
            const LayoutRegion(
              x: 0.58,
              y: 0.12,
              width: 0.32,
              height: 0.24,
            ),
      );
    }
  }

  Future<void> _openLayoutEditor() async {
    final result =
        await Navigator.push<LayoutEditorResult>(
      context,
      MaterialPageRoute(
        builder: (_) => LayoutEditorScreen(
          profile: _profile,
        ),
      ),
    );

    if (!mounted || result == null) {
      return;
    }

    setState(() {
      _profile = _profile.copyWith(
        textRegion: result.textRegion,
        mediaRegion: result.mediaRegion,
      );
    });
  }

  Future<void> _goNext() async {
  final displayProfile =
      await Navigator.push<VisionProfile>(
    context,
    MaterialPageRoute(
      builder: (_) => DisplaySetupScreen(
        profile: _profile,
      ),
    ),
  );

  if (!mounted || displayProfile == null) {
    return;
  }

  final completedProfile =
      await Navigator.push<VisionProfile>(
    context,
    MaterialPageRoute(
      builder: (_) => CentralPreviewScreen(
        profile: displayProfile,
      ),
    ),
  );

  if (!mounted || completedProfile == null) {
    return;
  }

  Navigator.pop(
    context,
    completedProfile,
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
                      '정보가 표시될 위치를\n정해주세요.',
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
                      '미리보기를 눌러 전체 화면에서 텍스트와 그림·표 위치를 편하게 조절할 수 있어요.',
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
                      onTap: _openLayoutEditor,
                      child: _buildPreview(),
                    ),

                    const SizedBox(height: 16),

                    SizedBox(
                      width: double.infinity,
                      child: OutlinedButton.icon(
                        onPressed: _openLayoutEditor,
                        icon: const Icon(
                          Icons.open_in_full_rounded,
                        ),
                        label: const Text(
                          '전체 화면에서 배치 편집',
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
                              const EdgeInsets.symmetric(
                            vertical: 15,
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

                    const SizedBox(height: 20),

                    Container(
                      width: double.infinity,
                      padding:
                          const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color:
                            const Color(0xFF111111),
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
                      child: Row(
                        crossAxisAlignment:
                            CrossAxisAlignment.start,
                        children: [
                          const Icon(
                            Icons.touch_app_rounded,
                            color: yellow,
                            size: 24,
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Text(
                              '작은 미리보기에서는 위치 확인만 하고, 실제 이동과 크기 조절은 전체 화면 편집기에서 진행합니다.',
                              style: TextStyle(
                                color:
                                    Colors.white.withValues(
                                  alpha: 0.78,
                                ),
                                fontSize: 14,
                                fontWeight:
                                    FontWeight.w600,
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
                    color:
                        Colors.white.withValues(
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
              '정보 배치 설정',
              style: TextStyle(
                color: Colors.white,
                fontSize: 21,
                fontWeight:
                    FontWeight.w800,
              ),
            ),
          ),
          const Text(
            '2 / 4',
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
                  top: 26,
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
                      SizedBox(height: 18),
                      _PreviewLine(
                        widthFactor: 1,
                      ),
                      SizedBox(height: 10),
                      _PreviewLine(
                        widthFactor:
                            0.83,
                      ),
                      SizedBox(height: 10),
                      _PreviewLine(
                        widthFactor:
                            0.94,
                      ),
                    ],
                  ),
                ),

                ..._buildAvoidRegions(
                  width,
                  height,
                ),

                if (_profile.textRegion !=
                    null)
                  _buildTextPreview(
                    _profile.textRegion!,
                    width,
                    height,
                  ),

                if (_profile.mediaRegion !=
                    null)
                  _buildMediaPreview(
                    _profile.mediaRegion!,
                    width,
                    height,
                  ),

                Positioned.fill(
                  child: Container(
                    color: Colors.transparent,
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
                              BorderRadius.circular(
                            12,
                          ),
                        ),
                        child: const Row(
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
                              '눌러서 전체 화면 편집',
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
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  List<Widget> _buildAvoidRegions(
    double width,
    double height,
  ) {
    return _profile.avoidRegions.map(
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
                    _CustomRegionPainter(
                  points: region.points,
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
                    : BoxShape.rectangle,
                borderRadius:
                    region.type ==
                            RegionShapeType
                                .ellipse
                        ? BorderRadius.all(
                            Radius.elliptical(
                              regionWidth,
                              regionHeight,
                            ),
                          )
                        : null,
                border: Border.all(
                  color: const Color(
                    0xFFFFE681,
                  ),
                  width: 2,
                ),
              ),
            ),
          ),
        );
      },
    ).toList();
  }

  Widget _buildTextPreview(
    LayoutRegion region,
    double width,
    double height,
  ) {
    const blue = Color(
      0xFF5AA9FF,
    );

    return Positioned(
      left: region.x * width,
      top: region.y * height,
      width:
          region.width * width,
      height:
          region.height * height,
      child: IgnorePointer(
        child: Container(
          decoration: BoxDecoration(
            color: blue.withValues(
              alpha: 0.18,
            ),
            borderRadius:
                BorderRadius.circular(
              14,
            ),
            border: Border.all(
              color: blue,
              width: 2,
            ),
          ),
          child: const Center(
            child: Text(
              '텍스트',
              style: TextStyle(
                color:
                    Colors.black87,
                fontWeight:
                    FontWeight.w800,
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildMediaPreview(
    LayoutRegion region,
    double width,
    double height,
  ) {
    const green = Color(
      0xFF63C174,
    );

    return Positioned(
      left: region.x * width,
      top: region.y * height,
      width:
          region.width * width,
      height:
          region.height * height,
      child: IgnorePointer(
        child: Container(
          decoration: BoxDecoration(
            color: green.withValues(
              alpha: 0.18,
            ),
            borderRadius:
                BorderRadius.circular(
              14,
            ),
            border: Border.all(
              color: green,
              width: 2,
            ),
          ),
          child: const Center(
            child: Icon(
              Icons.image_outlined,
              color:
                  Colors.black54,
              size: 38,
            ),
          ),
        ),
      ),
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
      ..color =
          const Color(
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

class _PreviewLine
    extends StatelessWidget {
  final double widthFactor;

  const _PreviewLine({
    required this.widthFactor,
  });

  @override
  Widget build(BuildContext context) {
    return FractionallySizedBox(
      widthFactor:
          widthFactor,
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