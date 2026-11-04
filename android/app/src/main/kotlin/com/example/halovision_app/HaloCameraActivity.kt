package com.example.halovision_app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

/**
 * HaloVision 전용 보기 전용 카메라.
 *
 * - 후면 카메라 Preview만 표시
 * - 촬영 버튼 없음
 * - 사진 저장 없음
 * - 갤러리 버튼 없음
 * - 기존 AccessibilityService의 HALO 버튼은 화면 위에 계속 표시
 */
class HaloCameraActivity : ComponentActivity() {

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 4102

        @Volatile
        private var activeInstance: HaloCameraActivity? = null

        fun isActive(): Boolean {
            return activeInstance != null
        }

        /**
         * Accessibility Screenshot과 별개로 CameraX Preview 자체의 현재 프레임을 복사한다.
         * XCover5처럼 기기별 Screenshot 합성 결과가 다른 경우의 OCR 안정성을 높인다.
         */
        fun captureCurrentPreviewBitmap(
            onReady: (Bitmap?) -> Unit
        ) {
            val activity =
                activeInstance

            if (activity == null) {
                onReady(null)
                return
            }

            activity.runOnUiThread {
                val previewBitmap =
                    try {
                        activity.previewView.bitmap
                    } catch (_: Exception) {
                        null
                    }

                val safeCopy =
                    try {
                        previewBitmap
                            ?.copy(
                                Bitmap.Config.ARGB_8888,
                                false
                            )
                    } catch (_: Exception) {
                        null
                    }

                if (
                    previewBitmap != null &&
                    previewBitmap !== safeCopy
                ) {
                    try {
                        previewBitmap.recycle()
                    } catch (_: Exception) {
                    }
                }

                onReady(
                    safeCopy
                )
            }
        }
    }

    private lateinit var previewView: PreviewView
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val root =
            FrameLayout(this).apply {
                setBackgroundColor(
                    Color.BLACK
                )
            }

        previewView =
            PreviewView(this).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                /*
                 * PERFORMANCE 모드의 SurfaceView보다
                 * AccessibilityService.takeScreenshot()과의 호환성을
                 * 우선하기 위해 TextureView 기반 COMPATIBLE 모드 사용.
                 */
                implementationMode =
                    PreviewView.ImplementationMode.COMPATIBLE

                scaleType =
                    PreviewView.ScaleType.FILL_CENTER

                importantForAccessibility =
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }

        root.addView(
            previewView
        )

        /*
         * 촬영 버튼은 없음.
         * 사용자가 카메라 화면을 빠져나올 수 있도록
         * 좌측 상단에 작은 닫기 버튼만 둔다.
         */
        val closeButton =
            ImageButton(this).apply {
                setImageResource(
                    android.R.drawable.ic_menu_close_clear_cancel
                )

                contentDescription =
                    "카메라 닫기"

                setColorFilter(
                    Color.WHITE
                )

                setPadding(
                    dp(12),
                    dp(12),
                    dp(12),
                    dp(12)
                )

                background =
                    GradientDrawable().apply {
                        shape =
                            GradientDrawable.OVAL

                        setColor(
                            0x99000000.toInt()
                        )
                    }

                setOnClickListener {
                    finish()
                }
            }

        root.addView(
            closeButton,
            FrameLayout.LayoutParams(
                dp(52),
                dp(52)
            ).apply {
                gravity =
                    Gravity.TOP or
                        Gravity.START

                topMargin =
                    dp(18)

                marginStart =
                    dp(18)
            }
        )

        setContentView(
            root
        )

        if (
            hasCameraPermission()
        ) {
            startCameraPreview()
        } else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.CAMERA
                ),
                CAMERA_PERMISSION_REQUEST
            )
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun startCameraPreview() {
        val providerFuture =
            ProcessCameraProvider.getInstance(
                this
            )

        providerFuture.addListener(
            {
                try {
                    val provider =
                        providerFuture.get()

                    cameraProvider =
                        provider

                    val preview =
                        Preview.Builder()
                            .build()
                            .also { cameraPreview ->
                                cameraPreview.setSurfaceProvider(
                                    previewView.surfaceProvider
                                )
                            }

                    provider.unbindAll()

                    /*
                     * Preview만 바인딩한다.
                     * ImageCapture / VideoCapture를 사용하지 않으므로
                     * 사진이나 동영상이 저장되지 않는다.
                     */
                    provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview
                    )
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "카메라 화면을 시작할 수 없습니다.",
                        Toast.LENGTH_LONG
                    ).show()

                    finish()
                }
            },
            ContextCompat.getMainExecutor(
                this
            )
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode !=
            CAMERA_PERMISSION_REQUEST
        ) {
            return
        }

        if (
            grantResults.isNotEmpty() &&
            grantResults[0] ==
                PackageManager.PERMISSION_GRANTED
        ) {
            startCameraPreview()
        } else {
            Toast.makeText(
                this,
                "카메라 기능을 사용하려면 카메라 권한이 필요합니다.",
                Toast.LENGTH_LONG
            ).show()

            finish()
        }
    }

    override fun onStart() {
        super.onStart()

        activeInstance =
            this
    }

    override fun onStop() {
        if (
            activeInstance ===
            this
        ) {
            activeInstance =
                null
        }

        super.onStop()
    }

    override fun onDestroy() {
        try {
            cameraProvider
                ?.unbindAll()
        } catch (
            _: Exception
        ) {
        }

        cameraProvider =
            null

        super.onDestroy()
    }

    private fun dp(
        value: Int
    ): Int {
        return (
            value *
                resources.displayMetrics.density
            ).toInt()
    }
}
