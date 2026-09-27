import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import '../models/vision_profile.dart';
import '../services/layout_resolver.dart';
import 'document_question_screen.dart';

class DocumentResultScreen extends StatelessWidget {
  final VisionProfile profile;

  const DocumentResultScreen({
    super.key,
    required this.profile,
  });

  static const yellow = Color(0xFFF4D35E);
  static const green = Color(0xFF63C174);
  static const blue = Color(0xFF5AA9FF);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context),

            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(
                  20,
                  12,
                  20,
                  30,
                ),
                child: Column(
                  crossAxisAlignment:
                      CrossAxisAlignment.start,
                  children: [
                    Text(
                      _resultTitle(),
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 28,
                        fontWeight: FontWeight.w900,
                        letterSpacing: -0.7,
                      ),
                    ),

                    const SizedBox(height: 8),

                    Text(
                      _resultDescription(),
                      style: TextStyle(
                        color: Colors.white.withValues(
                          alpha: 0.62,
                        ),
                        fontSize: 14,
                        height: 1.5,
                      ),
                    ),

                    const SizedBox(height: 20),

                    _buildVisionBadge(),

                    const SizedBox(height: 18),

                    _applyVisualAdjustments(
                      child: _buildVisionSpecificResult(
                        context,
                      ),
                    ),

                    const SizedBox(height: 22),

                    SizedBox(
                      width: double.infinity,
                      child: FilledButton.icon(
                        onPressed: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) =>
                                  const DocumentQuestionScreen(),
                            ),
                          );
                        },
                        icon: const Icon(
                          Icons.record_voice_over_rounded,
                        ),
                        label: const Text(
                          '이 문서에 대해 질문하기',
                        ),
                        style: FilledButton.styleFrom(
                          backgroundColor: yellow,
                          foregroundColor: Colors.black,
                          padding:
                              const EdgeInsets.symmetric(
                            vertical: 17,
                          ),
                          textStyle: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(
    BuildContext context,
  ) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        10,
        10,
        14,
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
              '분석 결과',
              style: TextStyle(
                color: Colors.white,
                fontSize: 20,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),

          IconButton(
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) =>
                      const DocumentQuestionScreen(),
                ),
              );
            },
            icon: const Icon(
              Icons.mic_none_rounded,
              color: yellow,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildVisionBadge() {
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: 13,
        vertical: 9,
      ),
      decoration: BoxDecoration(
        color: yellow.withValues(
          alpha: 0.10,
        ),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: yellow.withValues(
            alpha: 0.25,
          ),
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(
            Icons.visibility_outlined,
            color: yellow,
            size: 18,
          ),
          const SizedBox(width: 8),
          Text(
            _visionLabel(),
            style: const TextStyle(
              color: yellow,
              fontSize: 13,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildVisionSpecificResult(
    BuildContext context,
  ) {
    switch (profile.visionType) {
      case VisionType.centralLoss:
        return _buildCentralLossResult(
          context,
        );

      case VisionType.peripheralLoss:
        return _buildPeripheralLossResult(
          context,
        );

      case VisionType.blurred:
        return _buildBlurredResult(
          context,
        );

      case VisionType.colorDifficulty:
        return _buildColorDifficultyResult(
          context,
        );

      case VisionType.unknown:
        return _buildComfortResult(
          context,
        );

      case VisionType.custom:
        return _buildCustomResult(
          context,
        );
    }
  }

  // --------------------------------------------------
  // 1. 중심부 시야
  // --------------------------------------------------

  Widget _buildCentralLossResult(
    BuildContext context,
  ) {
    final resolved =
        LayoutResolver.resolve(
      profile,
    );

    return AspectRatio(
      aspectRatio: 0.72,
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
            decoration: _documentDecoration(),
            clipBehavior: Clip.antiAlias,
            child: Stack(
              children: [
                Positioned(
                  left: 18,
                  top: 16,
                  right: 18,
                  child: Text(
                    '중앙 영역을 피해 배치된 문서',
                    style: _titleStyle(
                      18,
                    ),
                  ),
                ),

                _positionedTextCard(
                  resolved.textRegion,
                  width,
                  height,
                  compact: true,
                ),

                _positionedMediaCard(
                  context,
                  resolved.mediaRegion,
                  width,
                  height,
                ),

                if (profile
                    .avoidRegions.isNotEmpty)
                  _buildBlankAvoidArea(
                    profile
                        .avoidRegions.first,
                    width,
                    height,
                  ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _buildBlankAvoidArea(
    RegionShape region,
    double width,
    double height,
  ) {
    final regionWidth =
        region.width * width;
    final regionHeight =
        region.height * height;

    return Positioned(
      left: region.x * width,
      top: region.y * height,
      width: regionWidth,
      height: regionHeight,
      child: IgnorePointer(
        child: Container(
          decoration: BoxDecoration(
            color: _documentBackgroundColor(),
            shape: region.type ==
                    RegionShapeType.circle
                ? BoxShape.circle
                : BoxShape.rectangle,
            borderRadius: region.type ==
                    RegionShapeType.ellipse
                ? BorderRadius.all(
                    Radius.elliptical(
                      regionWidth,
                      regionHeight,
                    ),
                  )
                : null,
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(
                  alpha: 0.05,
                ),
                blurRadius: 5,
              ),
            ],
          ),
        ),
      ),
    );
  }

  // --------------------------------------------------
  // 2. 주변부 시야
  // --------------------------------------------------

  Widget _buildPeripheralLossResult(
    BuildContext context,
  ) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(24),
      decoration: _documentDecoration(),
      child: Column(
        children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              color: _contentBackgroundColor(),
              borderRadius:
                  BorderRadius.circular(22),
              border: Border.all(
                color: green,
                width: 3,
              ),
            ),
            child: Column(
              children: [
                Text(
                  '중요 정보를 한 영역에 집중',
                  textAlign: TextAlign.center,
                  style: _titleStyle(
                    20,
                  ),
                ),

                const SizedBox(height: 14),

                Text(
                  'HaloVision은 사용자가 잘 볼 수 있는 영역에 핵심 텍스트와 미디어를 모아서 보여줍니다.',
                  textAlign: TextAlign.center,
                  style: _bodyStyle(
                    baseSize: 17,
                  ),
                ),

                const SizedBox(height: 18),

                Row(
                  children: [
                    Expanded(
                      child: _simpleMediaBox(
                        context,
                        icon:
                            Icons.image_outlined,
                        label: '그림',
                        onTap: () {
                          _showFigureDetail(
                            context,
                          );
                        },
                      ),
                    ),

                    const SizedBox(width: 12),

                    Expanded(
                      child: _simpleMediaBox(
                        context,
                        icon:
                            Icons.table_chart_outlined,
                        label: '표',
                        onTap: () {
                          _showTableDetail(
                            context,
                          );
                        },
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),

          const SizedBox(height: 18),

          Text(
            '주변부 시야 설정에서는 정보가 화면 전체에 흩어지지 않도록 주요 내용을 한곳에 모아 보여줍니다.',
            textAlign: TextAlign.center,
            style: TextStyle(
              color: _documentTextColor()
                  .withValues(
                alpha: 0.62,
              ),
              fontSize: 13,
              height: 1.5,
            ),
          ),
        ],
      ),
    );
  }

  // --------------------------------------------------
  // 3. 흐림
  // --------------------------------------------------

  Widget _buildBlurredResult(
    BuildContext context,
  ) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(22),
      decoration: _documentDecoration(),
      child: Column(
        crossAxisAlignment:
            CrossAxisAlignment.start,
        children: [
          Text(
            '읽기 쉬운 큰 글자',
            style: TextStyle(
              color: _documentTextColor(),
              fontSize:
                  27 * profile.fontScale,
              fontWeight: FontWeight.w900,
              height: 1.25,
            ),
          ),

          const SizedBox(height: 20),

          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: _contentBackgroundColor(),
              borderRadius:
                  BorderRadius.circular(18),
              border: Border.all(
                color: _contentBorderColor(),
                width: 2,
              ),
            ),
            child: Text(
              '문서의 내용을 크게 표시하고 글자 간격과 줄 간격을 넓게 구성합니다.\n\n'
              '복잡한 정보는 여러 영역으로 나누어 한 번에 읽어야 하는 양을 줄입니다.',
              style: TextStyle(
                color: _contentTextColor(),
                fontSize:
                    20 * profile.fontScale,
                fontWeight: profile.boldText
                    ? FontWeight.w900
                    : FontWeight.w700,
                height:
                    profile.lineSpacing,
              ),
            ),
          ),

          const SizedBox(height: 18),

          _wideActionCard(
            context,
            icon: Icons.image_outlined,
            title: '그림 크게 보기',
            onTap: () {
              _showFigureDetail(
                context,
              );
            },
          ),

          const SizedBox(height: 12),

          _wideActionCard(
            context,
            icon:
                Icons.table_chart_outlined,
            title: '표 크게 보기',
            onTap: () {
              _showTableDetail(
                context,
              );
            },
          ),
        ],
      ),
    );
  }

  // --------------------------------------------------
  // 4. 색상 구분 어려움
  // --------------------------------------------------

  Widget _buildColorDifficultyResult(
    BuildContext context,
  ) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: profile.colorCorrectionMode ==
                ColorCorrectionMode.highContrast
            ? Colors.black
            : Colors.white,
        borderRadius:
            BorderRadius.circular(24),
        border: Border.all(
          color: Colors.white.withValues(
            alpha: 0.10,
          ),
        ),
      ),
      child: Column(
        crossAxisAlignment:
            CrossAxisAlignment.start,
        children: [
          Text(
            '색상만으로 정보를 구분하지 않습니다.',
            style: _titleStyle(
              21,
            ),
          ),

          const SizedBox(height: 12),

          Text(
            '색 대신 아이콘, 글자, 굵은 테두리를 함께 사용해 내용을 구분합니다.',
            style: _bodyStyle(
              baseSize: 16,
            ),
          ),

          const SizedBox(height: 20),

          _symbolInformationCard(
            icon:
                Icons.article_outlined,
            label: 'TEXT',
            description:
                '문서 본문',
          ),

          const SizedBox(height: 12),

          GestureDetector(
            onTap: () {
              _showFigureDetail(
                context,
              );
            },
            child: _symbolInformationCard(
              icon:
                  Icons.image_outlined,
              label: 'FIGURE',
              description:
                  '문서 그림',
            ),
          ),

          const SizedBox(height: 12),

          GestureDetector(
            onTap: () {
              _showTableDetail(
                context,
              );
            },
            child: _symbolInformationCard(
              icon:
                  Icons.table_chart_outlined,
              label: 'TABLE',
              description:
                  '문서 표',
            ),
          ),
        ],
      ),
    );
  }

  // --------------------------------------------------
  // 5. 잘 모르겠어요
  // --------------------------------------------------

  Widget _buildComfortResult(
    BuildContext context,
  ) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(22),
      decoration: _documentDecoration(),
      child: Column(
        crossAxisAlignment:
            CrossAxisAlignment.start,
        children: [
          Text(
            '보기 편한 문서 화면',
            style: _titleStyle(
              22,
            ),
          ),

          const SizedBox(height: 14),

          Container(
            width: double.infinity,
            padding:
                const EdgeInsets.all(18),
            decoration: BoxDecoration(
              color: _contentBackgroundColor(),
              borderRadius:
                  BorderRadius.circular(18),
            ),
            child: Text(
              'HaloVision은 선택한 보기 설정을 기준으로 글자 크기와 간격, 대비를 조정합니다.',
              style: _bodyStyle(
                baseSize: 18,
              ),
            ),
          ),

          const SizedBox(height: 16),

          Row(
            children: [
              Expanded(
                child: _simpleMediaBox(
                  context,
                  icon:
                      Icons.image_outlined,
                  label: '그림',
                  onTap: () {
                    _showFigureDetail(
                      context,
                    );
                  },
                ),
              ),

              const SizedBox(width: 12),

              Expanded(
                child: _simpleMediaBox(
                  context,
                  icon: Icons
                      .table_chart_outlined,
                  label: '표',
                  onTap: () {
                    _showTableDetail(
                      context,
                    );
                  },
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  // --------------------------------------------------
  // 6. 직접 설정
  // --------------------------------------------------

  Widget _buildCustomResult(
    BuildContext context,
  ) {
    final resolved =
        LayoutResolver.resolve(
      profile,
    );

    return AspectRatio(
      aspectRatio: 0.72,
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
            decoration:
                _documentDecoration(),
            clipBehavior:
                Clip.antiAlias,
            child: Stack(
              children: [
                Positioned(
                  left: 18,
                  top: 16,
                  right: 18,
                  child: Text(
                    '내가 지정한 문서 배치',
                    style: _titleStyle(
                      18,
                    ),
                  ),
                ),

                _positionedTextCard(
                  resolved.textRegion,
                  width,
                  height,
                  compact: true,
                ),

                _positionedMediaCard(
                  context,
                  resolved.mediaRegion,
                  width,
                  height,
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  // --------------------------------------------------
  // 공통 배치 위젯
  // --------------------------------------------------

  Widget _positionedTextCard(
    LayoutRegion region,
    double width,
    double height, {
    required bool compact,
  }) {
    return Positioned(
      left: region.x * width,
      top: region.y * height,
      width: region.width * width,
      height: region.height * height,
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: _contentBackgroundColor(),
          borderRadius:
              BorderRadius.circular(16),
          border: Border.all(
            color: blue,
            width: 2,
          ),
        ),
        child: SingleChildScrollView(
          child: Text(
            'HaloVision은 문서의 텍스트, 그림, 표를 구분하고 사용자의 시야 특성에 맞게 다시 배치합니다.\n\n'
            '현재 화면은 실제 분석 서버를 연결하기 전 테스트용 문서 결과입니다.',
            style: TextStyle(
              color: _contentTextColor(),
              fontSize:
                  (compact ? 14 : 17) *
                      profile.fontScale,
              fontWeight: profile.boldText
                  ? FontWeight.w900
                  : FontWeight.w600,
              height:
                  profile.lineSpacing,
            ),
          ),
        ),
      ),
    );
  }

  Widget _positionedMediaCard(
    BuildContext context,
    LayoutRegion region,
    double width,
    double height,
  ) {
    return Positioned(
      left: region.x * width,
      top: region.y * height,
      width: region.width * width,
      height: region.height * height,
      child: Column(
        children: [
          Expanded(
            child: GestureDetector(
              onTap: () {
                _showFigureDetail(
                  context,
                );
              },
              child: Container(
                width: double.infinity,
                decoration: BoxDecoration(
                  color: const Color(
                    0xFFF0F0F0,
                  ),
                  borderRadius:
                      BorderRadius.circular(14),
                  border: Border.all(
                    color: green,
                    width: 2,
                  ),
                ),
                child: const Icon(
                  Icons.image_outlined,
                  color: Colors.black54,
                  size: 44,
                ),
              ),
            ),
          ),

          const SizedBox(height: 8),

          Expanded(
            child: GestureDetector(
              onTap: () {
                _showTableDetail(
                  context,
                );
              },
              child: Container(
                width: double.infinity,
                padding:
                    const EdgeInsets.all(6),
                decoration: BoxDecoration(
                  color: const Color(
                    0xFFF0F0F0,
                  ),
                  borderRadius:
                      BorderRadius.circular(14),
                  border: Border.all(
                    color: green,
                    width: 2,
                  ),
                ),
                child: const _MockTable(
                  compact: true,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _simpleMediaBox(
    BuildContext context, {
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius:
          BorderRadius.circular(18),
      child: Container(
        height: 125,
        decoration: BoxDecoration(
          color: const Color(
            0xFFF0F0F0,
          ),
          borderRadius:
              BorderRadius.circular(18),
          border: Border.all(
            color: Colors.black26,
            width: 2,
          ),
        ),
        child: Column(
          mainAxisAlignment:
              MainAxisAlignment.center,
          children: [
            Icon(
              icon,
              color: Colors.black87,
              size: 40,
            ),
            const SizedBox(height: 8),
            Text(
              label,
              style: const TextStyle(
                color: Colors.black87,
                fontWeight:
                    FontWeight.w800,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _wideActionCard(
    BuildContext context, {
    required IconData icon,
    required String title,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius:
          BorderRadius.circular(18),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          color: _contentBackgroundColor(),
          borderRadius:
              BorderRadius.circular(18),
          border: Border.all(
            color: _contentBorderColor(),
            width: 2,
          ),
        ),
        child: Row(
          children: [
            Icon(
              icon,
              color:
                  _contentTextColor(),
              size: 34,
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Text(
                title,
                style: TextStyle(
                  color:
                      _contentTextColor(),
                  fontSize: 17,
                  fontWeight:
                      FontWeight.w800,
                ),
              ),
            ),
            Icon(
              Icons.chevron_right_rounded,
              color:
                  _contentTextColor(),
            ),
          ],
        ),
      ),
    );
  }

  Widget _symbolInformationCard({
    required IconData icon,
    required String label,
    required String description,
  }) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(17),
      decoration: BoxDecoration(
        color: _contentBackgroundColor(),
        borderRadius:
            BorderRadius.circular(16),
        border: Border.all(
          color: _contentTextColor(),
          width: 2.5,
        ),
      ),
      child: Row(
        children: [
          Icon(
            icon,
            color: _contentTextColor(),
            size: 34,
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment:
                  CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: TextStyle(
                    color:
                        _contentTextColor(),
                    fontWeight:
                        FontWeight.w900,
                    fontSize: 14,
                    letterSpacing: 1.0,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  description,
                  style: TextStyle(
                    color:
                        _contentTextColor(),
                    fontWeight:
                        FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // --------------------------------------------------
  // 상세 보기
  // --------------------------------------------------

  void _showFigureDetail(
    BuildContext context,
  ) {
    _showDetailSheet(
      context,
      title: '그림 상세 보기',
      child: const Icon(
        Icons.image_outlined,
        size: 110,
        color: Colors.black45,
      ),
    );
  }

  void _showTableDetail(
    BuildContext context,
  ) {
    _showDetailSheet(
      context,
      title: '표 상세 보기',
      child: const _MockTable(
        compact: false,
      ),
    );
  }

  void _showDetailSheet(
    BuildContext context, {
    required String title,
    required Widget child,
  }) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor:
          const Color(0xFF111111),
      builder: (
        sheetContext,
      ) {
        return SafeArea(
          child: Padding(
            padding:
                const EdgeInsets.all(24),
            child: Column(
              mainAxisSize:
                  MainAxisSize.min,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        title,
                        style:
                            const TextStyle(
                          color:
                              Colors.white,
                          fontSize: 20,
                          fontWeight:
                              FontWeight.w900,
                        ),
                      ),
                    ),
                    IconButton(
                      onPressed: () {
                        Navigator.pop(
                          sheetContext,
                        );
                      },
                      icon: const Icon(
                        Icons.close_rounded,
                        color:
                            Colors.white,
                      ),
                    ),
                  ],
                ),

                const SizedBox(height: 18),

                Container(
                  width: double.infinity,
                  constraints:
                      const BoxConstraints(
                    minHeight: 240,
                    maxHeight: 460,
                  ),
                  padding:
                      const EdgeInsets.all(
                    20,
                  ),
                  decoration:
                      BoxDecoration(
                    color: const Color(
                      0xFFF2F2F2,
                    ),
                    borderRadius:
                        BorderRadius.circular(
                      20,
                    ),
                  ),
                  child: Center(
                    child: child,
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  // --------------------------------------------------
  // 시각 보정
  // --------------------------------------------------

  Widget _applyVisualAdjustments({
    required Widget child,
  }) {
    final brightness =
        profile.brightness;

    final sharpness =
        profile.sharpness;

    double contrast =
        profile.contrast;

    if (sharpness > 1.0) {
      contrast *=
          1.0 +
          ((sharpness - 1.0) *
              0.18);
    }

    final contrastOffset =
        128.0 *
        (1.0 - contrast);

    Widget result =
        ColorFiltered(
      colorFilter:
          ColorFilter.matrix(
        [
          contrast,
          0,
          0,
          0,
          contrastOffset,
          0,
          contrast,
          0,
          0,
          contrastOffset,
          0,
          0,
          contrast,
          0,
          contrastOffset,
          0,
          0,
          0,
          1,
          0,
        ],
      ),
      child: child,
    );

    result = ColorFiltered(
      colorFilter:
          ColorFilter.matrix(
        [
          brightness,
          0,
          0,
          0,
          0,
          0,
          brightness,
          0,
          0,
          0,
          0,
          0,
          brightness,
          0,
          0,
          0,
          0,
          0,
          1,
          0,
        ],
      ),
      child: result,
    );

    if (sharpness < 1.0) {
      final blurAmount =
          (1.0 - sharpness) *
          4.0;

      result = ImageFiltered(
        imageFilter:
            ui.ImageFilter.blur(
          sigmaX: blurAmount,
          sigmaY: blurAmount,
        ),
        child: result,
      );
    }

    return result;
  }

  // --------------------------------------------------
  // 스타일
  // --------------------------------------------------

  BoxDecoration _documentDecoration() {
    return BoxDecoration(
      color:
          _documentBackgroundColor(),
      borderRadius:
          BorderRadius.circular(24),
      border: Border.all(
        color: Colors.white.withValues(
          alpha: 0.10,
        ),
      ),
    );
  }

  TextStyle _titleStyle(
    double size,
  ) {
    return TextStyle(
      color: _documentTextColor(),
      fontSize:
          size * profile.fontScale,
      fontWeight: profile.boldText
          ? FontWeight.w900
          : FontWeight.w800,
      height: profile.lineSpacing,
    );
  }

  TextStyle _bodyStyle({
    required double baseSize,
  }) {
    return TextStyle(
      color: _contentTextColor(),
      fontSize:
          baseSize *
          profile.fontScale,
      fontWeight: profile.boldText
          ? FontWeight.w900
          : FontWeight.w600,
      height:
          profile.lineSpacing,
    );
  }

  Color _documentBackgroundColor() {
    if (profile.colorCorrectionMode ==
        ColorCorrectionMode
            .highContrast) {
      return Colors.black;
    }

    return const Color(
      0xFFF4F4F4,
    );
  }

  Color _documentTextColor() {
    if (profile.colorCorrectionMode ==
        ColorCorrectionMode
            .highContrast) {
      return Colors.white;
    }

    return Colors.black87;
  }

  Color _contentBackgroundColor() {
    switch (
        profile.colorCorrectionMode) {
      case ColorCorrectionMode
            .highContrast:
        return Colors.black;

      case ColorCorrectionMode
            .redGreenAssist:
        return const Color(
          0xFFFFF7E6,
        );

      case ColorCorrectionMode
            .blueYellowAssist:
        return const Color(
          0xFFF2F5FF,
        );

      case ColorCorrectionMode.normal:
        return Colors.white;
    }
  }

  Color _contentTextColor() {
    switch (
        profile.colorCorrectionMode) {
      case ColorCorrectionMode
            .highContrast:
        return Colors.white;

      case ColorCorrectionMode
            .redGreenAssist:
      case ColorCorrectionMode
            .blueYellowAssist:
      case ColorCorrectionMode.normal:
        return Colors.black87;
    }
  }

  Color _contentBorderColor() {
    switch (
        profile.colorCorrectionMode) {
      case ColorCorrectionMode
            .highContrast:
        return Colors.white;

      case ColorCorrectionMode
            .redGreenAssist:
        return const Color(
          0xFFE7A84E,
        );

      case ColorCorrectionMode
            .blueYellowAssist:
        return const Color(
          0xFF7086CE,
        );

      case ColorCorrectionMode.normal:
        return blue;
    }
  }

  String _visionLabel() {
    switch (profile.visionType) {
      case VisionType.centralLoss:
        return '중심부 시야 맞춤';

      case VisionType.peripheralLoss:
        return '주변부 시야 맞춤';

      case VisionType.blurred:
        return '흐림 보정 적용';

      case VisionType.colorDifficulty:
        return '색상 구분 보조 적용';

      case VisionType.unknown:
        return '보기 편한 화면 적용';

      case VisionType.custom:
        return '직접 설정 적용';
    }
  }

  String _resultTitle() {
    switch (profile.visionType) {
      case VisionType.centralLoss:
        return '중앙을 피해\n정보를 배치했어요.';

      case VisionType.peripheralLoss:
        return '잘 보이는 곳에\n정보를 모았어요.';

      case VisionType.blurred:
        return '더 크고 선명하게\n문서를 구성했어요.';

      case VisionType.colorDifficulty:
        return '색에 의존하지 않도록\n문서를 바꿨어요.';

      case VisionType.unknown:
        return '보기 편한 형태로\n문서를 정리했어요.';

      case VisionType.custom:
        return '설정한 위치에 맞춰\n문서를 배치했어요.';
    }
  }

  String _resultDescription() {
    switch (profile.visionType) {
      case VisionType.centralLoss:
        return '설정한 회피 영역을 피해 텍스트와 그림·표를 배치합니다.';

      case VisionType.peripheralLoss:
        return '잘 보이는 영역에 핵심 정보를 집중해서 표시합니다.';

      case VisionType.blurred:
        return '큰 글씨와 넓은 간격, 디스플레이 보정을 적용합니다.';

      case VisionType.colorDifficulty:
        return '색상뿐 아니라 아이콘과 텍스트, 테두리를 함께 사용합니다.';

      case VisionType.unknown:
        return '선택한 보기 편한 화면 설정을 문서에 적용합니다.';

      case VisionType.custom:
        return '직접 설정한 시야 영역과 정보 위치를 문서에 적용합니다.';
    }
  }
}

class _MockTable
    extends StatelessWidget {
  final bool compact;

  const _MockTable({
    required this.compact,
  });

  @override
  Widget build(
    BuildContext context,
  ) {
    final fontSize =
        compact ? 9.0 : 14.0;

    final padding =
        compact ? 3.0 : 11.0;

    return Table(
      border: TableBorder.all(
        color: Colors.black38,
      ),
      children: [
        TableRow(
          decoration:
              const BoxDecoration(
            color:
                Color(0xFFE0E0E0),
          ),
          children: [
            _TableCell(
              text: '항목',
              fontSize: fontSize,
              padding: padding,
              bold: true,
            ),
            _TableCell(
              text: '결과',
              fontSize: fontSize,
              padding: padding,
              bold: true,
            ),
          ],
        ),

        TableRow(
          children: [
            _TableCell(
              text: '텍스트',
              fontSize: fontSize,
              padding: padding,
            ),
            _TableCell(
              text: '감지',
              fontSize: fontSize,
              padding: padding,
            ),
          ],
        ),

        TableRow(
          children: [
            _TableCell(
              text: '그림',
              fontSize: fontSize,
              padding: padding,
            ),
            _TableCell(
              text: '1개',
              fontSize: fontSize,
              padding: padding,
            ),
          ],
        ),

        TableRow(
          children: [
            _TableCell(
              text: '표',
              fontSize: fontSize,
              padding: padding,
            ),
            _TableCell(
              text: '1개',
              fontSize: fontSize,
              padding: padding,
            ),
          ],
        ),
      ],
    );
  }
}

class _TableCell
    extends StatelessWidget {
  final String text;
  final double fontSize;
  final double padding;
  final bool bold;

  const _TableCell({
    required this.text,
    required this.fontSize,
    required this.padding,
    this.bold = false,
  });

  @override
  Widget build(
    BuildContext context,
  ) {
    return Padding(
      padding:
          EdgeInsets.all(
        padding,
      ),
      child: Text(
        text,
        textAlign:
            TextAlign.center,
        style: TextStyle(
          color: Colors.black87,
          fontSize: fontSize,
          fontWeight: bold
              ? FontWeight.w800
              : FontWeight.w500,
        ),
      ),
    );
  }
}