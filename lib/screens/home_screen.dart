import 'package:flutter/material.dart';
import 'document_import_screen.dart';
import 'document_analysis_screen.dart';

import '../models/vision_profile.dart';
import '../services/vision_profile_storage.dart';
import 'document_camera_screen.dart';
import 'onboarding_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() =>
      _HomeScreenState();
}

class _HomeScreenState
    extends State<HomeScreen> {
  VisionProfile? _profile;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _loadProfile();
  }

  Future<void> _loadProfile() async {
    final profile =
        await VisionProfileStorage.loadProfile();

    if (!mounted) {
      return;
    }

    setState(() {
      _profile = profile;
      _loading = false;
    });
  }

  String _visionTypeLabel(
    VisionType type,
  ) {
    switch (type) {
      case VisionType.centralLoss:
        return '중심부 시야 설정';

      case VisionType.peripheralLoss:
        return '주변부 시야 설정';

      case VisionType.blurred:
        return '흐림 보정 설정';

      case VisionType.colorDifficulty:
        return '색상 보정 설정';

      case VisionType.unknown:
        return '추천 시야 설정';

      case VisionType.custom:
        return '직접 설정';
    }
  }

  Future<void> _resetSetup() async {
    await VisionProfileStorage.clearProfile();

    if (!mounted) {
      return;
    }

    Navigator.pushAndRemoveUntil(
      context,
      MaterialPageRoute(
        builder: (_) =>
            const OnboardingScreen(),
      ),
      (route) => false,
    );
  }

  Future<void> _openCamera() async {
  final imagePath =
      await Navigator.push<String>(
    context,
    MaterialPageRoute(
      builder: (_) =>
          const DocumentCameraScreen(),
    ),
  );

  if (!mounted || imagePath == null) {
    return;
  }

  final profile = _profile;

  if (profile == null) {
    return;
  }

  await Navigator.push(
    context,
    MaterialPageRoute(
      builder: (_) =>
          DocumentAnalysisScreen(
        imagePath: imagePath,
        profile: profile,
      ),
    ),
  );
}

  Future<void> _openDocumentImport() async {
  final imagePath =
      await Navigator.push<String>(
    context,
    MaterialPageRoute(
      builder: (_) =>
          const DocumentImportScreen(),
    ),
  );

  if (!mounted || imagePath == null) {
    return;
  }

  final profile = _profile;

  if (profile == null) {
    return;
  }

  await Navigator.push(
    context,
    MaterialPageRoute(
      builder: (_) =>
          DocumentAnalysisScreen(
        imagePath: imagePath,
        profile: profile,
      ),
    ),
  );
}

  @override
  Widget build(
    BuildContext context,
  ) {
    const yellow =
        Color(0xFFF4D35E);

    if (_loading) {
      return const Scaffold(
        backgroundColor:
            Colors.black,
        body: Center(
          child:
              CircularProgressIndicator(
            color: yellow,
          ),
        ),
      );
    }

    final profile = _profile;

    return Scaffold(
      backgroundColor:
          Colors.black,
      body: SafeArea(
        child:
            SingleChildScrollView(
          padding:
              const EdgeInsets
                  .fromLTRB(
            24,
            24,
            24,
            32,
          ),
          child: Column(
            crossAxisAlignment:
                CrossAxisAlignment
                    .start,
            children: [
              const Row(
                children: [
                  Expanded(
                    child: Text(
                      'HaloVision',
                      style:
                          TextStyle(
                        color:
                            Colors.white,
                        fontSize:
                            28,
                        fontWeight:
                            FontWeight
                                .w900,
                      ),
                    ),
                  ),
                  Icon(
                    Icons
                        .visibility_rounded,
                    color: yellow,
                    size: 30,
                  ),
                ],
              ),

              const SizedBox(
                height: 34,
              ),

              const Text(
                '무엇을 볼까요?',
                style: TextStyle(
                  color:
                      Colors.white,
                  fontSize: 30,
                  fontWeight:
                      FontWeight.w900,
                  letterSpacing:
                      -0.6,
                ),
              ),

              const SizedBox(
                height: 10,
              ),

              Text(
                '문서를 촬영하거나 가져오면\n현재 시야 설정에 맞게 다시 배치해드려요.',
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
                height: 28,
              ),

              if (profile != null)
                Container(
                  width:
                      double.infinity,
                  padding:
                      const EdgeInsets
                          .all(
                    18,
                  ),
                  decoration:
                      BoxDecoration(
                    color:
                        const Color(
                      0xFF111111,
                    ),
                    borderRadius:
                        BorderRadius
                            .circular(
                      22,
                    ),
                    border:
                        Border.all(
                      color: yellow
                          .withValues(
                        alpha: 0.28,
                      ),
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment:
                        CrossAxisAlignment
                            .start,
                    children: [
                      const Row(
                        children: [
                          Icon(
                            Icons
                                .tune_rounded,
                            color:
                                yellow,
                            size: 24,
                          ),
                          SizedBox(
                            width: 10,
                          ),
                          Text(
                            '현재 시야 설정',
                            style:
                                TextStyle(
                              color:
                                  Colors.white,
                              fontSize:
                                  17,
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

                      Text(
                        _visionTypeLabel(
                          profile
                              .visionType,
                        ),
                        style:
                            const TextStyle(
                          color:
                              yellow,
                          fontSize:
                              19,
                          fontWeight:
                              FontWeight
                                  .w900,
                        ),
                      ),

                      const SizedBox(
                        height: 8,
                      ),

                      Text(
                        '설정된 프로필이 문서 결과 화면에 적용됩니다.',
                        style:
                            TextStyle(
                          color: Colors
                              .white
                              .withValues(
                            alpha:
                                0.62,
                          ),
                          fontSize:
                              13,
                          height:
                              1.4,
                        ),
                      ),
                    ],
                  ),
                ),

              const SizedBox(
                height: 24,
              ),

              _HomeActionCard(
                icon: Icons
                    .camera_alt_rounded,
                title:
                    '문서 촬영하기',
                description:
                    '카메라로 문서를 촬영해 분석합니다.',
                onTap:
                    _openCamera,
              ),

              const SizedBox(
                height: 14,
              ),

              _HomeActionCard(
                icon: Icons.upload_file_rounded,
                title: '문서 가져오기',
                description:
                    '휴대폰에 저장된 문서 이미지를 불러옵니다.',
                onTap: _openDocumentImport,
              ),

              const SizedBox(
                height: 26,
              ),

              SizedBox(
                width:
                    double.infinity,
                child:
                    OutlinedButton
                        .icon(
                  onPressed:
                      _resetSetup,
                  icon:
                      const Icon(
                    Icons
                        .settings_rounded,
                  ),
                  label:
                      const Text(
                    '내 시야 설정 다시 하기',
                  ),
                  style:
                      OutlinedButton
                          .styleFrom(
                    foregroundColor:
                        Colors.white,
                    side:
                        BorderSide(
                      color: Colors
                          .white
                          .withValues(
                        alpha:
                            0.18,
                      ),
                    ),
                    padding:
                        const EdgeInsets
                            .symmetric(
                      vertical:
                          16,
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
            ],
          ),
        ),
      ),
    );
  }
}

class _HomeActionCard
    extends StatelessWidget {
  final IconData icon;
  final String title;
  final String description;
  final VoidCallback onTap;

  const _HomeActionCard({
    required this.icon,
    required this.title,
    required this.description,
    required this.onTap,
  });

  @override
  Widget build(
    BuildContext context,
  ) {
    const yellow =
        Color(0xFFF4D35E);

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius:
            BorderRadius.circular(
          22,
        ),
        child: Container(
          width:
              double.infinity,
          padding:
              const EdgeInsets.all(
            20,
          ),
          decoration:
              BoxDecoration(
            color:
                const Color(
              0xFF111111,
            ),
            borderRadius:
                BorderRadius.circular(
              22,
            ),
            border:
                Border.all(
              color: Colors.white
                  .withValues(
                alpha: 0.08,
              ),
            ),
          ),
          child: Row(
            children: [
              Container(
                width: 58,
                height: 58,
                decoration:
                    BoxDecoration(
                  color: yellow
                      .withValues(
                    alpha: 0.13,
                  ),
                  borderRadius:
                      BorderRadius
                          .circular(
                    17,
                  ),
                ),
                child: Icon(
                  icon,
                  color: yellow,
                  size: 29,
                ),
              ),

              const SizedBox(
                width: 16,
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
                        fontSize:
                            18,
                        fontWeight:
                            FontWeight
                                .w800,
                      ),
                    ),

                    const SizedBox(
                      height: 5,
                    ),

                    Text(
                      description,
                      style:
                          TextStyle(
                        color: Colors
                            .white
                            .withValues(
                          alpha:
                              0.62,
                        ),
                        fontSize:
                            13,
                        height:
                            1.4,
                      ),
                    ),
                  ],
                ),
              ),

              Icon(
                Icons
                    .chevron_right_rounded,
                color: Colors.white
                    .withValues(
                  alpha: 0.4,
                ),
                size: 28,
              ),
            ],
          ),
        ),
      ),
    );
  }
}