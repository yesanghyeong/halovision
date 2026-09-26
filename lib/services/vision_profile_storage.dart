import 'package:shared_preferences/shared_preferences.dart';

import '../models/vision_profile.dart';

class VisionProfileStorage {
  static const String _profileKey = 'halo_vision_profile';
  static const String _setupCompletedKey =
      'halo_vision_setup_completed';

  static Future<void> saveProfile(
    VisionProfile profile,
  ) async {
    final prefs = await SharedPreferences.getInstance();

    await prefs.setString(
      _profileKey,
      profile.encode(),
    );

    await prefs.setBool(
      _setupCompletedKey,
      true,
    );
  }

  static Future<VisionProfile> loadProfile() async {
    final prefs = await SharedPreferences.getInstance();

    final savedProfile = prefs.getString(_profileKey);

    if (savedProfile == null || savedProfile.isEmpty) {
      return VisionProfile.defaultProfile();
    }

    try {
      return VisionProfile.decode(savedProfile);
    } catch (e) {
      return VisionProfile.defaultProfile();
    }
  }

  static Future<bool> hasCompletedSetup() async {
    final prefs = await SharedPreferences.getInstance();

    return prefs.getBool(_setupCompletedKey) ?? false;
  }

  static Future<void> markSetupCompleted(
    bool value,
  ) async {
    final prefs = await SharedPreferences.getInstance();

    await prefs.setBool(
      _setupCompletedKey,
      value,
    );
  }

  static Future<void> clearProfile() async {
    final prefs = await SharedPreferences.getInstance();

    await prefs.remove(_profileKey);
    await prefs.remove(_setupCompletedKey);
  }
}