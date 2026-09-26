import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

class DocumentImportScreen extends StatefulWidget {
  const DocumentImportScreen({
    super.key,
  });

  @override
  State<DocumentImportScreen> createState() =>
      _DocumentImportScreenState();
}

class _DocumentImportScreenState
    extends State<DocumentImportScreen> {
  final ImagePicker _picker = ImagePicker();

  XFile? _selectedImage;
  bool _isPicking = false;

  Future<void> _pickImage() async {
    if (_isPicking) {
      return;
    }

    setState(() {
      _isPicking = true;
    });

    try {
      final image = await _picker.pickImage(
        source: ImageSource.gallery,
        imageQuality: 100,
      );

      if (!mounted) {
        return;
      }

      if (image == null) {
        setState(() {
          _isPicking = false;
        });
        return;
      }

      setState(() {
        _selectedImage = image;
        _isPicking = false;
      });
    } catch (e) {
      if (!mounted) {
        return;
      }

      setState(() {
        _isPicking = false;
      });

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '이미지를 불러오지 못했습니다.\n$e',
          ),
        ),
      );
    }
  }

  void _removeImage() {
    setState(() {
      _selectedImage = null;
    });
  }

  void _useImage() {
    final image = _selectedImage;

    if (image == null) {
      return;
    }

    Navigator.pop(
      context,
      image.path,
    );
  }

  @override
  Widget build(BuildContext context) {
    const yellow = Color(0xFFF4D35E);

    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(),

            Expanded(
              child: _selectedImage == null
                  ? _buildEmptyState()
                  : _buildPreview(),
            ),

            if (_selectedImage != null)
              Padding(
                padding: const EdgeInsets.fromLTRB(
                  20,
                  10,
                  20,
                  22,
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: _removeImage,
                        icon: const Icon(
                          Icons.refresh_rounded,
                        ),
                        label: const Text(
                          '다시 선택',
                        ),
                        style: OutlinedButton.styleFrom(
                          foregroundColor: Colors.white,
                          side: BorderSide(
                            color: Colors.white.withValues(
                              alpha: 0.28,
                            ),
                          ),
                          padding: const EdgeInsets.symmetric(
                            vertical: 16,
                          ),
                        ),
                      ),
                    ),

                    const SizedBox(width: 12),

                    Expanded(
                      child: FilledButton.icon(
                        onPressed: _useImage,
                        icon: const Icon(
                          Icons.check_rounded,
                        ),
                        label: const Text(
                          '이 이미지 사용',
                        ),
                        style: FilledButton.styleFrom(
                          backgroundColor: yellow,
                          foregroundColor: Colors.black,
                          padding: const EdgeInsets.symmetric(
                            vertical: 16,
                          ),
                          textStyle: const TextStyle(
                            fontWeight: FontWeight.w800,
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

  Widget _buildHeader() {
    return Padding(
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
              '문서 가져오기',
              style: TextStyle(
                color: Colors.white,
                fontSize: 20,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),

          const Icon(
            Icons.upload_file_rounded,
            color: Color(0xFFF4D35E),
            size: 26,
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyState() {
    const yellow = Color(0xFFF4D35E);

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 92,
              height: 92,
              decoration: BoxDecoration(
                color: yellow.withValues(
                  alpha: 0.12,
                ),
                borderRadius: BorderRadius.circular(
                  26,
                ),
              ),
              child: const Icon(
                Icons.image_outlined,
                color: yellow,
                size: 48,
              ),
            ),

            const SizedBox(height: 24),

            const Text(
              '문서 이미지를 선택해주세요.',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: Colors.white,
                fontSize: 21,
                fontWeight: FontWeight.w900,
              ),
            ),

            const SizedBox(height: 10),

            Text(
              '휴대폰에 저장된 문서 사진을 불러와\nHaloVision으로 분석할 수 있어요.',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: Colors.white.withValues(
                  alpha: 0.62,
                ),
                fontSize: 14,
                height: 1.5,
              ),
            ),

            const SizedBox(height: 28),

            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                onPressed: _isPicking
                    ? null
                    : _pickImage,
                icon: _isPicking
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2.5,
                          color: Colors.black,
                        ),
                      )
                    : const Icon(
                        Icons.photo_library_outlined,
                      ),
                label: Text(
                  _isPicking
                      ? '불러오는 중...'
                      : '갤러리에서 선택',
                ),
                style: FilledButton.styleFrom(
                  backgroundColor: yellow,
                  foregroundColor: Colors.black,
                  disabledBackgroundColor:
                      yellow.withValues(
                    alpha: 0.5,
                  ),
                  padding: const EdgeInsets.symmetric(
                    vertical: 17,
                  ),
                  textStyle: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPreview() {
    final image = _selectedImage;

    if (image == null) {
      return const SizedBox.shrink();
    }

    return Padding(
      padding: const EdgeInsets.fromLTRB(
        18,
        12,
        18,
        6,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '선택한 문서',
            style: TextStyle(
              color: Colors.white.withValues(
                alpha: 0.68,
              ),
              fontSize: 14,
              fontWeight: FontWeight.w700,
            ),
          ),

          const SizedBox(height: 12),

          Expanded(
            child: Container(
              width: double.infinity,
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(
                  22,
                ),
              ),
              clipBehavior: Clip.antiAlias,
              child: Image.file(
                File(image.path),
                fit: BoxFit.contain,
              ),
            ),
          ),
        ],
      ),
    );
  }
}