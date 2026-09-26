import 'package:flutter/material.dart';

class HaloPrimaryButton extends StatelessWidget {
  final String text;
  final VoidCallback? onPressed;
  final IconData? icon;
  final bool fullWidth;

  const HaloPrimaryButton({
    super.key,
    required this.text,
    required this.onPressed,
    this.icon,
    this.fullWidth = true,
  });

  @override
  Widget build(BuildContext context) {
    final button = ElevatedButton(
      onPressed: onPressed,
      style: ElevatedButton.styleFrom(
        backgroundColor: const Color(0xFFF4D35E),
        foregroundColor: Colors.black,
        disabledBackgroundColor: Colors.grey.shade800,
        disabledForegroundColor: Colors.grey.shade500,
        elevation: 0,
        minimumSize: const Size(0, 58),
        padding: const EdgeInsets.symmetric(
          horizontal: 22,
          vertical: 16,
        ),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(18),
        ),
        textStyle: const TextStyle(
          fontSize: 18,
          fontWeight: FontWeight.w800,
        ),
      ),
      child: icon == null
          ? Text(text)
          : Row(
              mainAxisSize: MainAxisSize.min,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(
                  icon,
                  size: 24,
                ),
                const SizedBox(width: 10),
                Text(text),
              ],
            ),
    );

    if (!fullWidth) {
      return button;
    }

    return SizedBox(
      width: double.infinity,
      child: button,
    );
  }
}