import 'dart:io';

import 'package:camera/camera.dart';
import 'package:flutter/material.dart';

class DocumentCameraScreen extends StatefulWidget {
  const DocumentCameraScreen({
    super.key,
  });

  @override
  State<DocumentCameraScreen> createState() =>
      _DocumentCameraScreenState();
}

class _DocumentCameraScreenState
    extends State<DocumentCameraScreen> {
  CameraController? _controller;

  bool _isInitializing = true;
  bool _isTakingPicture = false;

  XFile? _capturedImage;

  @override
  void initState() {
    super.initState();
    _initializeCamera();
  }

  Future<void> _initializeCamera() async {
    try {
      final cameras =
          await availableCameras();

      if (cameras.isEmpty) {
        if (!mounted) {
          return;
        }

        setState(() {
          _isInitializing = false;
        });

        return;
      }

      final backCamera =
          cameras.firstWhere(
        (camera) =>
            camera.lensDirection ==
            CameraLensDirection.back,
        orElse: () => cameras.first,
      );

      final controller =
          CameraController(
        backCamera,
        ResolutionPreset.high,
        enableAudio: false,
      );

      await controller.initialize();

      if (!mounted) {
        await controller.dispose();
        return;
      }

      setState(() {
        _controller = controller;
        _isInitializing = false;
      });
    } catch (e) {
      if (!mounted) {
        return;
      }

      setState(() {
        _isInitializing = false;
      });

      ScaffoldMessenger.of(context)
          .showSnackBar(
        SnackBar(
          content: Text(
            '카메라를 시작할 수 없습니다.\n$e',
          ),
        ),
      );
    }
  }

  Future<void> _takePicture() async {
    final controller =
        _controller;

    if (controller == null ||
        !controller.value.isInitialized ||
        _isTakingPicture) {
      return;
    }

    setState(() {
      _isTakingPicture = true;
    });

    try {
      final image =
          await controller.takePicture();

      if (!mounted) {
        return;
      }

      setState(() {
        _capturedImage = image;
      });
    } catch (e) {
      if (!mounted) {
        return;
      }

      ScaffoldMessenger.of(context)
          .showSnackBar(
        SnackBar(
          content: Text(
            '사진 촬영에 실패했습니다.\n$e',
          ),
        ),
      );
    } finally {
      if (mounted) {
        setState(() {
          _isTakingPicture = false;
        });
      }
    }
  }

  void _retake() {
    setState(() {
      _capturedImage = null;
    });
  }

  void _useImage() {
    final image =
        _capturedImage;

    if (image == null) {
      return;
    }

    Navigator.pop(
      context,
      image.path,
    );
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(
    BuildContext context,
  ) {
    if (_capturedImage != null) {
      return _buildPreviewScreen();
    }

    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(),

            Expanded(
              child: _buildCameraView(),
            ),

            _buildBottomControls(),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Padding(
      padding:
          const EdgeInsets.fromLTRB(
        8,
        8,
        16,
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
              '문서 촬영',
              style: TextStyle(
                color: Colors.white,
                fontSize: 20,
                fontWeight:
                    FontWeight.w800,
              ),
            ),
          ),

          const Icon(
            Icons
                .document_scanner_outlined,
            color:
                Color(0xFFF4D35E),
            size: 26,
          ),
        ],
      ),
    );
  }

  Widget _buildCameraView() {
    if (_isInitializing) {
      return const Center(
        child:
            CircularProgressIndicator(
          color:
              Color(0xFFF4D35E),
        ),
      );
    }

    final controller =
        _controller;

    if (controller == null ||
        !controller.value.isInitialized) {
      return Center(
        child: Padding(
          padding:
              const EdgeInsets.all(
            28,
          ),
          child: Column(
            mainAxisAlignment:
                MainAxisAlignment.center,
            children: [
              const Icon(
                Icons
                    .no_photography_outlined,
                color:
                    Colors.white54,
                size: 64,
              ),

              const SizedBox(
                height: 18,
              ),

              const Text(
                '카메라를 사용할 수 없습니다.',
                textAlign:
                    TextAlign.center,
                style: TextStyle(
                  color:
                      Colors.white,
                  fontSize: 18,
                  fontWeight:
                      FontWeight.w800,
                ),
              ),

              const SizedBox(
                height: 10,
              ),

              Text(
                '카메라 권한을 확인한 뒤 다시 시도해주세요.',
                textAlign:
                    TextAlign.center,
                style: TextStyle(
                  color: Colors.white
                      .withValues(
                    alpha: 0.6,
                  ),
                  fontSize: 14,
                  height: 1.5,
                ),
              ),

              const SizedBox(
                height: 20,
              ),

              OutlinedButton.icon(
                onPressed:
                    _initializeCamera,
                icon: const Icon(
                  Icons
                      .refresh_rounded,
                ),
                label:
                    const Text(
                  '다시 시도',
                ),
                style:
                    OutlinedButton
                        .styleFrom(
                  foregroundColor:
                      const Color(
                    0xFFF4D35E,
                  ),
                ),
              ),
            ],
          ),
        ),
      );
    }

    return LayoutBuilder(
      builder: (
        context,
        constraints,
      ) {
        return Stack(
          fit: StackFit.expand,
          children: [
            ClipRRect(
              borderRadius:
                  BorderRadius.circular(
                22,
              ),
              child:
                  CameraPreview(
                controller,
              ),
            ),

            Positioned.fill(
              child: IgnorePointer(
                child:
                    CustomPaint(
                  painter:
                      _DocumentFramePainter(),
                ),
              ),
            ),

            Positioned(
              left: 24,
              right: 24,
              top: 20,
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
                    alpha: 0.58,
                  ),
                  borderRadius:
                      BorderRadius
                          .circular(
                    14,
                  ),
                ),
                child:
                    const Text(
                  '문서가 가이드 안에 들어오도록 맞춰주세요.',
                  textAlign:
                      TextAlign.center,
                  style:
                      TextStyle(
                    color:
                        Colors.white,
                    fontSize: 13,
                    fontWeight:
                        FontWeight
                            .w700,
                  ),
                ),
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _buildBottomControls() {
    return Container(
      padding:
          const EdgeInsets.fromLTRB(
        24,
        16,
        24,
        22,
      ),
      child: Column(
        children: [
          Text(
            '문서 전체가 잘 보이도록 촬영해주세요.',
            style: TextStyle(
              color: Colors.white
                  .withValues(
                alpha: 0.62,
              ),
              fontSize: 13,
            ),
          ),

          const SizedBox(
            height: 16,
          ),

          GestureDetector(
            onTap:
                _isTakingPicture
                    ? null
                    : _takePicture,
            child:
                AnimatedContainer(
              duration:
                  const Duration(
                milliseconds: 150,
              ),
              width: 78,
              height: 78,
              decoration:
                  BoxDecoration(
                shape:
                    BoxShape.circle,
                border:
                    Border.all(
                  color:
                      Colors.white,
                  width: 5,
                ),
                color: _isTakingPicture
                    ? Colors.white
                        .withValues(
                          alpha:
                              0.35,
                        )
                    : const Color(
                        0xFFF4D35E,
                      ),
              ),
              child: _isTakingPicture
                  ? const Padding(
                      padding:
                          EdgeInsets.all(
                        21,
                      ),
                      child:
                          CircularProgressIndicator(
                        strokeWidth:
                            3,
                        color:
                            Colors.black,
                      ),
                    )
                  : const Icon(
                      Icons
                          .camera_alt_rounded,
                      color:
                          Colors.black,
                      size: 32,
                    ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPreviewScreen() {
    final image =
        _capturedImage;

    if (image == null) {
      return const SizedBox.shrink();
    }

    return Scaffold(
      backgroundColor:
          Colors.black,
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding:
                  const EdgeInsets
                      .fromLTRB(
                12,
                10,
                18,
                10,
              ),
              child: Row(
                children: [
                  IconButton(
                    onPressed:
                        _retake,
                    icon:
                        const Icon(
                      Icons
                          .arrow_back_rounded,
                      color:
                          Colors.white,
                      size: 28,
                    ),
                  ),

                  const Expanded(
                    child: Text(
                      '촬영 결과 확인',
                      style:
                          TextStyle(
                        color:
                            Colors.white,
                        fontSize:
                            20,
                        fontWeight:
                            FontWeight
                                .w800,
                      ),
                    ),
                  ),

                  const Icon(
                    Icons
                        .check_circle_outline_rounded,
                    color:
                        Color(
                      0xFFF4D35E,
                    ),
                  ),
                ],
              ),
            ),

            Expanded(
              child: Padding(
                padding:
                    const EdgeInsets.all(
                  18,
                ),
                child:
                    ClipRRect(
                  borderRadius:
                      BorderRadius
                          .circular(
                    22,
                  ),
                  child: Container(
                    width:
                        double.infinity,
                    color:
                        Colors.white,
                    child:
                        Image.file(
                      File(
                        image.path,
                      ),
                      fit:
                          BoxFit.contain,
                    ),
                  ),
                ),
              ),
            ),

            Padding(
              padding:
                  const EdgeInsets
                      .fromLTRB(
                20,
                8,
                20,
                22,
              ),
              child: Row(
                children: [
                  Expanded(
                    child:
                        OutlinedButton.icon(
                      onPressed:
                          _retake,
                      icon:
                          const Icon(
                        Icons
                            .refresh_rounded,
                      ),
                      label:
                          const Text(
                        '다시 찍기',
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
                                0.3,
                          ),
                        ),
                        padding:
                            const EdgeInsets
                                .symmetric(
                          vertical:
                              16,
                        ),
                      ),
                    ),
                  ),

                  const SizedBox(
                    width: 12,
                  ),

                  Expanded(
                    child:
                        FilledButton.icon(
                      onPressed:
                          _useImage,
                      icon:
                          const Icon(
                        Icons
                            .check_rounded,
                      ),
                      label:
                          const Text(
                        '이 사진 사용',
                      ),
                      style:
                          FilledButton
                              .styleFrom(
                        backgroundColor:
                            const Color(
                          0xFFF4D35E,
                        ),
                        foregroundColor:
                            Colors.black,
                        padding:
                            const EdgeInsets
                                .symmetric(
                          vertical:
                              16,
                        ),
                        textStyle:
                            const TextStyle(
                          fontWeight:
                              FontWeight
                                  .w800,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DocumentFramePainter
    extends CustomPainter {
  @override
  void paint(
    Canvas canvas,
    Size size,
  ) {
    const marginX = 34.0;
    const marginY = 74.0;

    final rect =
        Rect.fromLTRB(
      marginX,
      marginY,
      size.width - marginX,
      size.height - marginY,
    );

    final overlayPaint =
        Paint()
          ..color = Colors.black
              .withValues(
            alpha: 0.30,
          );

    final path = Path()
      ..addRect(
        Offset.zero & size,
      )
      ..addRRect(
        RRect.fromRectAndRadius(
          rect,
          const Radius.circular(
            18,
          ),
        ),
      )
      ..fillType =
          PathFillType.evenOdd;

    canvas.drawPath(
      path,
      overlayPaint,
    );

    final borderPaint =
        Paint()
          ..color =
              const Color(
            0xFFF4D35E,
          )
          ..style =
              PaintingStyle.stroke
          ..strokeWidth = 3;

    canvas.drawRRect(
      RRect.fromRectAndRadius(
        rect,
        const Radius.circular(
          18,
        ),
      ),
      borderPaint,
    );
  }

  @override
  bool shouldRepaint(
    covariant CustomPainter
        oldDelegate,
  ) {
    return false;
  }
}