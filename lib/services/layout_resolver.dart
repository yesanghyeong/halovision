import '../models/vision_profile.dart';

class ResolvedLayout {
  final LayoutRegion textRegion;
  final LayoutRegion mediaRegion;

  const ResolvedLayout({
    required this.textRegion,
    required this.mediaRegion,
  });
}

class LayoutResolver {
  static ResolvedLayout resolve(
    VisionProfile profile,
  ) {
    final defaultText =
        profile.textRegion ??
        const LayoutRegion(
          x: 0.07,
          y: 0.52,
          width: 0.86,
          height: 0.34,
        );

    final defaultMedia =
        profile.mediaRegion ??
        const LayoutRegion(
          x: 0.56,
          y: 0.12,
          width: 0.34,
          height: 0.26,
        );

    LayoutRegion text = defaultText;
    LayoutRegion media = defaultMedia;

    final useVisibleRegion =
        profile.visibleRegions.isNotEmpty &&
        (profile.visionType ==
                VisionType.peripheralLoss ||
            profile.visionType ==
                VisionType.custom);

    if (useVisibleRegion) {
      final visible =
          profile.visibleRegions.first;

      text = _fitInsideVisibleRegion(
        text,
        visible,
        preferredWidth: 0.86,
        preferredHeight: 0.32,
      );

      media = _fitInsideVisibleRegion(
        media,
        visible,
        preferredWidth: 0.34,
        preferredHeight: 0.26,
      );

      if (_overlaps(text, media)) {
        final split =
            _splitVisibleRegion(
          visible,
        );

        text = split.textRegion;
        media = split.mediaRegion;
      }

      return ResolvedLayout(
        textRegion: text,
        mediaRegion: media,
      );
    }

    if (profile.avoidRegions.isNotEmpty) {
      for (final avoid
          in profile.avoidRegions) {
        if (_overlapsRegion(
          text,
          avoid,
        )) {
          text = _moveAwayFromAvoid(
            text,
            avoid,
          );
        }

        if (_overlapsRegion(
          media,
          avoid,
        )) {
          media = _moveAwayFromAvoid(
            media,
            avoid,
          );
        }
      }

      if (_overlaps(text, media)) {
        media = _findMediaAlternative(
          media,
          text,
          profile.avoidRegions,
        );
      }
    }

    return ResolvedLayout(
      textRegion: _clamp(text),
      mediaRegion: _clamp(media),
    );
  }

  static LayoutRegion _fitInsideVisibleRegion(
    LayoutRegion original,
    RegionShape visible, {
    required double preferredWidth,
    required double preferredHeight,
  }) {
    const margin = 0.025;

    final availableWidth =
        (visible.width - margin * 2)
            .clamp(
      0.10,
      1.0,
    );

    final availableHeight =
        (visible.height - margin * 2)
            .clamp(
      0.10,
      1.0,
    );

    final width =
        original.width
            .clamp(
      0.10,
      availableWidth,
    );

    final height =
        original.height
            .clamp(
      0.08,
      availableHeight,
    );

    var x = original.x;
    var y = original.y;

    final minX =
        visible.x + margin;

    final minY =
        visible.y + margin;

    final maxX =
        visible.x +
        visible.width -
        width -
        margin;

    final maxY =
        visible.y +
        visible.height -
        height -
        margin;

    x = x.clamp(
      minX,
      maxX < minX
          ? minX
          : maxX,
    );

    y = y.clamp(
      minY,
      maxY < minY
          ? minY
          : maxY,
    );

    return LayoutRegion(
      x: x,
      y: y,
      width: width,
      height: height,
    );
  }

  static ResolvedLayout _splitVisibleRegion(
    RegionShape visible,
  ) {
    const margin = 0.03;
    const gap = 0.025;

    final innerX =
        visible.x + margin;

    final innerY =
        visible.y + margin;

    final innerWidth =
        (visible.width -
                margin * 2)
            .clamp(
      0.12,
      1.0,
    );

    final innerHeight =
        (visible.height -
                margin * 2)
            .clamp(
      0.12,
      1.0,
    );

    if (innerHeight >=
        innerWidth) {
      final textHeight =
          innerHeight * 0.58;

      final mediaHeight =
          innerHeight -
          textHeight -
          gap;

      return ResolvedLayout(
        textRegion: LayoutRegion(
          x: innerX,
          y: innerY,
          width: innerWidth,
          height: textHeight,
        ),
        mediaRegion: LayoutRegion(
          x: innerX,
          y: innerY +
              textHeight +
              gap,
          width: innerWidth,
          height: mediaHeight
              .clamp(
            0.08,
            1.0,
          ),
        ),
      );
    }

    final textWidth =
        innerWidth * 0.62;

    final mediaWidth =
        innerWidth -
        textWidth -
        gap;

    return ResolvedLayout(
      textRegion: LayoutRegion(
        x: innerX,
        y: innerY,
        width: textWidth,
        height: innerHeight,
      ),
      mediaRegion: LayoutRegion(
        x: innerX +
            textWidth +
            gap,
        y: innerY,
        width: mediaWidth
            .clamp(
          0.08,
          1.0,
        ),
        height: innerHeight,
      ),
    );
  }

  static LayoutRegion _moveAwayFromAvoid(
    LayoutRegion region,
    RegionShape avoid,
  ) {
    const margin = 0.035;

    final candidates =
        <LayoutRegion>[
      LayoutRegion(
        x: region.x,
        y: avoid.y -
            region.height -
            margin,
        width: region.width,
        height: region.height,
      ),

      LayoutRegion(
        x: region.x,
        y: avoid.y +
            avoid.height +
            margin,
        width: region.width,
        height: region.height,
      ),

      LayoutRegion(
        x: avoid.x -
            region.width -
            margin,
        y: region.y,
        width: region.width,
        height: region.height,
      ),

      LayoutRegion(
        x: avoid.x +
            avoid.width +
            margin,
        y: region.y,
        width: region.width,
        height: region.height,
      ),
    ];

    for (final candidate
        in candidates) {
      final clamped =
          _clamp(candidate);

      if (!_overlapsRegion(
        clamped,
        avoid,
      )) {
        return clamped;
      }
    }

    final reduced =
        LayoutRegion(
      x: region.x,
      y: region.y,
      width: region.width * 0.82,
      height: region.height * 0.82,
    );

    return _clamp(
      reduced,
    );
  }

  static LayoutRegion _findMediaAlternative(
    LayoutRegion media,
    LayoutRegion text,
    List<RegionShape> avoids,
  ) {
    final candidates =
        <LayoutRegion>[
      LayoutRegion(
        x: 0.06,
        y: 0.08,
        width: media.width,
        height: media.height,
      ),
      LayoutRegion(
        x: 0.60,
        y: 0.08,
        width: media.width,
        height: media.height,
      ),
      LayoutRegion(
        x: 0.06,
        y: 0.66,
        width: media.width,
        height: media.height,
      ),
      LayoutRegion(
        x: 0.60,
        y: 0.66,
        width: media.width,
        height: media.height,
      ),
    ];

    for (final candidate
        in candidates) {
      final clamped =
          _clamp(candidate);

      if (_overlaps(
        clamped,
        text,
      )) {
        continue;
      }

      var touchesAvoid = false;

      for (final avoid
          in avoids) {
        if (_overlapsRegion(
          clamped,
          avoid,
        )) {
          touchesAvoid = true;
          break;
        }
      }

      if (!touchesAvoid) {
        return clamped;
      }
    }

    return _clamp(media);
  }

  static bool _overlapsRegion(
    LayoutRegion layout,
    RegionShape region,
  ) {
    return _rectOverlaps(
      layout.x,
      layout.y,
      layout.width,
      layout.height,
      region.x,
      region.y,
      region.width,
      region.height,
    );
  }

  static bool _overlaps(
    LayoutRegion first,
    LayoutRegion second,
  ) {
    return _rectOverlaps(
      first.x,
      first.y,
      first.width,
      first.height,
      second.x,
      second.y,
      second.width,
      second.height,
    );
  }

  static bool _rectOverlaps(
    double ax,
    double ay,
    double aw,
    double ah,
    double bx,
    double by,
    double bw,
    double bh,
  ) {
    return ax < bx + bw &&
        ax + aw > bx &&
        ay < by + bh &&
        ay + ah > by;
  }

  static LayoutRegion _clamp(
    LayoutRegion region,
  ) {
    final width =
        region.width.clamp(
      0.08,
      0.96,
    );

    final height =
        region.height.clamp(
      0.08,
      0.90,
    );

    final x = region.x.clamp(
      0.0,
      1.0 - width,
    );

    final y = region.y.clamp(
      0.0,
      1.0 - height,
    );

    return LayoutRegion(
      x: x,
      y: y,
      width: width,
      height: height,
    );
  }
}