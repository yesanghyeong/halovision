import 'package:flutter/material.dart';

import '../models/vision_profile.dart';
import '../widgets/halo_primary_button.dart';
import 'central_preview_screen.dart';
import 'display_setup_screen.dart';

enum _UnknownPreset {
  largeText,
  highContrast,
  balanced,
}

class UnknownSetupScreen extends StatefulWidget {
  const UnknownSetupScreen({
    super.key,
  });

  @override
  State<UnknownSetupScreen> createState() =>
      _UnknownSetupScreenState();
}

class _UnknownSetupScreenState
    extends State<UnknownSetupScreen> {
  _UnknownPreset _selected =
      _UnknownPreset.balanced;

  VisionProfile _buildProfile() {
    switch (_selected) {
      case _UnknownPreset.largeText:
        return const VisionProfile(
          visionType: VisionType.unknown,
          fontScale: 1.45,
          lineSpacing: 1.7,
          boldText: true,
          brightness: 1.05,
          contrast: 1.1,
          sharpness: 1.15,
          colorCorrectionMode:
              ColorCorrectionMode.normal,
        );

      case _UnknownPreset.highContrast:
        return const VisionProfile(
          visionType: VisionType.unknown,
          fontScale: 1.15,
          lineSpacing: 1.5,
          boldText: true,
          brightness: 1.0,
          contrast: 1.4,
          sharpness: 1.2,
          colorCorrectionMode:
              ColorCorrectionMode.highContrast,
        );

      case _UnknownPreset.balanced:
        return const VisionProfile(
          visionType: VisionType.unknown,
          fontScale: 1.2,
          lineSpacing: 1.5,
          boldText: false,
          brightness: 1.05,
          contrast: 1.15,
          sharpness: 1.1,
          colorCorrectionMode:
              ColorCorrectionMode.normal,
        );
    }
  }

  Future<void> _goNext() async {
    final initialProfile =
        _buildProfile();

    final displayProfile =
        await Navigator.push<VisionProfile>(
      context,
      MaterialPageRoute(
        builder: (_) =>
            DisplaySetupScreen(
          profile: initialProfile,
          stepText: '2 / 3',
          title: '세부 화면 조정',
        ),
      ),
    );

    if (!mounted ||
        displayProfile == null) {
      return;
    }

    final completedProfile =
        await Navigator.push<VisionProfile>(
      context,
      MaterialPageRoute(
        builder: (_) =>
            CentralPreviewScreen(
          profile: displayProfile,
          stepText: '3 / 3',
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(),

            Expanded(
              child:
                  SingleChildScrollView(
                padding:
                    const EdgeInsets
                        .fromLTRB(
                  24,
                  16,
                  24,
                  30,
                ),
                child: Column(
                  crossAxisAlignment:
                      CrossAxisAlignment
                          .start,
                  children: [
                    const Text(
                      '어떤 화면이 편한지\n직접 비교해보세요.',
                      style: TextStyle(
                        color:
                            Colors.white,
                        fontSize: 29,
                        fontWeight:
                            FontWeight.w900,
                        height: 1.25,
                        letterSpacing: -0.7,
                      ),
                    ),

                    const SizedBox(
                      height: 12,
                    ),

                    Text(
                      '시야 유형을 판단하는 과정이 아니며, 가장 읽기 편한 화면을 선택하기 위한 설정입니다.',
                      style: TextStyle(
                        color: Colors
                            .white
                            .withValues(
                          alpha: 0.66,
                        ),
                        fontSize: 14,
                        height: 1.5,
                      ),
                    ),

                    const SizedBox(
                      height: 26,
                    ),

                    _PresetCard(
                      selected:
                          _selected ==
                              _UnknownPreset
                                  .largeText,
                      title:
                          '글자가 큰 화면',
                      description:
                          '큰 글자와 넓은 줄 간격을 사용합니다.',
                      icon: Icons
                          .format_size_rounded,
                      preview:
                          _LargeTextPreview(),
                      onTap: () {
                        setState(() {
                          _selected =
                              _UnknownPreset
                                  .largeText;
                        });
                      },
                    ),

                    const SizedBox(
                      height: 14,
                    ),

                    _PresetCard(
                      selected:
                          _selected ==
                              _UnknownPreset
                                  .highContrast,
                      title:
                          '대비가 강한 화면',
                      description:
                          '글자와 배경을 명확하게 구분합니다.',
                      icon: Icons
                          .contrast_rounded,
                      preview:
                          _HighContrastPreview(),
                      onTap: () {
                        setState(() {
                          _selected =
                              _UnknownPreset
                                  .highContrast;
                        });
                      },
                    ),

                    const SizedBox(
                      height: 14,
                    ),

                    _PresetCard(
                      selected:
                          _selected ==
                              _UnknownPreset
                                  .balanced,
                      title:
                          '균형 잡힌 화면',
                      description:
                          '글자 크기와 대비를 적당히 높입니다.',
                      icon: Icons
                          .tune_rounded,
                      preview:
                          _BalancedPreview(),
                      onTap: () {
                        setState(() {
                          _selected =
                              _UnknownPreset
                                  .balanced;
                        });
                      },
                    ),

                    const SizedBox(
                      height: 20,
                    ),

                    Container(
                      width:
                          double.infinity,
                      padding:
                          const EdgeInsets
                              .all(
                        16,
                      ),
                      decoration:
                          BoxDecoration(
                        color:
                            const Color(
                          0xFFF4D35E,
                        ).withValues(
                          alpha: 0.08,
                        ),
                        borderRadius:
                            BorderRadius
                                .circular(
                          18,
                        ),
                        border:
                            Border.all(
                          color:
                              const Color(
                            0xFFF4D35E,
                          ).withValues(
                            alpha: 0.20,
                          ),
                        ),
                      ),
                      child: Row(
                        crossAxisAlignment:
                            CrossAxisAlignment
                                .start,
                        children: [
                          const Icon(
                            Icons
                                .info_outline_rounded,
                            color:
                                Color(
                              0xFFF4D35E,
                            ),
                          ),
                          const SizedBox(
                            width: 12,
                          ),
                          Expanded(
                            child: Text(
                              '다음 화면에서 글자 크기, 줄 간격, 대비, 선명도 등을 다시 세밀하게 조절할 수 있어요.',
                              style:
                                  TextStyle(
                                color: Colors
                                    .white
                                    .withValues(
                                  alpha:
                                      0.76,
                                ),
                                fontSize:
                                    13,
                                height:
                                    1.5,
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
              padding:
                  const EdgeInsets
                      .fromLTRB(
                24,
                12,
                24,
                22,
              ),
              decoration:
                  BoxDecoration(
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
              child:
                  HaloPrimaryButton(
                text:
                    '이 화면으로 시작하기',
                icon: Icons
                    .arrow_forward_rounded,
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
          const SizedBox(
            width: 4,
          ),
          const Expanded(
            child: Text(
              '보기 편한 화면 찾기',
              style: TextStyle(
                color:
                    Colors.white,
                fontSize: 20,
                fontWeight:
                    FontWeight.w800,
              ),
            ),
          ),
          const Text(
            '1 / 3',
            style: TextStyle(
              color:
                  Color(
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
}

class _PresetCard
    extends StatelessWidget {
  final bool selected;
  final String title;
  final String description;
  final IconData icon;
  final Widget preview;
  final VoidCallback onTap;

  const _PresetCard({
    required this.selected,
    required this.title,
    required this.description,
    required this.icon,
    required this.preview,
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
            BorderRadius.circular(
          20,
        ),
        child: AnimatedContainer(
          duration:
              const Duration(
            milliseconds: 180,
          ),
          width: double.infinity,
          padding:
              const EdgeInsets.all(
            16,
          ),
          decoration:
              BoxDecoration(
            color: selected
                ? yellow.withValues(
                    alpha: 0.10,
                  )
                : const Color(
                    0xFF111111,
                  ),
            borderRadius:
                BorderRadius.circular(
              20,
            ),
            border: Border.all(
              color: selected
                  ? yellow
                  : Colors.white
                      .withValues(
                    alpha: 0.09,
                  ),
              width:
                  selected ? 2 : 1,
            ),
          ),
          child: Column(
            crossAxisAlignment:
                CrossAxisAlignment
                    .start,
            children: [
              Row(
                children: [
                  Container(
                    width: 42,
                    height: 42,
                    decoration:
                        BoxDecoration(
                      color: selected
                          ? yellow
                              .withValues(
                              alpha:
                                  0.18,
                            )
                          : Colors.white
                              .withValues(
                              alpha:
                                  0.06,
                            ),
                      borderRadius:
                          BorderRadius
                              .circular(
                        13,
                      ),
                    ),
                    child: Icon(
                      icon,
                      color: selected
                          ? yellow
                          : Colors
                              .white70,
                    ),
                  ),

                  const SizedBox(
                    width: 12,
                  ),

                  Expanded(
                    child: Column(
                      crossAxisAlignment:
                          CrossAxisAlignment
                              .start,
                      children: [
                        Text(
                          title,
                          style:
                              const TextStyle(
                            color: Colors
                                .white,
                            fontSize:
                                16,
                            fontWeight:
                                FontWeight
                                    .w800,
                          ),
                        ),
                        const SizedBox(
                          height: 3,
                        ),
                        Text(
                          description,
                          style:
                              TextStyle(
                            color: Colors
                                .white
                                .withValues(
                              alpha:
                                  0.58,
                            ),
                            fontSize:
                                12,
                            height:
                                1.4,
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
                        ? yellow
                        : Colors.white30,
                  ),
                ],
              ),

              const SizedBox(
                height: 14,
              ),

              preview,
            ],
          ),
        ),
      ),
    );
  }
}

class _LargeTextPreview
    extends StatelessWidget {
  const _LargeTextPreview();

  @override
  Widget build(BuildContext context) {
    return _PreviewContainer(
      child: Column(
        crossAxisAlignment:
            CrossAxisAlignment
                .start,
        mainAxisAlignment:
            MainAxisAlignment
                .center,
        children: [
          const Text(
            '문서 제목',
            style: TextStyle(
              color: Colors.black,
              fontSize: 22,
              fontWeight:
                  FontWeight.w900,
            ),
          ),
          const SizedBox(
            height: 12,
          ),
          Container(
            height: 10,
            width: double.infinity,
            decoration:
                _lineDecoration(),
          ),
          const SizedBox(
            height: 10,
          ),
          FractionallySizedBox(
            widthFactor: 0.72,
            child: Container(
              height: 10,
              decoration:
                  _lineDecoration(),
            ),
          ),
        ],
      ),
    );
  }
}

class _HighContrastPreview
    extends StatelessWidget {
  const _HighContrastPreview();

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 120,
      width: double.infinity,
      padding:
          const EdgeInsets.all(
        18,
      ),
      decoration:
          BoxDecoration(
        color: Colors.black,
        borderRadius:
            BorderRadius.circular(
          15,
        ),
        border: Border.all(
          color: Colors.white,
          width: 2,
        ),
      ),
      child: const Column(
        crossAxisAlignment:
            CrossAxisAlignment
                .start,
        mainAxisAlignment:
            MainAxisAlignment
                .center,
        children: [
          Text(
            '문서 제목',
            style: TextStyle(
              color: Colors.white,
              fontSize: 18,
              fontWeight:
                  FontWeight.w900,
            ),
          ),
          SizedBox(height: 10),
          Text(
            '글자와 배경을 명확하게 구분합니다.',
            style: TextStyle(
              color: Colors.white,
              fontSize: 14,
              fontWeight:
                  FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}

class _BalancedPreview
    extends StatelessWidget {
  const _BalancedPreview();

  @override
  Widget build(BuildContext context) {
    return _PreviewContainer(
      child: Column(
        crossAxisAlignment:
            CrossAxisAlignment
                .start,
        mainAxisAlignment:
            MainAxisAlignment
                .center,
        children: [
          const Text(
            '문서 제목',
            style: TextStyle(
              color: Colors.black87,
              fontSize: 18,
              fontWeight:
                  FontWeight.w800,
            ),
          ),
          const SizedBox(
            height: 10,
          ),
          Container(
            height: 8,
            width: double.infinity,
            decoration:
                _lineDecoration(),
          ),
          const SizedBox(
            height: 8,
          ),
          FractionallySizedBox(
            widthFactor: 0.84,
            child: Container(
              height: 8,
              decoration:
                  _lineDecoration(),
            ),
          ),
          const SizedBox(
            height: 8,
          ),
          FractionallySizedBox(
            widthFactor: 0.62,
            child: Container(
              height: 8,
              decoration:
                  _lineDecoration(),
            ),
          ),
        ],
      ),
    );
  }
}

class _PreviewContainer
    extends StatelessWidget {
  final Widget child;

  const _PreviewContainer({
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      height: 120,
      padding:
          const EdgeInsets.all(
        18,
      ),
      decoration:
          BoxDecoration(
        color: const Color(
          0xFFF3F3F3,
        ),
        borderRadius:
            BorderRadius.circular(
          15,
        ),
      ),
      child: child,
    );
  }
}

BoxDecoration _lineDecoration() {
  return BoxDecoration(
    color: Colors.black.withValues(
      alpha: 0.20,
    ),
    borderRadius:
        BorderRadius.circular(6),
  );
}