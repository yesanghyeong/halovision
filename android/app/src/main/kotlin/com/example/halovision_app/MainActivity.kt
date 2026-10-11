package com.example.halovision_app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    companion object {
        private const val TAG =
            "HALO_MAIN"

        private const val CHANNEL =
            "com.example.halovision_app/accessibility"

        private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS =
            "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
    }

    override fun configureFlutterEngine(
        flutterEngine: FlutterEngine
    ) {
        super.configureFlutterEngine(
            flutterEngine
        )

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL
        ).setMethodCallHandler { call, result ->

            when (
                call.method
            ) {

                "isAccessibilityServiceEnabled" -> {
                    result.success(
                        isHaloAccessibilityServiceEnabled()
                    )
                }

                "isHaloRunning" -> {
                    result.success(
                        HaloAccessibilityService
                            .isHaloRunningPreference(
                                this
                            )
                    )
                }

                "setHaloRunning" -> {
                    val enabled =
                        call.argument<Boolean>(
                            "enabled"
                        )

                    if (
                        enabled == null
                    ) {
                        result.error(
                            "INVALID_ARGUMENT",
                            "enabled 값이 필요합니다.",
                            null
                        )

                        return@setMethodCallHandler
                    }

                    /*
                     * 접근성 권한이 없는 상태에서 HALO 기능만 켜는 것은 불가능.
                     */
                    if (
                        enabled &&
                        !isHaloAccessibilityServiceEnabled()
                    ) {
                        result.success(
                            false
                        )

                        return@setMethodCallHandler
                    }

                    val success =
                        HaloAccessibilityService
                            .setHaloRunningFromApp(
                                context = this,
                                enabled = enabled
                            )

                    result.success(
                        success
                    )
                }

                "openAccessibilitySettings" -> {
                    try {
                        openHaloAccessibilitySettings()

                        result.success(
                            null
                        )
                    } catch (
                        e: Exception
                    ) {
                        result.error(
                            "OPEN_ACCESSIBILITY_SETTINGS_FAILED",
                            e.message,
                            null
                        )
                    }
                }

                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    // =========================================================
    // HaloVision 접근성 서비스 설정 화면
    //
    // 기기가 상세 화면 직접 진입을 허용하면 시도하고,
    // Samsung One UI 등에서 제한되면 설치된 접근성 앱 화면으로
    // 자동 fallback 된다.
    // =========================================================

    private fun openHaloAccessibilitySettings() {

        val haloComponent =
            ComponentName(
                this,
                HaloAccessibilityService::class.java
            )

        val directIntent =
            Intent(
                ACTION_ACCESSIBILITY_DETAILS_SETTINGS
            ).apply {
                putExtra(
                    Intent.EXTRA_COMPONENT_NAME,
                    haloComponent.flattenToString()
                )

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        try {
            Log.d(
                TAG,
                "HaloVision 접근성 상세 화면 직접 이동 시도"
            )

            startActivity(
                directIntent
            )

            return

        } catch (
            e: SecurityException
        ) {
            Log.w(
                TAG,
                "상세 화면 직접 이동 차단됨",
                e
            )

        } catch (
            e: ActivityNotFoundException
        ) {
            Log.w(
                TAG,
                "상세 접근성 Activity 없음",
                e
            )

        } catch (
            e: Exception
        ) {
            Log.w(
                TAG,
                "상세 화면 직접 이동 실패",
                e
            )
        }

        val fallbackIntent =
            Intent(
                Settings.ACTION_ACCESSIBILITY_SETTINGS
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        startActivity(
            fallbackIntent
        )
    }

    // =========================================================
    // Android 접근성 권한 활성화 여부
    // =========================================================

    private fun isHaloAccessibilityServiceEnabled(): Boolean {

        val manager =
            getSystemService(
                ACCESSIBILITY_SERVICE
            ) as AccessibilityManager

        val enabledServices =
            manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )

        val targetPackage =
            packageName

        val targetClass =
            HaloAccessibilityService::class.java.name

        return enabledServices.any { info ->

            val serviceInfo =
                info.resolveInfo
                    ?.serviceInfo

            serviceInfo != null &&
                serviceInfo.packageName ==
                    targetPackage &&
                serviceInfo.name ==
                    targetClass
        }
    }
}
