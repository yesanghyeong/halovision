import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const HaloVisionApp());
}

class HaloVisionApp extends StatelessWidget {
  const HaloVisionApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'HaloVision',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: Colors.black,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFFFFD84D),
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
      ),
      home: const HaloControlCenter(),
    );
  }
}

class HaloControlCenter extends StatefulWidget {
  const HaloControlCenter({super.key});

  @override
  State<HaloControlCenter> createState() => _HaloControlCenterState();
}

class _HaloControlCenterState extends State<HaloControlCenter>
    with WidgetsBindingObserver {
  static const MethodChannel _channel =
      MethodChannel('com.example.halovision_app/accessibility');

  static const Color haloYellow = Color(0xFFFFD84D);
  static const Color haloBlue = Color(0xFF365CB8);
  static const Color cardColor = Color(0xFF141414);
  static const Color secondaryText = Color(0xFFB0B0B0);
  static const Color runningGreen = Color(0xFF58D17B);
  static const Color offRed = Color(0xFFE35C5C);

  bool _permissionEnabled = false;
  bool _haloRunning = false;
  bool _loading = true;
  bool _changing = false;
  String? _message;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refreshStatus();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refreshStatus();
    }
  }

  Future<void> _refreshStatus() async {
    try {
      final permission =
          await _channel.invokeMethod<bool>('isAccessibilityServiceEnabled') ??
              false;

      final running = permission
          ? (await _channel.invokeMethod<bool>('isHaloRunning') ?? true)
          : false;

      if (!mounted) return;

      setState(() {
        _permissionEnabled = permission;
        _haloRunning = permission && running;
        _loading = false;
        _message = null;
      });
    } on PlatformException catch (e) {
      if (!mounted) return;

      setState(() {
        _loading = false;
        _message = '상태를 확인하지 못했습니다: ${e.message ?? e.code}';
      });
    }
  }

  Future<void> _onMainButtonPressed() async {
    if (_loading || _changing) return;

    /*
     * 접근성 권한 자체가 없으면 최초 1회 시스템 설정이 필요하다.
     */
    if (!_permissionEnabled) {
      try {
        await _channel.invokeMethod<void>('openAccessibilitySettings');
      } on PlatformException catch (e) {
        if (!mounted) return;

        setState(() {
          _message = '접근성 설정을 열지 못했습니다: ${e.message ?? e.code}';
        });
      }
      return;
    }

    /*
     * 권한이 이미 허용된 이후부터는 시스템 설정으로 가지 않고
     * 앱 안에서 HALO 기능만 바로 ON/OFF 한다.
     */
    setState(() {
      _changing = true;
      _message = null;
    });

    final nextState = !_haloRunning;

    try {
      final success = await _channel.invokeMethod<bool>(
            'setHaloRunning',
            <String, dynamic>{
              'enabled': nextState,
            },
          ) ??
          false;

      if (!mounted) return;

      if (!success) {
        setState(() {
          _message = nextState
              ? 'HALO를 켜지 못했습니다. 접근성 권한 상태를 확인해주세요.'
              : 'HALO를 끄지 못했습니다.';
        });
      }

      await Future<void>.delayed(const Duration(milliseconds: 250));
      await _refreshStatus();
    } on PlatformException catch (e) {
      if (!mounted) return;

      setState(() {
        _message = '상태 변경에 실패했습니다: ${e.message ?? e.code}';
      });
    } finally {
      if (mounted) {
        setState(() {
          _changing = false;
        });
      }
    }
  }

  Future<void> _openPermissionSettings() async {
    try {
      await _channel.invokeMethod<void>('openAccessibilitySettings');
    } on PlatformException catch (e) {
      if (!mounted) return;

      setState(() {
        _message = '접근성 설정을 열지 못했습니다: ${e.message ?? e.code}';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final horizontalPadding =
        MediaQuery.of(context).size.width < 390 ? 22.0 : 28.0;

    return Scaffold(
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: _refreshStatus,
          color: haloYellow,
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: EdgeInsets.fromLTRB(
              horizontalPadding,
              30,
              horizontalPadding,
              34,
            ),
            children: [
              _header(),
              const SizedBox(height: 34),
              _statusCard(),
              const SizedBox(height: 18),
              _mainButton(),
              if (_message != null) ...[
                const SizedBox(height: 16),
                _messageCard(),
              ],
              const SizedBox(height: 20),
              _permissionCard(),
              const SizedBox(height: 18),
              _usageCard(),
              const SizedBox(height: 18),
              _featuresCard(),
              const SizedBox(height: 18),
              _privacyCard(),
              const SizedBox(height: 24),
              TextButton.icon(
                onPressed: _changing ? null : _refreshStatus,
                icon: const Icon(Icons.refresh_rounded),
                label: const Text('상태 다시 확인'),
                style: TextButton.styleFrom(
                  foregroundColor: Colors.white70,
                  textStyle: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _header() {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        const Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'HaloVision',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 38,
                  fontWeight: FontWeight.w900,
                  letterSpacing: -1.3,
                ),
              ),
              SizedBox(height: 8),
              Text(
                '기존 앱을 그대로 사용하면서\n필요한 순간에 HALO를 불러보세요.',
                style: TextStyle(
                  color: secondaryText,
                  fontSize: 18,
                  height: 1.45,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(width: 18),
        Container(
          width: 72,
          height: 72,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: haloBlue,
            shape: BoxShape.circle,
            border: Border.all(
              color: const Color(0xFF86C8FF),
              width: 4,
            ),
          ),
          child: const Text(
            'HALO',
            style: TextStyle(
              color: Colors.white,
              fontSize: 17,
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
      ],
    );
  }

  Widget _statusCard() {
    final Color statusColor;
    final String title;
    final String description;

    if (_loading) {
      statusColor = Colors.grey;
      title = '상태 확인 중...';
      description = 'HaloVision 상태를 확인하고 있습니다.';
    } else if (!_permissionEnabled) {
      statusColor = haloYellow;
      title = '처음 사용 설정이 필요합니다';
      description =
          '접근성 권한을 한 번 허용하면 이후에는 이 앱에서 HaloVision을 바로 켜고 끌 수 있습니다.';
    } else if (_haloRunning) {
      statusColor = runningGreen;
      title = 'HaloVision 실행 중';
      description =
          'HALO 버튼이 활성화되어 있습니다. 다른 앱으로 이동해 바로 사용할 수 있습니다.';
    } else {
      statusColor = offRed;
      title = 'HaloVision 꺼짐';
      description =
          'HALO 버튼과 화면 분석 기능이 꺼져 있습니다. 접근성 권한만 유지되어 다시 켤 때 설정 화면으로 갈 필요가 없습니다.';
    }

    return Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: cardColor,
        borderRadius: BorderRadius.circular(26),
        border: Border.all(
          color: statusColor.withOpacity(0.70),
          width: 1.5,
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 18,
            height: 18,
            margin: const EdgeInsets.only(top: 5),
            decoration: BoxDecoration(
              color: statusColor,
              shape: BoxShape.circle,
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 22,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  description,
                  style: const TextStyle(
                    color: secondaryText,
                    fontSize: 16,
                    height: 1.5,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _mainButton() {
    final String label;
    final Color background;
    final Color foreground;
    final IconData icon;

    if (!_permissionEnabled) {
      label = 'HaloVision 처음 켜기';
      background = haloYellow;
      foreground = Colors.black;
      icon = Icons.accessibility_new_rounded;
    } else if (_haloRunning) {
      label = 'HaloVision 끄기';
      background = const Color(0xFF9F3333);
      foreground = Colors.white;
      icon = Icons.power_settings_new_rounded;
    } else {
      label = 'HaloVision 켜기';
      background = runningGreen;
      foreground = Colors.black;
      icon = Icons.power_settings_new_rounded;
    }

    return SizedBox(
      height: 68,
      child: FilledButton.icon(
        onPressed:
            (_loading || _changing) ? null : _onMainButtonPressed,
        icon: _changing
            ? const SizedBox(
                width: 23,
                height: 23,
                child: CircularProgressIndicator(strokeWidth: 3),
              )
            : Icon(icon, size: 27),
        label: Text(
          _changing ? '처리 중...' : label,
          style: const TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.w900,
          ),
        ),
        style: FilledButton.styleFrom(
          backgroundColor: background,
          foregroundColor: foreground,
          disabledBackgroundColor: Colors.grey.shade800,
          disabledForegroundColor: Colors.grey.shade500,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(22),
          ),
        ),
      ),
    );
  }

  Widget _permissionCard() {
    final permissionText =
        _permissionEnabled ? '허용됨' : '허용 필요';

    return _sectionCard(
      icon: Icons.admin_panel_settings_outlined,
      title: '접근성 권한',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '현재 상태: $permissionText',
            style: TextStyle(
              color: _permissionEnabled ? runningGreen : haloYellow,
              fontSize: 17,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            '권한을 완전히 해제하거나 다시 허용하려면 Android 접근성 설정에서 변경할 수 있습니다.',
            style: TextStyle(
              color: secondaryText,
              fontSize: 15,
              height: 1.5,
            ),
          ),
          const SizedBox(height: 14),
          OutlinedButton.icon(
            onPressed: _openPermissionSettings,
            icon: const Icon(Icons.settings_accessibility_rounded),
            label: const Text('접근성 권한 관리'),
            style: OutlinedButton.styleFrom(
              foregroundColor: Colors.white,
              side: const BorderSide(
                color: Color(0xFF555555),
              ),
              padding: const EdgeInsets.symmetric(
                horizontal: 16,
                vertical: 13,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _usageCard() {
    return _sectionCard(
      icon: Icons.touch_app_rounded,
      title: '사용 방법',
      child: const Text(
        'HaloVision을 켠 뒤 네이버, 쿠팡, 카카오톡, 카메라 등 원하는 앱을 사용하세요. '
        '보기 어려운 순간 화면의 HALO 버튼을 누르면 됩니다.',
        style: TextStyle(
          color: secondaryText,
          fontSize: 16,
          height: 1.55,
        ),
      ),
    );
  }

  Widget _featuresCard() {
    return _sectionCard(
      icon: Icons.auto_awesome_rounded,
      title: 'HALO 기능',
      child: const Column(
        children: [
          _FeatureRow(
            icon: '🔍',
            title: '보기 편하게',
            description: '돋보기 또는 큰 글자로 확인',
          ),
          Divider(height: 26, color: Color(0xFF303030)),
          _FeatureRow(
            icon: '★',
            title: '중요한 내용만',
            description: '현재 화면의 핵심 정보만 간단하게',
          ),
          Divider(height: 26, color: Color(0xFF303030)),
          _FeatureRow(
            icon: '🔊',
            title: '읽어줘',
            description: '현재 화면의 글자를 음성으로 확인',
          ),
          Divider(height: 26, color: Color(0xFF303030)),
          _FeatureRow(
            icon: '💬',
            title: '질문하기',
            description: '화면 질문 및 버튼 위치 찾기',
          ),
        ],
      ),
    );
  }

  Widget _privacyCard() {
    return _sectionCard(
      icon: Icons.shield_outlined,
      title: '화면 확인 방식',
      child: const Text(
        'HaloVision은 계속 화면을 촬영하지 않습니다. '
        'HALO 기능을 실행한 순간에만 필요한 화면 정보를 확인합니다. '
        'HaloVision을 끄면 HALO 버튼과 화면 분석 기능도 사용할 수 없습니다.',
        style: TextStyle(
          color: secondaryText,
          fontSize: 15,
          height: 1.55,
        ),
      ),
    );
  }

  Widget _messageCard() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF2A2020),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: Colors.redAccent.withOpacity(0.55),
        ),
      ),
      child: Text(
        _message!,
        style: const TextStyle(
          color: Colors.white,
          fontSize: 15,
          height: 1.45,
        ),
      ),
    );
  }

  Widget _sectionCard({
    required IconData icon,
    required String title,
    required Widget child,
  }) {
    return Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: cardColor,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(
          color: const Color(0xFF303030),
          width: 1.2,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                icon,
                color: haloYellow,
                size: 25,
              ),
              const SizedBox(width: 10),
              Text(
                title,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 21,
                  fontWeight: FontWeight.w900,
                ),
              ),
            ],
          ),
          const SizedBox(height: 18),
          child,
        ],
      ),
    );
  }
}

class _FeatureRow extends StatelessWidget {
  final String icon;
  final String title;
  final String description;

  const _FeatureRow({
    required this.icon,
    required this.title,
    required this.description,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        SizedBox(
          width: 42,
          child: Text(
            icon,
            style: const TextStyle(fontSize: 25),
          ),
        ),
        const SizedBox(width: 6),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 17,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 3),
              Text(
                description,
                style: const TextStyle(
                  color: Color(0xFFB0B0B0),
                  fontSize: 14,
                  height: 1.35,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
