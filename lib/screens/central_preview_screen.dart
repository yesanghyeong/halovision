import 'package:flutter/material.dart';



import '../models/vision_profile.dart';

import '../services/vision_profile_storage.dart';

import '../widgets/halo_primary_button.dart';

import 'home_screen.dart';



class CentralPreviewScreen extends StatelessWidget {

  final VisionProfile profile;

  final String stepText;



  const CentralPreviewScreen({

    super.key,

    required this.profile,

    this.stepText = '4 / 4',

  });



  Future<void> _completeSetup(

    BuildContext context,

  ) async {

    await VisionProfileStorage.saveProfile(

      profile,

    );



    if (!context.mounted) {

      return;

    }



    Navigator.of(context).pushAndRemoveUntil(

      MaterialPageRoute(

        builder: (_) => const HomeScreen(),

      ),

      (route) => false,

    );

  }



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



  String _regionTitle() {

    if (profile.visionType ==

        VisionType.peripheralLoss) {

      return '잘 보이는 영역';

    }



    if (profile.visionType ==

        VisionType.custom) {

      if (profile.visibleRegions.isNotEmpty) {

        return '잘 보이는 영역';

      }



      if (profile.avoidRegions.isNotEmpty) {

        return '시야 회피 영역';

      }



      return '시야 영역';

    }



    if (profile.visionType ==

            VisionType.blurred ||

        profile.visionType ==

            VisionType.colorDifficulty ||

        profile.visionType ==

            VisionType.unknown) {

      return '시야 영역';

    }



    return '시야 회피 영역';

  }



  String _regionDescription() {

    if (profile.visionType ==

        VisionType.peripheralLoss) {

      if (profile.visibleRegions.isEmpty) {

        return '설정된 잘 보이는 영역이 없습니다.';

      }



      final type =

          profile.visibleRegions.first.type;



      switch (type) {

        case RegionShapeType.circle:

          return '원형 영역 안쪽에 정보를 우선 배치합니다.';



        case RegionShapeType.ellipse:

          return '타원형 영역 안쪽에 정보를 우선 배치합니다.';



        case RegionShapeType.custom:

          return '직접 지정한 영역 안쪽에 정보를 우선 배치합니다.';



        case RegionShapeType.rectangle:

          return '설정한 영역 안쪽에 정보를 우선 배치합니다.';

      }

    }



    if (profile.visionType ==

        VisionType.custom) {

      if (profile.visibleRegions.isNotEmpty) {

        final type =

            profile.visibleRegions.first.type;



        switch (type) {

          case RegionShapeType.circle:

            return '직접 지정한 원형 영역 안쪽에 정보를 우선 배치합니다.';



          case RegionShapeType.ellipse:

            return '직접 지정한 타원형 영역 안쪽에 정보를 우선 배치합니다.';



          case RegionShapeType.custom:

            return '직접 그린 잘 보이는 영역 안쪽에 정보를 우선 배치합니다.';



          case RegionShapeType.rectangle:

            return '직접 지정한 영역 안쪽에 정보를 우선 배치합니다.';

        }

      }



      if (profile.avoidRegions.isNotEmpty) {

        final type =

            profile.avoidRegions.first.type;



        switch (type) {

          case RegionShapeType.circle:

            return '직접 지정한 원형 영역을 피해 정보를 배치합니다.';



          case RegionShapeType.ellipse:

            return '직접 지정한 타원형 영역을 피해 정보를 배치합니다.';



          case RegionShapeType.custom:

            return '직접 그린 잘 보이지 않는 영역을 피해 정보를 배치합니다.';



          case RegionShapeType.rectangle:

            return '직접 지정한 영역을 피해 정보를 배치합니다.';

        }

      }



      return '직접 설정된 시야 영역이 없습니다.';

    }



    if (profile.visionType ==

            VisionType.blurred ||

        profile.visionType ==

            VisionType.colorDifficulty ||

        profile.visionType ==

            VisionType.unknown) {

      return '별도의 시야 영역을 지정하지 않고 디스플레이 설정을 적용합니다.';

    }



    if (profile.avoidRegions.isEmpty) {

      return '설정된 회피 영역이 없습니다.';

    }



    final type =

        profile.avoidRegions.first.type;



    switch (type) {

      case RegionShapeType.circle:

        return '원형의 잘 보이지 않는 영역을 피해 정보를 배치합니다.';



      case RegionShapeType.ellipse:

        return '타원형의 잘 보이지 않는 영역을 피해 정보를 배치합니다.';



      case RegionShapeType.custom:

        return '직접 그린 잘 보이지 않는 영역을 피해 정보를 배치합니다.';



      case RegionShapeType.rectangle:

        return '설정한 영역을 피해 정보를 배치합니다.';

    }

  }



  String _colorModeLabel() {

    switch (profile.colorCorrectionMode) {

      case ColorCorrectionMode.normal:

        return '기본';



      case ColorCorrectionMode.highContrast:

        return '고대비';



      case ColorCorrectionMode.redGreenAssist:

        return '적·녹색 구분 보조';



      case ColorCorrectionMode.blueYellowAssist:

        return '청·황색 구분 보조';

    }

  }



  Color _regionColor() {

    if (profile.visibleRegions.isNotEmpty) {

      return const Color(

        0xFF63C174,

      );

    }



    return const Color(

      0xFFF4D35E,

    );

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

            _buildHeader(

              context,

            ),



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

                      '설정된 화면을\n확인해주세요.',

                      style: TextStyle(

                        color:

                            Colors.white,

                        fontSize: 29,

                        fontWeight:

                            FontWeight.w900,

                        height: 1.25,

                        letterSpacing:

                            -0.7,

                      ),

                    ),



                    const SizedBox(

                      height: 12,

                    ),



                    Text(

                      '시야 영역, 정보 배치, 디스플레이 설정을 최종 확인할 수 있어요.',

                      style: TextStyle(

                        color: Colors.white

                            .withValues(

                          alpha: 0.65,

                        ),

                        fontSize: 15,

                        height: 1.5,

                      ),

                    ),



                    const SizedBox(

                      height: 24,

                    ),



                    _buildPreview(),



                    const SizedBox(

                      height: 22,

                    ),



                    Container(

                      width:

                          double.infinity,

                      padding:

                          const EdgeInsets.all(

                        17,

                      ),

                      decoration:

                          BoxDecoration(

                        color:

                            const Color(

                          0xFF111111,

                        ),

                        borderRadius:

                            BorderRadius.circular(

                          18,

                        ),

                        border:

                            Border.all(

                          color: Colors

                              .white

                              .withValues(

                            alpha:

                                0.08,

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

                                    .check_circle_outline_rounded,

                                color:

                                    yellow,

                                size: 24,

                              ),

                              SizedBox(

                                width: 10,

                              ),

                              Text(

                                '현재 설정',

                                style:

                                    TextStyle(

                                  color:

                                      Colors.white,

                                  fontSize:

                                      17,

                                  fontWeight:

                                      FontWeight.w800,

                                ),

                              ),

                            ],

                          ),



                          const SizedBox(

                            height: 16,

                          ),



                          _SummaryItem(

                            color: yellow,

                            title: '시야 유형',

                            description:

                                _visionTypeLabel(),

                          ),



                          const SizedBox(

                            height: 12,

                          ),



                          _SummaryItem(

                            color:

                                _regionColor(),

                            title:

                                _regionTitle(),

                            description:

                                _regionDescription(),

                          ),



                          if (profile

                                  .textRegion !=

                              null) ...[

                            const SizedBox(

                              height: 12,

                            ),

                            const _SummaryItem(

                              color:

                                  Color(

                                0xFF5AA9FF,

                              ),

                              title:

                                  '텍스트 영역',

                              description:

                                  '파란 상자 위치에 텍스트를 우선 배치합니다.',

                            ),

                          ],



                          if (profile

                                  .mediaRegion !=

                              null) ...[

                            const SizedBox(

                              height: 12,

                            ),

                            const _SummaryItem(

                              color:

                                  Color(

                                0xFF63C174,

                              ),

                              title:

                                  '그림 · 표 영역',

                              description:

                                  '초록 상자 위치에 그림과 표를 우선 배치합니다.',

                            ),

                          ],



                          const SizedBox(

                            height: 16,

                          ),



                          Divider(

                            color: Colors

                                .white

                                .withValues(

                              alpha:

                                  0.08,

                            ),

                          ),



                          const SizedBox(

                            height: 12,

                          ),



                          _DisplaySummaryRow(

                            title:

                                '글자 크기',

                            value:

                                '${(profile.fontScale * 100).round()}%',

                          ),



                          const SizedBox(

                            height: 10,

                          ),



                          _DisplaySummaryRow(

                            title: '줄 간격',

                            value: profile

                                .lineSpacing

                                .toStringAsFixed(

                              1,

                            ),

                          ),



                          const SizedBox(

                            height: 10,

                          ),



                          _DisplaySummaryRow(

                            title:

                                '굵은 글씨',

                            value:

                                profile.boldText

                                    ? '사용'

                                    : '사용 안 함',

                          ),



                          const SizedBox(

                            height: 10,

                          ),



                          _DisplaySummaryRow(

                            title: '밝기',

                            value:

                                '${(profile.brightness * 100).round()}%',

                          ),



                          const SizedBox(

                            height: 10,

                          ),



                          _DisplaySummaryRow(

                            title: '대비',

                            value:

                                '${(profile.contrast * 100).round()}%',

                          ),



                          const SizedBox(

                            height: 10,

                          ),



                          _DisplaySummaryRow(

                            title: '선명도',

                            value:

                                '${(profile.sharpness * 100).round()}%',

                          ),



                          const SizedBox(

                            height: 10,

                          ),



                          _DisplaySummaryRow(

                            title:

                                '색상 보정',

                            value:

                                _colorModeLabel(),

                          ),

                        ],

                      ),

                    ),



                    const SizedBox(

                      height: 18,

                    ),



                    Container(

                      width:

                          double.infinity,

                      padding:

                          const EdgeInsets.all(

                        16,

                      ),

                      decoration:

                          BoxDecoration(

                        color: yellow

                            .withValues(

                          alpha:

                              0.08,

                        ),

                        borderRadius:

                            BorderRadius.circular(

                          18,

                        ),

                        border:

                            Border.all(

                          color: yellow

                              .withValues(

                            alpha:

                                0.22,

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

                            color:

                                yellow,

                            size: 23,

                          ),

                          const SizedBox(

                            width: 12,

                          ),

                          Expanded(

                            child: Text(

                              '실제 문서에서는 문서 내용과 크기에 따라 설정값을 기준으로 배치를 자동 조정할 수 있습니다.',

                              style: TextStyle(

                                color: Colors

                                    .white

                                    .withValues(

                                  alpha:

                                      0.78,

                                ),

                                fontSize:

                                    14,

                                height:

                                    1.5,

                              ),

                            ),

                          ),

                        ],

                      ),

                    ),



                    const SizedBox(

                      height: 10,

                    ),



                    SizedBox(

                      width:

                          double.infinity,

                      child:

                          TextButton.icon(

                        onPressed: () {

                          Navigator.pop(

                            context,

                          );

                        },

                        icon:

                            const Icon(

                          Icons

                              .tune_rounded,

                        ),

                        label:

                            const Text(

                          '이전 단계에서 다시 조정',

                        ),

                        style: TextButton

                            .styleFrom(

                          foregroundColor:

                              Colors.white,

                          padding:

                              const EdgeInsets

                                  .symmetric(

                            vertical:

                                15,

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

              decoration:

                  BoxDecoration(

                color: Colors.black,

                border: Border(

                  top: BorderSide(

                    color: Colors

                        .white

                        .withValues(

                      alpha: 0.07,

                    ),

                  ),

                ),

              ),

              child:

                  HaloPrimaryButton(

                text: '설정 완료 · 홈으로',

                icon:

                    Icons.check_rounded,

                onPressed: () {

                  _completeSetup(

                    context,

                  );

                },

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

              Navigator.pop(

                context,

              );

            },

            tooltip: '뒤로가기',

            icon: const Icon(

              Icons.arrow_back_rounded,

              color:

                  Colors.white,

              size: 28,

            ),

          ),

          const SizedBox(

            width: 4,

          ),

          const Expanded(

            child: Text(

              '최종 미리보기',

              style: TextStyle(

                color:

                    Colors.white,

                fontSize: 21,

                fontWeight:

                    FontWeight.w800,

              ),

            ),

          ),

          Text(

            stepText,

            style:

                const TextStyle(

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

            decoration:

                BoxDecoration(

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

                  left: 20,

                  top: 20,

                  child: Text(

                    'HaloVision 문서 미리보기',

                    style:

                        TextStyle(

                      color:

                          Colors.black87,

                      fontSize:

                          17,

                      fontWeight:

                          FontWeight.w900,

                    ),

                  ),

                ),



                ..._buildAvoidRegions(

                  width,

                  height,

                ),



                ..._buildVisibleRegions(

                  width,

                  height,

                ),



                if (profile.textRegion !=

                    null)

                  _buildTextRegion(

                    profile.textRegion!,

                    width,

                    height,

                  ),



                if (profile.mediaRegion !=

                    null)

                  _buildMediaRegion(

                    profile.mediaRegion!,

                    width,

                    height,

                  ),



                Positioned(

                  right: 12,

                  bottom: 12,

                  child: Container(

                    padding:

                        const EdgeInsets

                            .symmetric(

                      horizontal: 9,

                      vertical: 6,

                    ),

                    decoration:

                        BoxDecoration(

                      color: Colors

                          .black

                          .withValues(

                        alpha: 0.72,

                      ),

                      borderRadius:

                          BorderRadius

                              .circular(

                        10,

                      ),

                    ),

                    child:

                        const Text(

                      '최종 배치',

                      style:

                          TextStyle(

                        color: Colors

                            .white,

                        fontSize: 11,

                        fontWeight:

                            FontWeight

                                .w700,

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

    return profile.avoidRegions.map(

      (region) {

        final regionWidth =

            region.width * width;



        final regionHeight =

            region.height * height;



        if (region.type ==

            RegionShapeType.custom) {

          return Positioned(

            left:

                region.x * width,

            top:

                region.y * height,

            width:

                regionWidth,

            height:

                regionHeight,

            child:

                IgnorePointer(

              child: CustomPaint(

                painter:

                    _RegionPainter(

                  points:

                      region.points,

                  color:

                      const Color(

                    0xFFF4D35E,

                  ),

                ),

              ),

            ),

          );

        }



        return Positioned(

          left:

              region.x * width,

          top:

              region.y * height,

          width:

              regionWidth,

          height:

              regionHeight,

          child: IgnorePointer(

            child: Container(

              decoration:

                  BoxDecoration(

                color:

                    const Color(

                  0xFFF4D35E,

                ).withValues(

                  alpha: 0.48,

                ),

                shape: region.type ==

                        RegionShapeType

                            .circle

                    ? BoxShape.circle

                    : BoxShape

                        .rectangle,

                borderRadius:

                    region.type ==

                            RegionShapeType

                                .ellipse

                        ? BorderRadius

                            .all(

                            Radius

                                .elliptical(

                              regionWidth,

                              regionHeight,

                            ),

                          )

                        : null,

                border:

                    Border.all(

                  color:

                      const Color(

                    0xFFFFE681,

                  ),

                  width: 2.5,

                ),

              ),

            ),

          ),

        );

      },

    ).toList();

  }



  List<Widget> _buildVisibleRegions(

    double width,

    double height,

  ) {

    return profile.visibleRegions.map(

      (region) {

        final regionWidth =

            region.width * width;



        final regionHeight =

            region.height * height;



        if (region.type ==

            RegionShapeType.custom) {

          return Positioned(

            left:

                region.x * width,

            top:

                region.y * height,

            width:

                regionWidth,

            height:

                regionHeight,

            child:

                IgnorePointer(

              child: CustomPaint(

                painter:

                    _RegionPainter(

                  points:

                      region.points,

                  color:

                      const Color(

                    0xFF63C174,

                  ),

                ),

              ),

            ),

          );

        }



        return Positioned(

          left:

              region.x * width,

          top:

              region.y * height,

          width:

              regionWidth,

          height:

              regionHeight,

          child: IgnorePointer(

            child: Container(

              decoration:

                  BoxDecoration(

                color:

                    const Color(

                  0xFF63C174,

                ).withValues(

                  alpha: 0.18,

                ),

                shape: region.type ==

                        RegionShapeType

                            .circle

                    ? BoxShape.circle

                    : BoxShape

                        .rectangle,

                borderRadius:

                    region.type ==

                            RegionShapeType

                                .ellipse

                        ? BorderRadius

                            .all(

                            Radius

                                .elliptical(

                              regionWidth,

                              regionHeight,

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

        );

      },

    ).toList();

  }



  Widget _buildTextRegion(

    LayoutRegion region,

    double width,

    double height,

  ) {

    const blue =

        Color(0xFF5AA9FF);



    return Positioned(

      left:

          region.x * width,

      top:

          region.y * height,

      width:

          region.width * width,

      height:

          region.height * height,

      child: Container(

        padding:

            const EdgeInsets.all(

          10,

        ),

        decoration:

            BoxDecoration(

          color:

              blue.withValues(

            alpha: 0.18,

          ),

          borderRadius:

              BorderRadius.circular(

            15,

          ),

          border: Border.all(

            color: blue,

            width: 2,

          ),

        ),

        child: Center(

          child: Text(

            'HaloVision은 문서 내용을\n사용자의 시야에 맞게 재배치합니다.',

            textAlign:

                TextAlign.center,

            overflow:

                TextOverflow.fade,

            style: TextStyle(

              color:

                  Colors.black87,

              fontSize:

                  13 *

                      profile

                          .fontScale,

              fontWeight:

                  profile.boldText

                      ? FontWeight

                          .w900

                      : FontWeight

                          .w700,

              height:

                  profile.lineSpacing,

            ),

          ),

        ),

      ),

    );

  }



  Widget _buildMediaRegion(

    LayoutRegion region,

    double width,

    double height,

  ) {

    const green =

        Color(0xFF63C174);



    return Positioned(

      left:

          region.x * width,

      top:

          region.y * height,

      width:

          region.width * width,

      height:

          region.height * height,

      child: Container(

        decoration:

            BoxDecoration(

          color:

              green.withValues(

            alpha: 0.18,

          ),

          borderRadius:

              BorderRadius.circular(

            15,

          ),

          border:

              Border.all(

            color: green,

            width: 2,

          ),

        ),

        child:

            const Center(

          child: Icon(

            Icons

                .image_outlined,

            color:

                Colors.black54,

            size: 44,

          ),

        ),

      ),

    );

  }

}



class _RegionPainter

    extends CustomPainter {

  final List<Offset> points;

  final Color color;



  const _RegionPainter({

    required this.points,

    required this.color,

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

          color.withValues(

        alpha: 0.45,

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

    covariant _RegionPainter

        oldDelegate,

  ) {

    return true;

  }

}



class _SummaryItem

    extends StatelessWidget {

  final Color color;

  final String title;

  final String description;



  const _SummaryItem({

    required this.color,

    required this.title,

    required this.description,

  });



  @override

  Widget build(

    BuildContext context,

  ) {

    return Row(

      crossAxisAlignment:

          CrossAxisAlignment.start,

      children: [

        Container(

          width: 13,

          height: 13,

          margin:

              const EdgeInsets.only(

            top: 4,

          ),

          decoration:

              BoxDecoration(

            color: color,

            shape:

                BoxShape.circle,

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

                  color:

                      Colors.white,

                  fontSize: 14,

                  fontWeight:

                      FontWeight.w800,

                ),

              ),

              const SizedBox(

                height: 4,

              ),

              Text(

                description,

                style: TextStyle(

                  color: Colors

                      .white

                      .withValues(

                    alpha: 0.62,

                  ),

                  fontSize: 13,

                  height: 1.4,

                ),

              ),

            ],

          ),

        ),

      ],

    );

  }

}



class _DisplaySummaryRow

    extends StatelessWidget {

  final String title;

  final String value;



  const _DisplaySummaryRow({

    required this.title,

    required this.value,

  });



  @override

  Widget build(

    BuildContext context,

  ) {

    return Row(

      children: [

        Expanded(

          child: Text(

            title,

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

          value,

          style:

              const TextStyle(

            color:

                Colors.white,

            fontSize: 13,

            fontWeight:

                FontWeight.w700,

          ),

        ),

      ],

    );

  }

}