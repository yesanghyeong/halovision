import 'dart:io';

import 'package:flutter/material.dart';

import '../models/vision_profile.dart';
import 'document_result_screen.dart';

class DocumentAnalysisScreen extends StatelessWidget {
  final String imagePath;
  final VisionProfile profile;

  const DocumentAnalysisScreen({
    super.key,
    required this.imagePath,
    required this.profile,
  });

  String _visionTypeLabel() {
    switch (profile.visionType) {
      case VisionType.centralLoss:
        return '중심부 시야 설정';

      case VisionType.peripheralLoss:
        return '주변부 시야 설정';

      case VisionType.blurred:
        return '흐림 보정 설정';

      case VisionType.colorDifficulty:
        return '색상 보정 설정';

      case VisionType.unknown:
        return '보기 편한 화면 설정';

      case VisionType.custom:
        return '직접 설정';
    }
  }

  @override
  Widget build(BuildContext context) {
    const yellow = Color(0xFFF4D35E);

    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(
                10,
                10,
                18,
                8,
              ),
              child: Row(
                children: [
                  IconButton(
                    onPressed: () {
                      Navigator.pop(context);
                    },
                    icon: const Icon(
                      Icons.arrow_back_rounded,
                      color: Colors.white,
                      size: 28,
                    ),
                  ),
                  const SizedBox(width: 4),
                  const Expanded(
                    child: Text(
                      '문서 분석',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 20,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  const Icon(
                    Icons.auto_awesome_rounded,
                    color: yellow,
                    size: 25,
                  ),
                ],
              ),
            ),

            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(
                  24,
                  20,
                  24,
                  30,
                ),
                child: Column(
                  crossAxisAlignment:
                      CrossAxisAlignment.start,
                  children: [
                    const Text(
                      '문서를 분석할 준비가\n완료되었습니다.',
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
                      '문서의 텍스트, 그림, 표를 분석한 뒤 현재 시야 설정에 맞게 다시 배치합니다.',
                      style: TextStyle(
                        color: Colors.white.withValues(
                          alpha: 0.65,
                        ),
                        fontSize: 14,
                        height: 1.5,
                      ),
                    ),

                    const SizedBox(height: 24),

                    AspectRatio(
                      aspectRatio: 0.78,
                      child: Container(
                        width: double.infinity,
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius:
                              BorderRadius.circular(22),
                        ),
                        clipBehavior: Clip.antiAlias,
                        child: Image.file(
                          File(imagePath),
                          fit: BoxFit.contain,
                        ),
                      ),
                    ),

                    const SizedBox(height: 20),

                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(17),
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
                      child: Column(
                        crossAxisAlignment:
                            CrossAxisAlignment.start,
                        children: [
                          const Text(
                            '적용할 시야 설정',
                            style: TextStyle(
                              color: Colors.white70,
                              fontSize: 13,
                              fontWeight: FontWeight.w700,
                            ),
                          ),

                          const SizedBox(height: 8),

                          Text(
                            _visionTypeLabel(),
                            style: const TextStyle(
                              color: yellow,
                              fontSize: 19,
                              fontWeight: FontWeight.w900,
                            ),
                          ),

                          const SizedBox(height: 12),

                          _InfoRow(
                            title: '글자 크기',
                            value:
                                '${(profile.fontScale * 100).round()}%',
                          ),

                          const SizedBox(height: 8),

                          _InfoRow(
                            title: '줄 간격',
                            value: profile.lineSpacing
                                .toStringAsFixed(1),
                          ),

                          const SizedBox(height: 8),

                          _InfoRow(
                            title: '굵은 글씨',
                            value: profile.boldText
                                ? '사용'
                                : '사용 안 함',
                          ),
                        ],
                      ),
                    ),

                    const SizedBox(height: 18),

                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: yellow.withValues(
                          alpha: 0.08,
                        ),
                        borderRadius:
                            BorderRadius.circular(18),
                        border: Border.all(
                          color: yellow.withValues(
                            alpha: 0.20,
                          ),
                        ),
                      ),
                      child: Row(
                        crossAxisAlignment:
                            CrossAxisAlignment.start,
                        children: [
                          const Icon(
                            Icons.info_outline_rounded,
                            color: yellow,
                          ),

                          const SizedBox(width: 12),

                          Expanded(
                            child: Text(
                              '다음 단계에서 FastAPI 서버와 연결해 실제 OCR 및 문서 구조 분석을 수행할 예정입니다.',
                              style: TextStyle(
                                color: Colors.white.withValues(
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

            Padding(
              padding: const EdgeInsets.fromLTRB(
                24,
                12,
                24,
                22,
              ),
              child: SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => DocumentResultScreen(
                          profile: profile,
                        ),
                      ),
                    );
                  },
                  icon: const Icon(
                    Icons.auto_awesome_rounded,
                  ),
                  label: const Text(
                    '문서 분석 시작',
                  ),
                  style: FilledButton.styleFrom(
                    backgroundColor: yellow,
                    foregroundColor: Colors.black,
                    padding: const EdgeInsets.symmetric(
                      vertical: 17,
                    ),
                    textStyle: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  final String title;
  final String value;

  const _InfoRow({
    required this.title,
    required this.value,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(
            title,
            style: TextStyle(
              color: Colors.white.withValues(
                alpha: 0.58,
              ),
              fontSize: 13,
            ),
          ),
        ),
        Text(
          value,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 13,
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    );
  }
}