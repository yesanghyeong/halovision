import 'package:flutter/material.dart';

import '../widgets/halo_primary_button.dart';
import 'vision_select_screen.dart';

class OnboardingScreen extends StatelessWidget {
  const OnboardingScreen({super.key});

  @override
  Widget build(BuildContext context) {
    const yellow = Color(0xFFF4D35E);

    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 24, 24, 28),
          child: Column(
            children: [
              const Spacer(),

              Container(
                width: 108,
                height: 108,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: yellow.withValues(alpha: 0.12),
                  border: Border.all(
                    color: yellow.withValues(alpha: 0.55),
                    width: 1.5,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: yellow.withValues(alpha: 0.14),
                      blurRadius: 28,
                      spreadRadius: 3,
                    ),
                  ],
                ),
                child: const Icon(
                  Icons.visibility_rounded,
                  color: yellow,
                  size: 54,
                ),
              ),

              const SizedBox(height: 34),

              const Text(
                'HaloVision',
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 36,
                  fontWeight: FontWeight.w900,
                  letterSpacing: -1.0,
                ),
              ),

              const SizedBox(height: 18),

              const Text(
                '내 시야에 맞게\n문서를 다시 보여드립니다.',
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 25,
                  fontWeight: FontWeight.w800,
                  height: 1.35,
                  letterSpacing: -0.5,
                ),
              ),

              const SizedBox(height: 18),

              Text(
                '시야 특성에 따라 문서의 텍스트, 표, 그림을\n보기 편한 위치와 형태로 조정할 수 있어요.',
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: Colors.white.withValues(alpha: 0.67),
                  fontSize: 16,
                  fontWeight: FontWeight.w500,
                  height: 1.55,
                ),
              ),

              const Spacer(),

              Container(
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  color: const Color(0xFF111111),
                  borderRadius: BorderRadius.circular(22),
                  border: Border.all(
                    color: Colors.white.withValues(alpha: 0.08),
                  ),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(
                      Icons.tune_rounded,
                      color: yellow,
                      size: 26,
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Text(
                        '몇 가지 간단한 설정을 통해 나에게 맞는 화면을 만들어볼게요.',
                        style: TextStyle(
                          color: Colors.white.withValues(alpha: 0.82),
                          fontSize: 15,
                          fontWeight: FontWeight.w600,
                          height: 1.5,
                        ),
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 22),

              HaloPrimaryButton(
                text: '시작하기',
                icon: Icons.arrow_forward_rounded,
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => const VisionSelectScreen(),
                    ),
                  );
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}