import 'package:flutter/material.dart';

import '../models/vision_profile.dart';
import '../widgets/halo_primary_button.dart';
import 'central_layout_setup_screen.dart';
import 'central_region_editor_screen.dart';
import 'peripheral_region_editor_screen.dart';

class CustomSetupScreen extends StatefulWidget {
  const CustomSetupScreen({
    super.key,
  });

  @override
  State<CustomSetupScreen> createState() =>
      _CustomSetupScreenState();
}

class _CustomSetupScreenState
    extends State<CustomSetupScreen> {
  bool _useAvoidRegion = true;

  Future<void> _goNext() async {
  RegionShape? region;

  if (_useAvoidRegion) {
    region = await Navigator.push<RegionShape>(
      context,
      MaterialPageRoute(
        builder: (_) => const CentralRegionEditorScreen(),
      ),
    );
  } else {
    region = await Navigator.push<RegionShape>(
      context,
      MaterialPageRoute(
        builder: (_) => const PeripheralRegionEditorScreen(),
      ),
    );
  }

  if (!mounted || region == null) {
    return;
  }

  final profile = VisionProfile(
    visionType: VisionType.custom,
    avoidRegions: _useAvoidRegion
        ? [region]
        : const [],
    visibleRegions: _useAvoidRegion
        ? const []
        : [region],
  );

  final completedProfile =
      await Navigator.push<VisionProfile>(
    context,
    MaterialPageRoute(
      builder: (_) => CentralLayoutSetupScreen(
        profile: profile,
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
    const green = Color(0xFF63C174);

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
                  18,
                  24,
                  30,
                ),
                child: Column(
                  crossAxisAlignment:
                      CrossAxisAlignment.start,
                  children: [
                    const Text(
                      '어떤 방식으로\n직접 설정할까요?',
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
                      '시야 유형을 선택하지 않고, 실제로 정보를 피하거나 집중해서 보여줄 영역을 직접 지정할 수 있어요.',
                      style: TextStyle(
                        color: Colors.white.withValues(
                          alpha: 0.65,
                        ),
                        fontSize: 14,
                        height: 1.5,
                      ),
                    ),

                    const SizedBox(height: 28),

                    _ModeCard(
                      selected: _useAvoidRegion,
                      title: '잘 보이지 않는 영역 피하기',
                      description:
                          '화면에서 잘 보이지 않는 부분을 직접 지정하고, 텍스트와 그림을 그 영역 밖에 배치합니다.',
                      icon: Icons.visibility_off_outlined,
                      color: yellow,
                      preview:
                          const _AvoidRegionPreview(),
                      onTap: () {
                        setState(() {
                          _useAvoidRegion = true;
                        });
                      },
                    ),

                    const SizedBox(height: 16),

                    _ModeCard(
                      selected: !_useAvoidRegion,
                      title: '잘 보이는 영역에 모아 보기',
                      description:
                          '화면에서 비교적 잘 보이는 부분을 직접 지정하고, 정보를 그 영역 안에 집중해서 배치합니다.',
                      icon: Icons.visibility_outlined,
                      color: green,
                      preview:
                          const _VisibleRegionPreview(),
                      onTap: () {
                        setState(() {
                          _useAvoidRegion = false;
                        });
                      },
                    ),

                    const SizedBox(height: 22),

                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color:
                            Colors.white.withValues(
                          alpha: 0.045,
                        ),
                        borderRadius:
                            BorderRadius.circular(18),
                        border: Border.all(
                          color:
                              Colors.white.withValues(
                            alpha: 0.08,
                          ),
                        ),
                      ),
                      child: Row(
                        crossAxisAlignment:
                            CrossAxisAlignment.start,
                        children: [
                          const Icon(
                            Icons.tune_rounded,
                            color: yellow,
                            size: 23,
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Text(
                              '다음 화면에서 영역의 위치와 크기를 직접 조절하고, 이후 텍스트와 그림·표의 배치도 원하는 위치로 설정할 수 있어요.',
                              style: TextStyle(
                                color: Colors.white
                                    .withValues(
                                  alpha: 0.76,
                                ),
                                fontSize: 13,
                                height: 1.5,
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
                text: '영역 설정하기',
                icon: Icons.arrow_forward_rounded,
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
              '직접 설정',
              style: TextStyle(
                color: Colors.white,
                fontSize: 20,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          const Text(
            '1 / 4',
            style: TextStyle(
              color: Color(0xFFF4D35E),
              fontSize: 14,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}

class _ModeCard extends StatelessWidget {
  final bool selected;
  final String title;
  final String description;
  final IconData icon;
  final Color color;
  final Widget preview;
  final VoidCallback onTap;

  const _ModeCard({
    required this.selected,
    required this.title,
    required this.description,
    required this.icon,
    required this.color,
    required this.preview,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius:
            BorderRadius.circular(20),
        child: AnimatedContainer(
          duration: const Duration(
            milliseconds: 180,
          ),
          width: double.infinity,
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: selected
                ? color.withValues(
                    alpha: 0.10,
                  )
                : const Color(0xFF111111),
            borderRadius:
                BorderRadius.circular(20),
            border: Border.all(
              color: selected
                  ? color
                  : Colors.white.withValues(
                      alpha: 0.09,
                    ),
              width: selected ? 2 : 1,
            ),
          ),
          child: Column(
            crossAxisAlignment:
                CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: color.withValues(
                        alpha: selected
                            ? 0.18
                            : 0.08,
                      ),
                      borderRadius:
                          BorderRadius.circular(
                        13,
                      ),
                    ),
                    child: Icon(
                      icon,
                      color: color,
                    ),
                  ),

                  const SizedBox(width: 12),

                  Expanded(
                    child: Column(
                      crossAxisAlignment:
                          CrossAxisAlignment.start,
                      children: [
                        Text(
                          title,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 16,
                            fontWeight:
                                FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          description,
                          style: TextStyle(
                            color: Colors.white
                                .withValues(
                              alpha: 0.58,
                            ),
                            fontSize: 12,
                            height: 1.4,
                          ),
                        ),
                      ],
                    ),
                  ),

                  Icon(
                    selected
                        ? Icons
                            .check_circle_rounded
                        : Icons
                            .radio_button_unchecked_rounded,
                    color: selected
                        ? color
                        : Colors.white30,
                  ),
                ],
              ),

              const SizedBox(height: 16),

              preview,
            ],
          ),
        ),
      ),
    );
  }
}

class _AvoidRegionPreview
    extends StatelessWidget {
  const _AvoidRegionPreview();

  @override
  Widget build(BuildContext context) {
    const yellow =
        Color(0xFFF4D35E);

    return AspectRatio(
      aspectRatio: 2.4,
      child: Container(
        decoration: BoxDecoration(
          color: const Color(
            0xFFF2F2F2,
          ),
          borderRadius:
              BorderRadius.circular(15),
        ),
        child: Stack(
          children: [
            const Positioned(
              left: 14,
              right: 14,
              top: 18,
              child: Column(
                children: [
                  _DemoLine(),
                  SizedBox(height: 8),
                  _DemoLine(
                    widthFactor: 0.78,
                  ),
                  SizedBox(height: 8),
                  _DemoLine(
                    widthFactor: 0.9,
                  ),
                ],
              ),
            ),

            Center(
              child: Container(
                width: 72,
                height: 58,
                decoration: BoxDecoration(
                  color: yellow.withValues(
                    alpha: 0.55,
                  ),
                  shape: BoxShape.circle,
                  border: Border.all(
                    color: yellow,
                    width: 3,
                  ),
                ),
                child: const Icon(
                  Icons.close_rounded,
                  color: Colors.black54,
                  size: 29,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _VisibleRegionPreview
    extends StatelessWidget {
  const _VisibleRegionPreview();

  @override
  Widget build(BuildContext context) {
    const green =
        Color(0xFF63C174);

    return AspectRatio(
      aspectRatio: 2.4,
      child: Container(
        decoration: BoxDecoration(
          color: const Color(
            0xFF252525,
          ),
          borderRadius:
              BorderRadius.circular(15),
        ),
        child: Stack(
          children: [
            Center(
              child: Container(
                width: 150,
                height: 82,
                padding:
                    const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: const Color(
                    0xFFF2F2F2,
                  ),
                  borderRadius:
                      BorderRadius.all(
                    Radius.elliptical(
                      150,
                      82,
                    ),
                  ),
                  border: Border.all(
                    color: green,
                    width: 3,
                  ),
                ),
                child: const Column(
                  mainAxisAlignment:
                      MainAxisAlignment.center,
                  children: [
                    _DemoLine(),
                    SizedBox(height: 8),
                    _DemoLine(
                      widthFactor: 0.72,
                    ),
                  ],
                ),
              ),
            ),

            const Positioned(
              right: 10,
              bottom: 8,
              child: Icon(
                Icons
                    .center_focus_strong_rounded,
                color: green,
                size: 24,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DemoLine
    extends StatelessWidget {
  final double widthFactor;

  const _DemoLine({
    this.widthFactor = 1.0,
  });

  @override
  Widget build(BuildContext context) {
    return FractionallySizedBox(
      widthFactor: widthFactor,
      child: Container(
        height: 8,
        decoration: BoxDecoration(
          color:
              Colors.black.withValues(
            alpha: 0.20,
          ),
          borderRadius:
              BorderRadius.circular(5),
        ),
      ),
    );
  }
}