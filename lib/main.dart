import 'package:flutter/material.dart';

import 'screens/home_screen.dart';
import 'screens/onboarding_screen.dart';
import 'services/vision_profile_storage.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final hasCompletedSetup =
      await VisionProfileStorage.hasCompletedSetup();

  runApp(
    HaloVisionApp(
      hasCompletedSetup: hasCompletedSetup,
    ),
  );
}

class HaloVisionApp extends StatelessWidget {
  final bool hasCompletedSetup;

  const HaloVisionApp({
    super.key,
    required this.hasCompletedSetup,
  });

  @override
  Widget build(BuildContext context) {
    const yellow = Color(0xFFF4D35E);

    return MaterialApp(
      title: 'HaloVision',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: Colors.black,
        colorScheme: ColorScheme.fromSeed(
          seedColor: yellow,
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
        fontFamilyFallback: const [
          'Noto Sans KR',
          'Roboto',
        ],
      ),
      home: hasCompletedSetup
          ? const HomeScreen()
          : const OnboardingScreen(),
    );
  }
}