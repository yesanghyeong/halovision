import 'package:flutter/material.dart';

import '../models/vision_profile.dart';
import 'central_preview_screen.dart';
import 'display_setup_screen.dart';

class ColorDifficultySetupScreen extends StatefulWidget {
  const ColorDifficultySetupScreen({super.key});

  @override
  State<ColorDifficultySetupScreen> createState() =>
      _ColorDifficultySetupScreenState();
}

class _ColorDifficultySetupScreenState
    extends State<ColorDifficultySetupScreen> {
  VisionProfile _profile = const VisionProfile(
    visionType: VisionType.colorDifficulty,
    colorCorrectionMode: ColorCorrectionMode.highContrast,
  );

  Future<void> _openDisplaySetup() async {
    final result = await Navigator.push<VisionProfile>(
      context,
      MaterialPageRoute(
        builder: (_) => DisplaySetupScreen(
          profile: _profile,
          stepText: '1 / 2',
          title: '색상 보정 설정',
        ),
      ),
    );

    if (!mounted || result == null) {
      return;
    }

    setState(() {
      _profile = result;
    });

    final completedProfile =
        await Navigator.push<VisionProfile>(
      context,
      MaterialPageRoute(
        builder: (_) => CentralPreviewScreen(
          profile: _profile,
          stepText: '2 / 2',
        ),
      ),
    );

    if (!mounted || completedProfile == null) {
      return;
    }

    Navigator.pop(
      context,
      completedProfile,
    );
  }

  @override
  void initState() {
    super.initState();

    WidgetsBinding.instance.addPostFrameCallback((_) {
      _openDisplaySetup();
    });
  }

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      backgroundColor: Colors.black,
      body: Center(
        child: CircularProgressIndicator(
          color: Color(0xFFF4D35E),
        ),
      ),
    );
  }
}