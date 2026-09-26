import 'package:flutter/material.dart';

import '../models/vision_profile.dart';
import '../widgets/halo_primary_button.dart';

class DisplaySetupScreen extends StatefulWidget {
  final VisionProfile profile;
  final String stepText;
  final String title;

  const DisplaySetupScreen({
    super.key,
    required this.profile,
    this.stepText = '3 / 4',
    this.title = '디스플레이 설정',
  });

  @override
  State<DisplaySetupScreen> createState() =>
      _DisplaySetupScreenState();
}

class _DisplaySetupScreenState
    extends State<DisplaySetupScreen> {
  late double _brightness;
  late double _contrast;
  late double _sharpness;

  late double _fontScale;
  late double _lineSpacing;

  late bool _boldText;

  late ColorCorrectionMode _colorMode;

  @override
  void initState() {
    super.initState();

    _brightness = widget.profile.brightness;
    _contrast = widget.profile.contrast;
    _sharpness = widget.profile.sharpness;

    _fontScale = widget.profile.fontScale;
    _lineSpacing = widget.profile.lineSpacing;

    _boldText = widget.profile.boldText;

    _colorMode =
        widget.profile.colorCorrectionMode;
  }

  void _reset() {
    setState(() {
      _brightness = 1.0;
      _contrast = 1.0;
      _sharpness = 1.0;

      _fontScale = 1.0;
      _lineSpacing = 1.4;

      _boldText = false;

      _colorMode =
          ColorCorrectionMode.normal;
    });
  }

  void _complete() {
    final updatedProfile =
        widget.profile.copyWith(
      brightness: _brightness,
      contrast: _contrast,
      sharpness: _sharpness,
      fontScale: _fontScale,
      lineSpacing: _lineSpacing,
      boldText: _boldText,
      colorCorrectionMode:
          _colorMode,
    );

    Navigator.pop(
      context,
      updatedProfile,
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
                      '화면을 보기 편하게\n조정해주세요.',
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
                      '글자 크기와 대비, 밝기 등을 조절하면 문서가 더 편하게 보일 수 있어요.',
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

                    _buildLivePreview(),

                    const SizedBox(height: 28),

                    _buildSectionTitle(
                      '텍스트 설정',
                    ),

                    const SizedBox(height: 14),

                    _buildSliderCard(
                      title: '글자 크기',
                      valueLabel:
                          '${(_fontScale * 100).round()}%',
                      value: _fontScale,
                      min: 0.8,
                      max: 1.8,
                      divisions: 20,
                      onChanged: (value) {
                        setState(() {
                          _fontScale = value;
                        });
                      },
                    ),

                    const SizedBox(height: 12),

                    _buildSliderCard(
                      title: '줄 간격',
                      valueLabel:
                          _lineSpacing.toStringAsFixed(
                        1,
                      ),
                      value: _lineSpacing,
                      min: 1.0,
                      max: 2.2,
                      divisions: 12,
                      onChanged: (value) {
                        setState(() {
                          _lineSpacing = value;
                        });
                      },
                    ),

                    const SizedBox(height: 12),

                    _buildToggleCard(
                      icon:
                          Icons.format_bold_rounded,
                      title: '굵은 글씨',
                      description:
                          '텍스트를 더 두껍게 표시합니다.',
                      value: _boldText,
                      onChanged: (value) {
                        setState(() {
                          _boldText = value;
                        });
                      },
                    ),

                    const SizedBox(height: 28),

                    _buildSectionTitle(
                      '화면 보정',
                    ),

                    const SizedBox(height: 14),

                    _buildSliderCard(
                      title: '밝기',
                      valueLabel:
                          '${(_brightness * 100).round()}%',
                      value: _brightness,
                      min: 0.6,
                      max: 1.4,
                      divisions: 16,
                      onChanged: (value) {
                        setState(() {
                          _brightness = value;
                        });
                      },
                    ),

                    const SizedBox(height: 12),

                    _buildSliderCard(
                      title: '대비',
                      valueLabel:
                          '${(_contrast * 100).round()}%',
                      value: _contrast,
                      min: 0.7,
                      max: 1.6,
                      divisions: 18,
                      onChanged: (value) {
                        setState(() {
                          _contrast = value;
                        });
                      },
                    ),

                    const SizedBox(height: 12),

                    _buildSliderCard(
                      title: '선명도',
                      valueLabel:
                          '${(_sharpness * 100).round()}%',
                      value: _sharpness,
                      min: 0.7,
                      max: 1.6,
                      divisions: 18,
                      onChanged: (value) {
                        setState(() {
                          _sharpness = value;
                        });
                      },
                    ),

                    const SizedBox(height: 28),

                    _buildSectionTitle(
                      '색상 보정',
                    ),

                    const SizedBox(height: 14),

                    _ColorModeCard(
                      title: '기본',
                      description:
                          '색상을 변경하지 않습니다.',
                      selected:
                          _colorMode ==
                              ColorCorrectionMode
                                  .normal,
                      onTap: () {
                        setState(() {
                          _colorMode =
                              ColorCorrectionMode
                                  .normal;
                        });
                      },
                    ),

                    const SizedBox(height: 10),

                    _ColorModeCard(
                      title: '고대비',
                      description:
                          '텍스트와 배경의 구분을 더 강하게 합니다.',
                      selected:
                          _colorMode ==
                              ColorCorrectionMode
                                  .highContrast,
                      onTap: () {
                        setState(() {
                          _colorMode =
                              ColorCorrectionMode
                                  .highContrast;
                        });
                      },
                    ),

                    const SizedBox(height: 10),

                    _ColorModeCard(
                      title: '적·녹색 구분 보조',
                      description:
                          '빨강과 초록 계열 색상의 구분을 돕습니다.',
                      selected:
                          _colorMode ==
                              ColorCorrectionMode
                                  .redGreenAssist,
                      onTap: () {
                        setState(() {
                          _colorMode =
                              ColorCorrectionMode
                                  .redGreenAssist;
                        });
                      },
                    ),

                    const SizedBox(height: 10),

                    _ColorModeCard(
                      title: '청·황색 구분 보조',
                      description:
                          '파랑과 노랑 계열 색상의 구분을 돕습니다.',
                      selected:
                          _colorMode ==
                              ColorCorrectionMode
                                  .blueYellowAssist,
                      onTap: () {
                        setState(() {
                          _colorMode =
                              ColorCorrectionMode
                                  .blueYellowAssist;
                        });
                      },
                    ),

                    const SizedBox(height: 18),

                    Center(
                      child: TextButton.icon(
                        onPressed: _reset,
                        icon: const Icon(
                          Icons.restart_alt_rounded,
                        ),
                        label: const Text(
                          '기본값으로 되돌리기',
                        ),
                        style:
                            TextButton.styleFrom(
                          foregroundColor:
                              Colors.white.withValues(
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
        Expanded(
          child: Text(
            widget.title,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 21,
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
        Text(
          widget.stepText,
          style: const TextStyle(
            color: Color(0xFFF4D35E),
            fontSize: 14,
            fontWeight: FontWeight.w800,
          ),
        ),
      ],
    ),
  );
}

  Widget _buildSectionTitle(
    String title,
  ) {
    return Text(
      title,
      style: const TextStyle(
        color: Colors.white,
        fontSize: 18,
        fontWeight: FontWeight.w900,
      ),
    );
  }

  Widget _buildLivePreview() {
    final baseBackground =
        _colorMode ==
                ColorCorrectionMode.highContrast
            ? Colors.black
            : const Color(0xFFF4F4F4);

    final textColor =
        _colorMode ==
                ColorCorrectionMode.highContrast
            ? Colors.white
            : Colors.black87;

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: baseBackground,
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: Colors.white.withValues(
            alpha: 0.12,
          ),
        ),
      ),
      child: Column(
        crossAxisAlignment:
            CrossAxisAlignment.start,
        children: [
          Text(
            '미리보기',
            style: TextStyle(
              color: textColor,
              fontSize:
                  16 * _fontScale,
              fontWeight: _boldText
                  ? FontWeight.w900
                  : FontWeight.w700,
            ),
          ),

          const SizedBox(height: 14),

          Text(
            'HaloVision은 문서의 내용을 사용자의 시야에 맞게 보기 편한 위치로 재배치합니다.',
            style: TextStyle(
              color: textColor,
              fontSize:
                  15 * _fontScale,
              fontWeight: _boldText
                  ? FontWeight.w800
                  : FontWeight.w500,
              height: _lineSpacing,
            ),
          ),

          const SizedBox(height: 18),

          Container(
            width: double.infinity,
            height: 70,
            decoration: BoxDecoration(
              color:
                  _previewMediaColor(),
              borderRadius:
                  BorderRadius.circular(
                14,
              ),
            ),
            child: Icon(
              Icons.image_outlined,
              color: textColor.withValues(
                alpha: 0.55,
              ),
              size: 38,
            ),
          ),
        ],
      ),
    );
  }

  Color _previewMediaColor() {
    switch (_colorMode) {
      case ColorCorrectionMode.normal:
        return Colors.grey.shade300;

      case ColorCorrectionMode.highContrast:
        return Colors.white.withValues(
          alpha: 0.15,
        );

      case ColorCorrectionMode.redGreenAssist:
        return const Color(
          0xFFB5CCFF,
        );

      case ColorCorrectionMode.blueYellowAssist:
        return const Color(
          0xFFFFD8A8,
        );
    }
  }

  Widget _buildSliderCard({
    required String title,
    required String valueLabel,
    required double value,
    required double min,
    required double max,
    required int divisions,
    required ValueChanged<double>
        onChanged,
  }) {
    const yellow = Color(0xFFF4D35E);

    return Container(
      padding: const EdgeInsets.fromLTRB(
        16,
        14,
        16,
        10,
      ),
      decoration: BoxDecoration(
        color: const Color(0xFF111111),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: Colors.white.withValues(
            alpha: 0.08,
          ),
        ),
      ),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  title,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 15,
                    fontWeight:
                        FontWeight.w800,
                  ),
                ),
              ),
              Text(
                valueLabel,
                style: const TextStyle(
                  color: yellow,
                  fontSize: 14,
                  fontWeight:
                      FontWeight.w800,
                ),
              ),
            ],
          ),

          Slider(
            value: value,
            min: min,
            max: max,
            divisions: divisions,
            activeColor: yellow,
            inactiveColor:
                Colors.white.withValues(
              alpha: 0.14,
            ),
            onChanged: onChanged,
          ),
        ],
      ),
    );
  }

  Widget _buildToggleCard({
    required IconData icon,
    required String title,
    required String description,
    required bool value,
    required ValueChanged<bool>
        onChanged,
  }) {
    const yellow = Color(0xFFF4D35E);

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF111111),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: value
              ? yellow
              : Colors.white.withValues(
                  alpha: 0.08,
                ),
        ),
      ),
      child: Row(
        children: [
          Icon(
            icon,
            color: value
                ? yellow
                : Colors.white70,
            size: 25,
          ),

          const SizedBox(width: 13),

          Expanded(
            child: Column(
              crossAxisAlignment:
                  CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 15,
                    fontWeight:
                        FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  description,
                  style: TextStyle(
                    color:
                        Colors.white.withValues(
                      alpha: 0.60,
                    ),
                    fontSize: 12,
                    height: 1.35,
                  ),
                ),
              ],
            ),
          ),

          Switch(
            value: value,
            activeThumbColor: yellow,
            onChanged: onChanged,
          ),
        ],
      ),
    );
  }
}

class _ColorModeCard
    extends StatelessWidget {
  final String title;
  final String description;
  final bool selected;
  final VoidCallback onTap;

  const _ColorModeCard({
    required this.title,
    required this.description,
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
            BorderRadius.circular(18),
        child: AnimatedContainer(
          duration: const Duration(
            milliseconds: 160,
          ),
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: selected
                ? yellow.withValues(
                    alpha: 0.12,
                  )
                : const Color(
                    0xFF111111,
                  ),
            borderRadius:
                BorderRadius.circular(
              18,
            ),
            border: Border.all(
              color: selected
                  ? yellow
                  : Colors.white.withValues(
                      alpha: 0.08,
                    ),
              width:
                  selected ? 2 : 1,
            ),
          ),
          child: Row(
            children: [
              Icon(
                selected
                    ? Icons
                        .radio_button_checked_rounded
                    : Icons
                        .radio_button_off_rounded,
                color: selected
                    ? yellow
                    : Colors.white54,
                size: 24,
              ),

              const SizedBox(width: 13),

              Expanded(
                child: Column(
                  crossAxisAlignment:
                      CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: TextStyle(
                        color: selected
                            ? yellow
                            : Colors.white,
                        fontSize: 15,
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
                          alpha: 0.60,
                        ),
                        fontSize: 12,
                        height: 1.35,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}