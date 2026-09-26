import 'package:flutter/material.dart';
import 'peripheral_vision_setup_screen.dart';
import 'blurred_setup_screen.dart';
import 'color_difficulty_setup_screen.dart';
import 'unknown_setup_screen.dart';
import 'custom_setup_screen.dart';

import '../models/vision_profile.dart';
import '../widgets/halo_primary_button.dart';
import '../widgets/vision_option_card.dart';
import 'central_vision_setup_screen.dart';

class VisionSelectScreen extends StatefulWidget {
  const VisionSelectScreen({super.key});

  @override
  State<VisionSelectScreen> createState() => _VisionSelectScreenState();
}

class _VisionSelectScreenState extends State<VisionSelectScreen> {
  VisionType? _selectedType;

  void _selectType(VisionType type) {
    setState(() {
      _selectedType = type;
    });
  }

  Future<void> _goNext() async {
    final selectedType = _selectedType;

    if (selectedType == null) {
      return;
    }

    switch (selectedType) {
      case VisionType.centralLoss:
        final profile = await Navigator.push<VisionProfile>(
          context,
          MaterialPageRoute(
            builder: (_) => const CentralVisionSetupScreen(),
          ),
        );

        if (!mounted) {
          return;
        }

        if (profile != null) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text(
                '중심부 시야 설정이 적용되었습니다.',
              ),
            ),
          );
        }

        break;

      case VisionType.peripheralLoss:
  final profile = await Navigator.push<VisionProfile>(
    context,
    MaterialPageRoute(
      builder: (_) => const PeripheralVisionSetupScreen(),
    ),
  );

  if (!mounted) {
    return;
  }

  if (profile != null) {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text(
          '주변부 시야 설정이 적용되었습니다.',
        ),
      ),
    );
  }

  break;

      case VisionType.blurred:
        final profile = await Navigator.push<VisionProfile>(
          context,
          MaterialPageRoute(
            builder: (_) => const BlurredSetupScreen(),
    ),
  );

  if (!mounted) {
    return;
  }

  if (profile != null) {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text(
          '흐림 보정 설정이 적용되었습니다.',
        ),
      ),
    );
  }

  break;

      case VisionType.colorDifficulty:
      final profile = await Navigator.push<VisionProfile>(
        context,
        MaterialPageRoute(
          builder: (_) =>
              const ColorDifficultySetupScreen(),
    ),
  );

  if (!mounted) {
    return;
  }

  if (profile != null) {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text(
          '색상 보정 설정이 적용되었습니다.',
        ),
      ),
    );
  }

  break;

      case VisionType.unknown:
        final profile = await Navigator.push<VisionProfile>(
          context,
          MaterialPageRoute(
          builder: (_) => const UnknownSetupScreen(),
    ),
  );

  if (!mounted) {
    return;
  }

  if (profile != null) {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text(
          '보기 편한 화면 설정이 적용되었습니다.',
        ),
      ),
    );
  }

  break;

case VisionType.custom:
  final profile = await Navigator.push<VisionProfile>(
    context,
    MaterialPageRoute(
      builder: (_) => const CustomSetupScreen(),
    ),
  );

  if (!mounted) {
    return;
  }

  if (profile != null) {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text(
          '직접 설정이 적용되었습니다.',
        ),
      ),
    );
  }

  break;
    }
  }

 

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Column(
          children: [
            Padding(
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
                      '시야 설정',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 21,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                ],
              ),
            ),

            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(
                  24,
                  12,
                  24,
                  24,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      '평소 화면이나 글자가\n어떻게 보이나요?',
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
                      '가장 비슷한 항목을 하나 선택해주세요.\n나중에 언제든 다시 변경할 수 있어요.',
                      style: TextStyle(
                        color: Colors.white.withValues(
                          alpha: 0.64,
                        ),
                        fontSize: 15,
                        fontWeight: FontWeight.w500,
                        height: 1.5,
                      ),
                    ),

                    const SizedBox(height: 28),

                    VisionOptionCard(
                      title: '중심부가 잘 안 보여요',
                      description:
                          '화면 가운데의 글자나 물체를 보기 어려워요.',
                      icon:
                          Icons.radio_button_unchecked_rounded,
                      selected:
                          _selectedType == VisionType.centralLoss,
                      onTap: () {
                        _selectType(
                          VisionType.centralLoss,
                        );
                      },
                    ),

                    const SizedBox(height: 14),

                    VisionOptionCard(
                      title: '주변부가 잘 안 보여요',
                      description:
                          '화면 가장자리의 정보를 보기 어려워요.',
                      icon:
                          Icons.center_focus_strong_rounded,
                      selected:
                          _selectedType ==
                              VisionType.peripheralLoss,
                      onTap: () {
                        _selectType(
                          VisionType.peripheralLoss,
                        );
                      },
                    ),

                    const SizedBox(height: 14),

                    VisionOptionCard(
                      title: '전체적으로 흐릿해요',
                      description:
                          '글자와 그림의 경계가 선명하지 않게 보여요.',
                      icon: Icons.blur_on_rounded,
                      selected:
                          _selectedType == VisionType.blurred,
                      onTap: () {
                        _selectType(
                          VisionType.blurred,
                        );
                      },
                    ),

                    const SizedBox(height: 14),

                    VisionOptionCard(
                      title: '색 구분이 어려워요',
                      description:
                          '일부 색상이나 비슷한 색을 구별하기 어려워요.',
                      icon: Icons.palette_outlined,
                      selected:
                          _selectedType ==
                              VisionType.colorDifficulty,
                      onTap: () {
                        _selectType(
                          VisionType.colorDifficulty,
                        );
                      },
                    ),

                    const SizedBox(height: 14),

                    VisionOptionCard(
                      title: '잘 모르겠어요',
                      description:
                          '간단한 화면 비교를 통해 추천 설정을 찾아볼게요.',
                      icon:
                          Icons.help_outline_rounded,
                      selected:
                          _selectedType == VisionType.unknown,
                      onTap: () {
                        _selectType(
                          VisionType.unknown,
                        );
                      },
                    ),

                    const SizedBox(height: 14),

                    VisionOptionCard(
                      title: '직접 설정할게요',
                      description:
                          '보이지 않는 영역과 정보 위치를 직접 지정할 수 있어요.',
                      icon: Icons.draw_outlined,
                      selected:
                          _selectedType == VisionType.custom,
                      onTap: () {
                        _selectType(
                          VisionType.custom,
                        );
                      },
                    ),

                    const SizedBox(height: 28),

                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: const Color(0xFF111111),
                        borderRadius:
                            BorderRadius.circular(18),
                        border: Border.all(
                          color: Colors.white.withValues(
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
                            color: Color(0xFFF4D35E),
                            size: 23,
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Text(
                              '이 설정은 의료 진단이 아니라 HaloVision 화면을 개인화하기 위한 기능입니다.',
                              style: TextStyle(
                                color: Colors.white.withValues(
                                  alpha: 0.72,
                                ),
                                fontSize: 14,
                                fontWeight: FontWeight.w500,
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
                text: _selectedType == null
                    ? '시야 유형을 선택해주세요'
                    : '다음',
                icon: _selectedType == null
                    ? null
                    : Icons.arrow_forward_rounded,
                onPressed:
                    _selectedType == null ? null : _goNext,
              ),
            ),
          ],
        ),
      ),
    );
  }
}