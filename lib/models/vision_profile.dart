import 'dart:convert';
import 'dart:ui';

enum VisionType {
  centralLoss,
  peripheralLoss,
  blurred,
  colorDifficulty,
  unknown,
  custom,
}

enum ColorCorrectionMode {
  normal,
  highContrast,
  redGreenAssist,
  blueYellowAssist,
}

enum RegionShapeType {
  circle,
  ellipse,
  rectangle,
  custom,
}

class RegionShape {
  final RegionShapeType type;

  // 화면 전체 크기 기준 비율값 (0.0 ~ 1.0)
  final double x;
  final double y;
  final double width;
  final double height;

  // 자유형 영역용 좌표
  final List<Offset> points;

  const RegionShape({
    required this.type,
    required this.x,
    required this.y,
    required this.width,
    required this.height,
    this.points = const [],
  });

  RegionShape copyWith({
    RegionShapeType? type,
    double? x,
    double? y,
    double? width,
    double? height,
    List<Offset>? points,
  }) {
    return RegionShape(
      type: type ?? this.type,
      x: x ?? this.x,
      y: y ?? this.y,
      width: width ?? this.width,
      height: height ?? this.height,
      points: points ?? this.points,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'type': type.name,
      'x': x,
      'y': y,
      'width': width,
      'height': height,
      'points': points
          .map(
            (point) => {
              'x': point.dx,
              'y': point.dy,
            },
          )
          .toList(),
    };
  }

  factory RegionShape.fromJson(Map<String, dynamic> json) {
    return RegionShape(
      type: RegionShapeType.values.firstWhere(
        (e) => e.name == json['type'],
        orElse: () => RegionShapeType.circle,
      ),
      x: (json['x'] ?? 0.0).toDouble(),
      y: (json['y'] ?? 0.0).toDouble(),
      width: (json['width'] ?? 0.0).toDouble(),
      height: (json['height'] ?? 0.0).toDouble(),
      points: ((json['points'] ?? []) as List)
          .map(
            (point) => Offset(
              (point['x'] ?? 0.0).toDouble(),
              (point['y'] ?? 0.0).toDouble(),
            ),
          )
          .toList(),
    );
  }
}

class LayoutRegion {
  final double x;
  final double y;
  final double width;
  final double height;

  const LayoutRegion({
    required this.x,
    required this.y,
    required this.width,
    required this.height,
  });

  LayoutRegion copyWith({
    double? x,
    double? y,
    double? width,
    double? height,
  }) {
    return LayoutRegion(
      x: x ?? this.x,
      y: y ?? this.y,
      width: width ?? this.width,
      height: height ?? this.height,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'x': x,
      'y': y,
      'width': width,
      'height': height,
    };
  }

  factory LayoutRegion.fromJson(Map<String, dynamic> json) {
    return LayoutRegion(
      x: (json['x'] ?? 0.0).toDouble(),
      y: (json['y'] ?? 0.0).toDouble(),
      width: (json['width'] ?? 0.0).toDouble(),
      height: (json['height'] ?? 0.0).toDouble(),
    );
  }
}

class VisionProfile {
  final VisionType visionType;

  final List<RegionShape> avoidRegions;
  final List<RegionShape> visibleRegions;

  final LayoutRegion? textRegion;
  final LayoutRegion? mediaRegion;

  final double brightness;
  final double contrast;
  final double sharpness;

  final double fontScale;
  final double lineSpacing;
  final bool boldText;

  final ColorCorrectionMode colorCorrectionMode;

  const VisionProfile({
    required this.visionType,
    this.avoidRegions = const [],
    this.visibleRegions = const [],
    this.textRegion,
    this.mediaRegion,
    this.brightness = 1.0,
    this.contrast = 1.0,
    this.sharpness = 1.0,
    this.fontScale = 1.0,
    this.lineSpacing = 1.4,
    this.boldText = false,
    this.colorCorrectionMode = ColorCorrectionMode.normal,
  });

  factory VisionProfile.defaultProfile() {
    return const VisionProfile(
      visionType: VisionType.unknown,
    );
  }

  VisionProfile copyWith({
    VisionType? visionType,
    List<RegionShape>? avoidRegions,
    List<RegionShape>? visibleRegions,
    LayoutRegion? textRegion,
    LayoutRegion? mediaRegion,
    double? brightness,
    double? contrast,
    double? sharpness,
    double? fontScale,
    double? lineSpacing,
    bool? boldText,
    ColorCorrectionMode? colorCorrectionMode,
  }) {
    return VisionProfile(
      visionType: visionType ?? this.visionType,
      avoidRegions: avoidRegions ?? this.avoidRegions,
      visibleRegions: visibleRegions ?? this.visibleRegions,
      textRegion: textRegion ?? this.textRegion,
      mediaRegion: mediaRegion ?? this.mediaRegion,
      brightness: brightness ?? this.brightness,
      contrast: contrast ?? this.contrast,
      sharpness: sharpness ?? this.sharpness,
      fontScale: fontScale ?? this.fontScale,
      lineSpacing: lineSpacing ?? this.lineSpacing,
      boldText: boldText ?? this.boldText,
      colorCorrectionMode:
          colorCorrectionMode ?? this.colorCorrectionMode,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'visionType': visionType.name,
      'avoidRegions':
          avoidRegions.map((region) => region.toJson()).toList(),
      'visibleRegions':
          visibleRegions.map((region) => region.toJson()).toList(),
      'textRegion': textRegion?.toJson(),
      'mediaRegion': mediaRegion?.toJson(),
      'brightness': brightness,
      'contrast': contrast,
      'sharpness': sharpness,
      'fontScale': fontScale,
      'lineSpacing': lineSpacing,
      'boldText': boldText,
      'colorCorrectionMode': colorCorrectionMode.name,
    };
  }

  factory VisionProfile.fromJson(Map<String, dynamic> json) {
    return VisionProfile(
      visionType: VisionType.values.firstWhere(
        (e) => e.name == json['visionType'],
        orElse: () => VisionType.unknown,
      ),
      avoidRegions: ((json['avoidRegions'] ?? []) as List)
          .map(
            (region) => RegionShape.fromJson(
              Map<String, dynamic>.from(region),
            ),
          )
          .toList(),
      visibleRegions: ((json['visibleRegions'] ?? []) as List)
          .map(
            (region) => RegionShape.fromJson(
              Map<String, dynamic>.from(region),
            ),
          )
          .toList(),
      textRegion: json['textRegion'] == null
          ? null
          : LayoutRegion.fromJson(
              Map<String, dynamic>.from(json['textRegion']),
            ),
      mediaRegion: json['mediaRegion'] == null
          ? null
          : LayoutRegion.fromJson(
              Map<String, dynamic>.from(json['mediaRegion']),
            ),
      brightness: (json['brightness'] ?? 1.0).toDouble(),
      contrast: (json['contrast'] ?? 1.0).toDouble(),
      sharpness: (json['sharpness'] ?? 1.0).toDouble(),
      fontScale: (json['fontScale'] ?? 1.0).toDouble(),
      lineSpacing: (json['lineSpacing'] ?? 1.4).toDouble(),
      boldText: json['boldText'] ?? false,
      colorCorrectionMode: ColorCorrectionMode.values.firstWhere(
        (e) => e.name == json['colorCorrectionMode'],
        orElse: () => ColorCorrectionMode.normal,
      ),
    );
  }

  String encode() {
    return jsonEncode(toJson());
  }

  factory VisionProfile.decode(String source) {
    return VisionProfile.fromJson(
      Map<String, dynamic>.from(
        jsonDecode(source),
      ),
    );
  }
}