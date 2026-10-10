package com.example.halovision_app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.MagnificationConfig
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Region
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs


class HaloAccessibilityService : AccessibilityService() {

    companion object {

        private const val TAG = "HALO_ACCESS"

        private const val DEFAULT_TEXT_SIZE_SP = 32f
        private const val MIN_TEXT_SIZE_SP = 30f
        private const val MAX_TEXT_SIZE_SP = 72f
        private const val TEXT_SIZE_STEP_SP = 4f

        private const val DEFAULT_MAGNIFIER_SCALE = 2.0f

        private const val SERVER_BASE_URL =
            "http://127.0.0.1:8000"

        // =====================================================
        // Vision Profile SharedPreferences
        // =====================================================

        private const val VISION_PROFILE_PREFS =
            "halo_vision_profile"

        private const val KEY_PROFILE_TEXT_SIZE =
            "profile_text_size"

        private const val KEY_PROFILE_MAGNIFIER_SCALE =
            "profile_magnifier_scale"

        private const val KEY_AUTO_READ_QUESTION =
            "auto_read_question"

        private const val KEY_AUTO_READ_IMPORTANT =
            "auto_read_important"

        // 돋보기가 쓰레기통 위에서 움직임이 멈춘 뒤
        // 종료되기까지의 시간
        private const val MAGNIFIER_DISMISS_DELAY_MS =
            300L
    }


    // =========================================================
    // 기본 객체
    // =========================================================

    private lateinit var windowManager: WindowManager

    private val mainHandler =
        Handler(Looper.getMainLooper())


    // =========================================================
    // Overlay
    // =========================================================

    private var floatingButton: TextView? = null

    private var actionMenu: LinearLayout? = null

    private var resultPanel: LinearLayout? = null


    // =========================================================
    // 현재 화면 Context
    // =========================================================

    private var currentScreenLines: List<String> =
        emptyList()


    // =========================================================
    // Vision Profile
    // =========================================================

    private var profileTextSizeSp =
        DEFAULT_TEXT_SIZE_SP

    private var profileMagnifierScale =
        DEFAULT_MAGNIFIER_SCALE

    private var autoReadQuestionAnswer =
        true

    private var autoReadImportantResult =
        false


    // 현재 열린 큰 글자 화면에서 사용하는 크기
    private var readableTextSizeSp =
        DEFAULT_TEXT_SIZE_SP


    // =========================================================
    // TTS
    // =========================================================

    private var textToSpeech: TextToSpeech? = null

    private var isTtsReady =
        false


    // =========================================================
    // Speech Recognition
    // =========================================================

    private var speechRecognizer: SpeechRecognizer? = null

    private var isListeningForQuestion =
        false


    // =========================================================
    // Window Magnifier
    // =========================================================

    private var magnifierTrashView: TextView? = null

    private var isHaloWindowMagnifierActive =
        false

    private var isMagnifierOverTrash =
        false

    private var magnificationChangedListener:
        AccessibilityService
            .MagnificationController
            .OnMagnificationChangedListener? = null


    private val magnifierDismissRunnable =
        Runnable {

            if (
                isHaloWindowMagnifierActive &&
                isMagnifierOverTrash
            ) {

                stopWindowMagnifier()
            }
        }


    // =========================================================
    // AccessibilityService 시작
    // =========================================================

    override fun onServiceConnected() {

        super.onServiceConnected()

        Log.d(
            TAG,
            "Halo AccessibilityService 연결됨"
        )

        windowManager =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager


        // 사용자 설정 먼저 불러오기
        loadVisionProfile()

        initializeTextToSpeech()

        initializeSpeechRecognizer()

        initializeMagnificationListener()

        showFloatingHaloButton()
    }


    // =========================================================
    // 자동 화면 분석 안 함
    // =========================================================

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {

        // 사용자가 HALO 버튼을 눌렀을 때만
        // 화면 정보를 가져온다.
    }


    override fun onInterrupt() {

        Log.d(
            TAG,
            "Halo AccessibilityService 중단됨"
        )
    }


    // =========================================================
    // 서비스 종료
    // =========================================================

    override fun onDestroy() {

        mainHandler.removeCallbacks(
            magnifierDismissRunnable
        )

        hideMagnifierTrashZone()


        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            magnificationChangedListener?.let { listener ->

                try {

                    magnificationController.removeListener(
                        listener
                    )

                } catch (_: Exception) {
                }
            }
        }


        magnificationChangedListener =
            null


        removeActionMenu()

        removeResultPanel()


        floatingButton?.let { button ->

            try {

                if (::windowManager.isInitialized) {

                    windowManager.removeView(
                        button
                    )
                }

            } catch (_: Exception) {
            }
        }


        floatingButton =
            null


        // TTS 종료

        textToSpeech?.stop()

        textToSpeech?.shutdown()

        textToSpeech =
            null

        isTtsReady =
            false


        // SpeechRecognizer 종료

        speechRecognizer?.cancel()

        speechRecognizer?.destroy()

        speechRecognizer =
            null

        isListeningForQuestion =
            false


        super.onDestroy()
    }


    // =========================================================
    // Vision Profile 불러오기
    // =========================================================

    private fun loadVisionProfile() {

        val prefs =
            getSharedPreferences(
                VISION_PROFILE_PREFS,
                MODE_PRIVATE
            )


        profileTextSizeSp =
            prefs.getFloat(
                KEY_PROFILE_TEXT_SIZE,
                DEFAULT_TEXT_SIZE_SP
            ).coerceIn(
                MIN_TEXT_SIZE_SP,
                MAX_TEXT_SIZE_SP
            )


        profileMagnifierScale =
            prefs.getFloat(
                KEY_PROFILE_MAGNIFIER_SCALE,
                DEFAULT_MAGNIFIER_SCALE
            ).coerceIn(
                1.5f,
                4.0f
            )


        autoReadQuestionAnswer =
            prefs.getBoolean(
                KEY_AUTO_READ_QUESTION,
                true
            )


        autoReadImportantResult =
            prefs.getBoolean(
                KEY_AUTO_READ_IMPORTANT,
                false
            )


        readableTextSizeSp =
            profileTextSizeSp


        Log.d(
            TAG,
            """
            Vision Profile 로드 완료
            글자 크기: ${profileTextSizeSp}sp
            돋보기: ${profileMagnifierScale}배
            질문 자동 읽기: $autoReadQuestionAnswer
            중요 정보 자동 읽기: $autoReadImportantResult
            """.trimIndent()
        )
    }


    // =========================================================
    // Vision Profile 저장
    // =========================================================

    private fun saveVisionProfile(
        textSizeSp: Float,
        magnifierScale: Float,
        readQuestionAutomatically: Boolean,
        readImportantAutomatically: Boolean
    ) {

        val safeTextSize =
            textSizeSp.coerceIn(
                MIN_TEXT_SIZE_SP,
                MAX_TEXT_SIZE_SP
            )


        val safeMagnifierScale =
            magnifierScale.coerceIn(
                1.5f,
                4.0f
            )


        val prefs =
            getSharedPreferences(
                VISION_PROFILE_PREFS,
                MODE_PRIVATE
            )


        prefs.edit()
            .putFloat(
                KEY_PROFILE_TEXT_SIZE,
                safeTextSize
            )
            .putFloat(
                KEY_PROFILE_MAGNIFIER_SCALE,
                safeMagnifierScale
            )
            .putBoolean(
                KEY_AUTO_READ_QUESTION,
                readQuestionAutomatically
            )
            .putBoolean(
                KEY_AUTO_READ_IMPORTANT,
                readImportantAutomatically
            )
            .apply()


        profileTextSizeSp =
            safeTextSize

        profileMagnifierScale =
            safeMagnifierScale

        autoReadQuestionAnswer =
            readQuestionAutomatically

        autoReadImportantResult =
            readImportantAutomatically

        readableTextSizeSp =
            safeTextSize


        Log.d(
            TAG,
            "Vision Profile 저장 완료"
        )
    }


    // =========================================================
    // HALO 플로팅 버튼
    // =========================================================

    private fun showFloatingHaloButton() {

        if (
            floatingButton != null
        ) {
            return
        }


        val button =
            TextView(this).apply {

                text =
                    "HALO"

                textSize =
                    13f

                setTextColor(
                    Color.WHITE
                )

                gravity =
                    Gravity.CENTER


                background =
                    GradientDrawable().apply {

                        shape =
                            GradientDrawable.OVAL


                        setColor(
                            Color.rgb(
                                42,
                                87,
                                190
                            )
                        )


                        setStroke(
                            dp(3),
                            Color.rgb(
                                110,
                                190,
                                255
                            )
                        )
                    }
            }


        val params =
            WindowManager.LayoutParams(
                dp(64),
                dp(64),
                WindowManager.LayoutParams
                    .TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams
                    .FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )


        params.gravity =
            Gravity.TOP or Gravity.END

        params.x =
            dp(16)

        params.y =
            dp(250)


        var initialX = 0
        var initialY = 0

        var initialTouchX = 0f
        var initialTouchY = 0f

        var moved =
            false


        button.setOnTouchListener { _, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    initialX =
                        params.x

                    initialY =
                        params.y

                    initialTouchX =
                        event.rawX

                    initialTouchY =
                        event.rawY

                    moved =
                        false

                    true
                }


                MotionEvent.ACTION_MOVE -> {

                    val dx =
                        (
                            event.rawX -
                                initialTouchX
                            ).toInt()

                    val dy =
                        (
                            event.rawY -
                                initialTouchY
                            ).toInt()


                    if (
                        abs(dx) > 10 ||
                        abs(dy) > 10
                    ) {

                        moved =
                            true
                    }


                    params.x =
                        initialX - dx

                    params.y =
                        initialY + dy


                    try {

                        windowManager.updateViewLayout(
                            button,
                            params
                        )

                    } catch (e: Exception) {

                        Log.e(
                            TAG,
                            "HALO 버튼 이동 오류",
                            e
                        )
                    }


                    true
                }


                MotionEvent.ACTION_UP -> {

                    if (!moved) {

                        onHaloButtonClicked()
                    }

                    true
                }


                else -> false
            }
        }


        try {

            windowManager.addView(
                button,
                params
            )


            floatingButton =
                button


        } catch (e: Exception) {

            Log.e(
                TAG,
                "HALO 버튼 표시 실패",
                e
            )
        }
    }


    // =========================================================
    // HALO 버튼 클릭
    // =========================================================

    private fun onHaloButtonClicked() {

        textToSpeech?.stop()


        if (
            isListeningForQuestion
        ) {

            speechRecognizer?.cancel()

            isListeningForQuestion =
                false
        }


        removeActionMenu()

        removeResultPanel()


        currentScreenLines =
            captureCurrentScreen()


        Log.d(
            TAG,
            "현재 화면 텍스트 ${currentScreenLines.size}개 확보"
        )


        showActionMenu()
    }


    // =========================================================
    // 현재 화면 Node Tree
    // =========================================================

    private fun captureCurrentScreen():
        List<String> {

        val rootNode =
            rootInActiveWindow
                ?: return emptyList()


        val texts =
            mutableListOf<String>()


        collectTexts(
            rootNode,
            texts
        )


        return texts
            .map {
                it.trim()
            }
            .filter {
                it.isNotEmpty()
            }
            .distinct()
    }


    // =========================================================
    // Node Tree 텍스트 재귀 수집
    // =========================================================

    private fun collectTexts(
        node: AccessibilityNodeInfo?,
        result: MutableList<String>
    ) {

        if (
            node == null
        ) {
            return
        }


        val nodeText =
            node.text
                ?.toString()
                ?.trim()


        if (
            !nodeText.isNullOrEmpty()
        ) {

            result.add(
                nodeText
            )
        }


        val description =
            node.contentDescription
                ?.toString()
                ?.trim()


        if (
            !description.isNullOrEmpty()
        ) {

            result.add(
                description
            )
        }


        for (
            index in
            0 until node.childCount
        ) {

            collectTexts(
                node.getChild(
                    index
                ),
                result
            )
        }
    }


    // =========================================================
    // HALO 메인 메뉴
    // =========================================================

    private fun showActionMenu() {

        removeActionMenu()

        removeResultPanel()


        val panel =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL


                setPadding(
                    dp(20),
                    dp(20),
                    dp(20),
                    dp(20)
                )


                background =
                    createRoundedBackground(
                        Color.rgb(
                            18,
                            18,
                            18
                        ),
                        Color.rgb(
                            75,
                            155,
                            255
                        ),
                        24
                    )
            }


        val title =
            TextView(this).apply {

                text =
                    "HALO"

                textSize =
                    26f

                gravity =
                    Gravity.CENTER


                setTextColor(
                    Color.rgb(
                        100,
                        190,
                        255
                    )
                )


                setPadding(
                    0,
                    dp(4),
                    0,
                    dp(8)
                )
            }


        panel.addView(
            title
        )


        val description =
            TextView(this).apply {

                text =
                    "무엇을 도와드릴까요?"

                textSize =
                    19f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setPadding(
                    0,
                    0,
                    0,
                    dp(12)
                )
            }


        panel.addView(
            description
        )


        val contextStatus =
            TextView(this).apply {

                text =
                    if (
                        currentScreenLines.isEmpty()
                    ) {

                        "현재 화면에서 읽을 수 있는 정보를 찾지 못했습니다."

                    } else {

                        "현재 화면 정보를 불러왔습니다."
                    }

                textSize =
                    14f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.LTGRAY
                )

                setPadding(
                    0,
                    0,
                    0,
                    dp(16)
                )
            }


        panel.addView(
            contextStatus
        )


        // 보기 편하게

        val readableButton =
            createActionButton(
                "🔍  보기 편하게",
                "돋보기 또는 큰 글자로 확인"
            )


        readableButton.setOnClickListener {

            removeActionMenu()

            showReadableOptionsMenu()
        }


        panel.addView(
            readableButton
        )


        // 중요한 내용만

        val importantButton =
            createActionButton(
                "★  중요한 내용만",
                "AI가 필요한 핵심 정보만 정리"
            )


        importantButton.setOnClickListener {

            removeActionMenu()

            requestImportantScreen()
        }


        panel.addView(
            importantButton
        )


        // 읽어줘

        val readButton =
            createActionButton(
                "🔊  읽어줘",
                "현재 화면 내용을 음성으로 확인"
            )


        readButton.setOnClickListener {

            removeActionMenu()

            startReadingCurrentScreen()
        }


        panel.addView(
            readButton
        )


        // 질문하기

        val questionButton =
            createActionButton(
                "💬  질문하기",
                "현재 화면에 대해 음성으로 질문"
            )


        questionButton.setOnClickListener {

            removeActionMenu()

            startQuestionListening()
        }


        panel.addView(
            questionButton
        )


        // =====================================================
        // 내 시야 설정
        // =====================================================

        val visionProfileButton =
            createActionButton(
                "⚙  내 시야 설정",
                "글자 크기와 돋보기 기본 배율"
            )


        visionProfileButton.setOnClickListener {

            removeActionMenu()

            showVisionProfileTextSizeMenu()
        }


        panel.addView(
            visionProfileButton
        )


        // 닫기

        val closeButton =
            Button(this).apply {

                text =
                    "닫기"

                textSize =
                    17f

                setOnClickListener {

                    removeActionMenu()
                }
            }


        val closeParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            )


        closeParams.topMargin =
            dp(14)


        panel.addView(
            closeButton,
            closeParams
        )


        val params =
            WindowManager.LayoutParams(
                dp(340),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )


        params.gravity =
            Gravity.CENTER


        try {

            windowManager.addView(
                panel,
                params
            )


            actionMenu =
                panel


        } catch (e: Exception) {

            Log.e(
                TAG,
                "HALO 메뉴 표시 실패",
                e
            )
        }
    }


    // =========================================================
    // HALO 메뉴 버튼
    // =========================================================

    private fun createActionButton(
        buttonTitle: String,
        buttonSubtitle: String
    ): LinearLayout {

        val container =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL


                setPadding(
                    dp(16),
                    dp(12),
                    dp(16),
                    dp(12)
                )


                background =
                    createRoundedBackground(
                        Color.rgb(
                            38,
                            38,
                            38
                        ),
                        Color.rgb(
                            70,
                            70,
                            70
                        ),
                        16
                    )


                isClickable =
                    true

                isFocusable =
                    true
            }


        val title =
            TextView(this).apply {

                text =
                    buttonTitle

                textSize =
                    18f

                setTextColor(
                    Color.WHITE
                )
            }


        val subtitle =
            TextView(this).apply {

                text =
                    buttonSubtitle

                textSize =
                    13f

                setTextColor(
                    Color.LTGRAY
                )

                setPadding(
                    0,
                    dp(4),
                    0,
                    0
                )
            }


        container.addView(
            title
        )

        container.addView(
            subtitle
        )


        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )


        params.bottomMargin =
            dp(10)


        container.layoutParams =
            params


        return container
    }


    // =========================================================
    // Vision Profile - 내 시야 설정
    // =========================================================

    private fun showVisionProfileTextSizeMenu() {

        removeResultPanel()
        removeActionMenu()


        val panel =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(20),
                    dp(20),
                    dp(20),
                    dp(20)
                )

                background =
                    createRoundedBackground(
                        Color.rgb(
                            18,
                            18,
                            18
                        ),
                        Color.rgb(
                            75,
                            155,
                            255
                        ),
                        24
                    )
            }


        // =====================================================
        // 전체 스크롤
        // =====================================================

        val scrollView =
            ScrollView(this)


        val content =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL
            }


        scrollView.addView(
            content
        )


        // =====================================================
        // 제목
        // =====================================================

        val title =
            TextView(this).apply {

                text =
                    "내 시야 설정"

                textSize =
                    24f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.rgb(
                        100,
                        190,
                        255
                    )
                )

                setPadding(
                    0,
                    dp(4),
                    0,
                    dp(18)
                )
            }


        content.addView(
            title
        )


        // =====================================================
        // 1. 기본 글자 크기
        // =====================================================

        val textSizeTitle =
            TextView(this).apply {

                text =
                    "기본 글자 크기"

                textSize =
                    21f

                setTextColor(
                    Color.WHITE
                )

                setPadding(
                    0,
                    0,
                    0,
                    dp(6)
                )
            }


        content.addView(
            textSizeTitle
        )


        val textSizeDescription =
            TextView(this).apply {

                text =
                    "아래 글자를 직접 보고 편한 크기를 선택하세요."

                textSize =
                    15f

                setTextColor(
                    Color.LTGRAY
                )

                setPadding(
                    0,
                    0,
                    0,
                    dp(10)
                )
            }


        content.addView(
            textSizeDescription
        )


        val currentTextSetting =
            TextView(this).apply {

                text =
                    "현재 설정: ${profileTextSizeSp.toInt()}sp"

                textSize =
                    17f

                setTextColor(
                    Color.rgb(
                        100,
                        190,
                        255
                    )
                )

                setPadding(
                    0,
                    0,
                    0,
                    dp(12)
                )
            }


        content.addView(
            currentTextSetting
        )


        fun selectTextSize(
            size: Float
        ) {

            saveVisionProfile(
                textSizeSp =
                    size,

                magnifierScale =
                    profileMagnifierScale,

                readQuestionAutomatically =
                    autoReadQuestionAnswer,

                readImportantAutomatically =
                    autoReadImportantResult
            )


            currentTextSetting.text =
                "현재 설정: ${size.toInt()}sp"
        }


        fun createTextSizeButton(
            size: Float
        ): Button {

            return Button(this).apply {

                text =
                    "${size.toInt()}sp"

                // 실제 선택할 크기와 동일하게 표시
                textSize =
                    size

                isAllCaps =
                    false

                gravity =
                    Gravity.CENTER

                setPadding(
                    dp(8),
                    dp(10),
                    dp(8),
                    dp(10)
                )

                setOnClickListener {

                    selectTextSize(
                        size
                    )
                }
            }
        }


        val size32Button =
            createTextSizeButton(
                32f
            )


        val size32Params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )


        size32Params.bottomMargin =
            dp(8)


        content.addView(
            size32Button,
            size32Params
        )


        val size40Button =
            createTextSizeButton(
                40f
            )


        val size40Params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )


        size40Params.bottomMargin =
            dp(8)


        content.addView(
            size40Button,
            size40Params
        )


        val size48Button =
            createTextSizeButton(
                48f
            )


        val size48Params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )


        size48Params.bottomMargin =
            dp(8)


        content.addView(
            size48Button,
            size48Params
        )


        val size56Button =
            createTextSizeButton(
                56f
            )


        val size56Params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )


        size56Params.bottomMargin =
            dp(28)


        content.addView(
            size56Button,
            size56Params
        )


        // =====================================================
        // 구분선 1
        // =====================================================

        val divider1 =
            TextView(this).apply {

                setBackgroundColor(
                    Color.rgb(
                        70,
                        70,
                        70
                    )
                )
            }


        val divider1Params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            )


        divider1Params.bottomMargin =
            dp(22)


        content.addView(
            divider1,
            divider1Params
        )


        // =====================================================
        // 2. 돋보기 기본 배율
        // =====================================================

        val magnifierTitle =
            TextView(this).apply {

                text =
                    "돋보기 기본 배율"

                textSize =
                    21f

                setTextColor(
                    Color.WHITE
                )

                setPadding(
                    0,
                    0,
                    0,
                    dp(6)
                )
            }


        content.addView(
            magnifierTitle
        )


        val magnifierDescription =
            TextView(this).apply {

                text =
                    "돋보기를 시작했을 때 사용할 확대 배율입니다."

                textSize =
                    15f

                setTextColor(
                    Color.LTGRAY
                )

                setPadding(
                    0,
                    0,
                    0,
                    dp(10)
                )
            }


        content.addView(
            magnifierDescription
        )


        val currentMagnifierSetting =
            TextView(this).apply {

                text =
                    "현재 설정: ${formatMagnifierScale(profileMagnifierScale)}"

                textSize =
                    17f

                setTextColor(
                    Color.rgb(
                        100,
                        190,
                        255
                    )
                )

                setPadding(
                    0,
                    0,
                    0,
                    dp(12)
                )
            }


        content.addView(
            currentMagnifierSetting
        )


        fun selectMagnifierScale(
            scale: Float
        ) {

            saveVisionProfile(
                textSizeSp =
                    profileTextSizeSp,

                magnifierScale =
                    scale,

                readQuestionAutomatically =
                    autoReadQuestionAnswer,

                readImportantAutomatically =
                    autoReadImportantResult
            )


            currentMagnifierSetting.text =
                "현재 설정: ${formatMagnifierScale(scale)}"
        }


        // =====================================================
        // 1.5 / 2배
        // =====================================================

        val magnifierRow1 =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL
            }


        val scale15Button =
            Button(this).apply {

                text =
                    "1.5배"

                textSize =
                    18f

                setOnClickListener {

                    selectMagnifierScale(
                        1.5f
                    )
                }
            }


        val scale20Button =
            Button(this).apply {

                text =
                    "2배"

                textSize =
                    18f

                setOnClickListener {

                    selectMagnifierScale(
                        2.0f
                    )
                }
            }


        val scale15Params =
            LinearLayout.LayoutParams(
                0,
                dp(58),
                1f
            )


        scale15Params.marginEnd =
            dp(5)


        val scale20Params =
            LinearLayout.LayoutParams(
                0,
                dp(58),
                1f
            )


        scale20Params.marginStart =
            dp(5)


        magnifierRow1.addView(
            scale15Button,
            scale15Params
        )


        magnifierRow1.addView(
            scale20Button,
            scale20Params
        )


        content.addView(
            magnifierRow1
        )


        // =====================================================
        // 2.5 / 3배
        // =====================================================

        val magnifierRow2 =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL
            }


        val scale25Button =
            Button(this).apply {

                text =
                    "2.5배"

                textSize =
                    18f

                setOnClickListener {

                    selectMagnifierScale(
                        2.5f
                    )
                }
            }


        val scale30Button =
            Button(this).apply {

                text =
                    "3배"

                textSize =
                    18f

                setOnClickListener {

                    selectMagnifierScale(
                        3.0f
                    )
                }
            }


        val scale25Params =
            LinearLayout.LayoutParams(
                0,
                dp(58),
                1f
            )


        scale25Params.marginEnd =
            dp(5)

        scale25Params.topMargin =
            dp(8)


        val scale30Params =
            LinearLayout.LayoutParams(
                0,
                dp(58),
                1f
            )


        scale30Params.marginStart =
            dp(5)

        scale30Params.topMargin =
            dp(8)


        magnifierRow2.addView(
            scale25Button,
            scale25Params
        )


        magnifierRow2.addView(
            scale30Button,
            scale30Params
        )


        content.addView(
            magnifierRow2
        )


        val magnifierGuide =
            TextView(this).apply {

                text =
                    "설정한 배율은 다음에 돋보기를 시작할 때 적용됩니다."

                textSize =
                    14f

                setTextColor(
                    Color.LTGRAY
                )

                setPadding(
                    0,
                    dp(10),
                    0,
                    dp(24)
                )
            }


        content.addView(
            magnifierGuide
        )


        // =====================================================
        // 구분선 2
        // =====================================================

        val divider2 =
            TextView(this).apply {

                setBackgroundColor(
                    Color.rgb(
                        70,
                        70,
                        70
                    )
                )
            }


        val divider2Params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            )


        divider2Params.bottomMargin =
            dp(22)


        content.addView(
            divider2,
            divider2Params
        )


        // =====================================================
        // 3. 음성 설정
        // =====================================================

        val voiceTitle =
            TextView(this).apply {

                text =
                    "음성 설정"

                textSize =
                    21f

                setTextColor(
                    Color.WHITE
                )

                setPadding(
                    0,
                    0,
                    0,
                    dp(6)
                )
            }


        content.addView(
            voiceTitle
        )


        val voiceDescription =
            TextView(this).apply {

                text =
                    "AI 결과가 나오면 자동으로 읽을지 선택합니다."

                textSize =
                    15f

                setTextColor(
                    Color.LTGRAY
                )

                setPadding(
                    0,
                    0,
                    0,
                    dp(14)
                )
            }


        content.addView(
            voiceDescription
        )


        // =====================================================
        // 질문 답변 자동 읽기
        // =====================================================

        val questionAutoReadButton =
            Button(this)


        fun updateQuestionAutoReadButton() {

            questionAutoReadButton.text =
                if (
                    autoReadQuestionAnswer
                ) {

                    "🔊 질문 답변 자동 읽기 : ON"

                } else {

                    "🔇 질문 답변 자동 읽기 : OFF"
                }
        }


        questionAutoReadButton.apply {

            textSize =
                18f

            isAllCaps =
                false


            setOnClickListener {

                autoReadQuestionAnswer =
                    !autoReadQuestionAnswer


                saveVisionProfile(
                    textSizeSp =
                        profileTextSizeSp,

                    magnifierScale =
                        profileMagnifierScale,

                    readQuestionAutomatically =
                        autoReadQuestionAnswer,

                    readImportantAutomatically =
                        autoReadImportantResult
                )


                updateQuestionAutoReadButton()
            }
        }


        updateQuestionAutoReadButton()


        val questionAutoParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(62)
            )


        questionAutoParams.bottomMargin =
            dp(10)


        content.addView(
            questionAutoReadButton,
            questionAutoParams
        )


        // =====================================================
        // 중요 정보 자동 읽기
        // =====================================================

        val importantAutoReadButton =
            Button(this)


        fun updateImportantAutoReadButton() {

            importantAutoReadButton.text =
                if (
                    autoReadImportantResult
                ) {

                    "🔊 중요 정보 자동 읽기 : ON"

                } else {

                    "🔇 중요 정보 자동 읽기 : OFF"
                }
        }


        importantAutoReadButton.apply {

            textSize =
                18f

            isAllCaps =
                false


            setOnClickListener {

                autoReadImportantResult =
                    !autoReadImportantResult


                saveVisionProfile(
                    textSizeSp =
                        profileTextSizeSp,

                    magnifierScale =
                        profileMagnifierScale,

                    readQuestionAutomatically =
                        autoReadQuestionAnswer,

                    readImportantAutomatically =
                        autoReadImportantResult
                )


                updateImportantAutoReadButton()
            }
        }


        updateImportantAutoReadButton()


        val importantAutoParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(62)
            )


        importantAutoParams.bottomMargin =
            dp(12)


        content.addView(
            importantAutoReadButton,
            importantAutoParams
        )


        // =====================================================
        // 음성 설명
        // =====================================================

        val voiceGuide =
            TextView(this).apply {

                text =
                    "자동 읽기를 꺼도 결과 화면의 읽기 버튼으로 직접 들을 수 있습니다."

                textSize =
                    14f

                setTextColor(
                    Color.LTGRAY
                )

                setPadding(
                    0,
                    0,
                    0,
                    dp(20)
                )
            }


        content.addView(
            voiceGuide
        )


        // =====================================================
        // 스크롤 영역
        // =====================================================

        panel.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )


        // =====================================================
        // HALO 메뉴로 돌아가기
        // =====================================================

        val backButton =
            Button(this).apply {

                text =
                    "HALO 메뉴로 돌아가기"

                textSize =
                    17f

                setOnClickListener {

                    removeResultPanel()

                    showActionMenu()
                }
            }


        panel.addView(
            backButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            )
        )


        // =====================================================
        // Overlay
        // =====================================================

        val params =
            WindowManager.LayoutParams(
                dp(370),
                dp(680),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )


        params.gravity =
            Gravity.CENTER


        try {

            windowManager.addView(
                panel,
                params
            )


            resultPanel =
                panel


        } catch (e: Exception) {

            Log.e(
                TAG,
                "Vision Profile 화면 표시 실패",
                e
            )
        }
    }


    // =========================================================
    // 배율 표시 문자열
    // =========================================================

    private fun formatMagnifierScale(
        scale: Float
    ): String {

        return if (
            scale % 1f == 0f
        ) {

            "${scale.toInt()}배"

        } else {

            "${scale}배"
        }
    }


    // =========================================================
    // 보기 편하게 - 선택
    // =========================================================

    private fun showReadableOptionsMenu() {

        removeResultPanel()

        removeActionMenu()


        val panel =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(20),
                    dp(20),
                    dp(20),
                    dp(20)
                )

                background =
                    createRoundedBackground(
                        Color.rgb(
                            18,
                            18,
                            18
                        ),
                        Color.rgb(
                            75,
                            155,
                            255
                        ),
                        24
                    )
            }


        val title =
            TextView(this).apply {

                text =
                    "보기 편하게"

                textSize =
                    24f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.rgb(
                        100,
                        190,
                        255
                    )
                )

                setPadding(
                    0,
                    dp(4),
                    0,
                    dp(8)
                )
            }


        panel.addView(
            title
        )


        val description =
            TextView(this).apply {

                text =
                    "원래 화면을 돋보기로 확대하거나\n글자만 크게 볼 수 있습니다."

                textSize =
                    17f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setPadding(
                    0,
                    0,
                    0,
                    dp(18)
                )
            }


        panel.addView(
            description
        )


        val magnifierButton =
            createActionButton(
                "🔎  돋보기로 보기",
                "원래 화면을 이동 가능한 돋보기로 확대"
            )


        magnifierButton.setOnClickListener {

            removeResultPanel()

            startWindowMagnifier()
        }


        panel.addView(
            magnifierButton
        )


        val largeTextButton =
            createActionButton(
                "📝  큰 글자로 보기",
                "화면의 글자를 추출하여 크게 표시"
            )


        largeTextButton.setOnClickListener {

            removeResultPanel()


            if (
                currentScreenLines.isEmpty()
            ) {

                showMessagePanel(
                    "보기 편하게",
                    "현재 화면에서 읽을 수 있는 텍스트가 없습니다."
                )

                return@setOnClickListener
            }


            showReadablePanel(
                currentScreenLines.joinToString(
                    "\n\n"
                )
            )
        }


        panel.addView(
            largeTextButton
        )


        val backButton =
            Button(this).apply {

                text =
                    "HALO 메뉴로 돌아가기"

                textSize =
                    17f

                setOnClickListener {

                    removeResultPanel()

                    showActionMenu()
                }
            }


        val backParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            )


        backParams.topMargin =
            dp(8)


        panel.addView(
            backButton,
            backParams
        )


        val params =
            WindowManager.LayoutParams(
                dp(350),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )


        params.gravity =
            Gravity.CENTER


        try {

            windowManager.addView(
                panel,
                params
            )

            resultPanel =
                panel

        } catch (e: Exception) {

            Log.e(
                TAG,
                "보기 편하게 선택 화면 표시 실패",
                e
            )
        }
    }


    // =========================================================
    // Magnification Listener
    // =========================================================

    private fun initializeMagnificationListener() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }


        if (
            magnificationChangedListener != null
        ) {
            return
        }


        val listener =
            object :
                AccessibilityService
                    .MagnificationController
                    .OnMagnificationChangedListener {


                override fun onMagnificationChanged(
                    controller:
                        AccessibilityService
                            .MagnificationController,
                    region: Region,
                    config: MagnificationConfig
                ) {

                    if (
                        !isHaloWindowMagnifierActive
                    ) {
                        return
                    }


                    if (
                        config.mode !=
                        MagnificationConfig
                            .MAGNIFICATION_MODE_WINDOW ||
                        config.scale <= 1.0f
                    ) {

                        isHaloWindowMagnifierActive =
                            false

                        isMagnifierOverTrash =
                            false


                        mainHandler.removeCallbacks(
                            magnifierDismissRunnable
                        )


                        hideMagnifierTrashZone()

                        return
                    }


                    checkMagnifierTrashZone(
                        region
                    )
                }


                @Suppress("DEPRECATION")
                override fun onMagnificationChanged(
                    controller:
                        AccessibilityService
                            .MagnificationController,
                    region: Region,
                    scale: Float,
                    centerX: Float,
                    centerY: Float
                ) {

                    // Window Magnification에서는 사용 안 함
                }
            }


        try {

            magnificationController.addListener(
                listener,
                mainHandler
            )


            magnificationChangedListener =
                listener


        } catch (e: Exception) {

            Log.e(
                TAG,
                "돋보기 리스너 등록 실패",
                e
            )
        }
    }


    // =========================================================
    // 창 돋보기 시작
    // =========================================================

    private fun startWindowMagnifier() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.TIRAMISU
        ) {

            showMessagePanel(
                "돋보기",
                "이 기능은 Android 13 이상에서 사용할 수 있습니다."
            )

            return
        }


        try {

            val bounds =
                windowManager
                    .currentWindowMetrics
                    .bounds


            val config =
                MagnificationConfig
                    .Builder()
                    .setMode(
                        MagnificationConfig
                            .MAGNIFICATION_MODE_WINDOW
                    )
                    // Vision Profile 적용
                    .setScale(
                        profileMagnifierScale
                    )
                    .setCenterX(
                        bounds.centerX()
                            .toFloat()
                    )
                    .setCenterY(
                        bounds.centerY()
                            .toFloat()
                    )
                    .build()


            val success =
                magnificationController
                    .setMagnificationConfig(
                        config,
                        true
                    )


            if (success) {

                isHaloWindowMagnifierActive =
                    true

                isMagnifierOverTrash =
                    false


                showMagnifierTrashZone()


            } else {

                showMessagePanel(
                    "돋보기",
                    "창 돋보기를 시작하지 못했습니다."
                )
            }


        } catch (e: Exception) {

            Log.e(
                TAG,
                "창 돋보기 시작 실패",
                e
            )


            showMessagePanel(
                "돋보기",
                "창 돋보기를 시작하지 못했습니다."
            )
        }
    }


    // =========================================================
    // 창 돋보기 종료
    // =========================================================

    private fun stopWindowMagnifier() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }


        mainHandler.removeCallbacks(
            magnifierDismissRunnable
        )


        isHaloWindowMagnifierActive =
            false

        isMagnifierOverTrash =
            false


        try {

            val success =
                magnificationController
                    .resetCurrentMagnification(
                        true
                    )


            if (success) {

                hideMagnifierTrashZone()

            } else {

                isHaloWindowMagnifierActive =
                    true
            }


        } catch (e: Exception) {

            isHaloWindowMagnifierActive =
                true


            Log.e(
                TAG,
                "창 돋보기 종료 실패",
                e
            )
        }
    }


    // =========================================================
    // 돋보기 쓰레기통
    // =========================================================

    private fun showMagnifierTrashZone() {

        hideMagnifierTrashZone()


        val trash =
            TextView(this).apply {

                text =
                    "🗑\n돋보기를 여기로 이동"

                textSize =
                    16f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setPadding(
                    dp(12),
                    dp(8),
                    dp(12),
                    dp(8)
                )

                background =
                    createRoundedBackground(
                        Color.rgb(
                            85,
                            35,
                            35
                        ),
                        Color.rgb(
                            210,
                            90,
                            90
                        ),
                        22
                    )
            }


        val params =
            WindowManager.LayoutParams(
                dp(210),
                dp(88),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )


        params.gravity =
            Gravity.BOTTOM or
                Gravity.CENTER_HORIZONTAL


        params.y =
            dp(20)


        try {

            windowManager.addView(
                trash,
                params
            )


            magnifierTrashView =
                trash


        } catch (e: Exception) {

            Log.e(
                TAG,
                "돋보기 종료 영역 표시 실패",
                e
            )
        }
    }


    // =========================================================
    // 쓰레기통 충돌 확인
    //
    // 움직이는 동안 계속 타이머를 초기화.
    // 사용자가 손을 놓아 위치 변경이 멈추면
    // 300ms 뒤 종료 → 드롭과 비슷한 UX.
    // =========================================================

    private fun checkMagnifierTrashZone(
        region: Region
    ) {

        val magnifierBounds =
            region.bounds


        if (
            magnifierBounds.isEmpty
        ) {
            return
        }


        val screenBounds =
            windowManager
                .currentWindowMetrics
                .bounds


        val detectionWidth =
            dp(300)


        val detectionHeight =
            dp(180)


        val detectionLeft =
            screenBounds.centerX() -
                detectionWidth / 2


        val detectionRight =
            screenBounds.centerX() +
                detectionWidth / 2


        val detectionTop =
            screenBounds.bottom -
                detectionHeight


        val horizontalOverlap =
            magnifierBounds.right >=
                detectionLeft &&
                magnifierBounds.left <=
                detectionRight


        val verticalOverlap =
            magnifierBounds.bottom >=
                detectionTop


        val overTrash =
            horizontalOverlap &&
                verticalOverlap


        if (overTrash) {

            if (
                !isMagnifierOverTrash
            ) {

                isMagnifierOverTrash =
                    true


                magnifierTrashView?.apply {

                    text =
                        "🗑\n여기에서 놓으면 종료"


                    background =
                        createRoundedBackground(
                            Color.rgb(
                                185,
                                35,
                                35
                            ),
                            Color.rgb(
                                255,
                                150,
                                150
                            ),
                            22
                        )
                }
            }


            // 이동할 때마다 타이머 초기화
            mainHandler.removeCallbacks(
                magnifierDismissRunnable
            )


            mainHandler.postDelayed(
                magnifierDismissRunnable,
                MAGNIFIER_DISMISS_DELAY_MS
            )


        } else {

            mainHandler.removeCallbacks(
                magnifierDismissRunnable
            )


            if (
                isMagnifierOverTrash
            ) {

                isMagnifierOverTrash =
                    false


                magnifierTrashView?.apply {

                    text =
                        "🗑\n돋보기를 여기로 이동"


                    background =
                        createRoundedBackground(
                            Color.rgb(
                                85,
                                35,
                                35
                            ),
                            Color.rgb(
                                210,
                                90,
                                90
                            ),
                            22
                        )
                }
            }
        }
    }


    // =========================================================
    // 쓰레기통 숨기기
    // =========================================================

    private fun hideMagnifierTrashZone() {

        mainHandler.removeCallbacks(
            magnifierDismissRunnable
        )


        magnifierTrashView?.let { view ->

            try {

                if (
                    ::windowManager.isInitialized
                ) {

                    windowManager.removeView(
                        view
                    )
                }

            } catch (_: Exception) {
            }
        }


        magnifierTrashView =
            null
    }


    // =========================================================
    // 큰 글자로 보기
    // =========================================================

    private fun showReadablePanel(
        screenText: String
    ) {

        // 새 화면을 열 때 Vision Profile의 기본 크기 적용
        readableTextSizeSp =
            profileTextSizeSp


        showZoomTextPanel(
            titleText =
                "보기 편하게",

            guideText =
                "두 손가락을 벌리거나 모아서 글자 크기를 조절하세요.",

            contentText =
                screenText,

            readButtonText =
                null,

            onRead =
                null
        )
    }


    // =========================================================
    // 중요한 내용만
    // =========================================================

    private fun requestImportantScreen() {

        if (
            currentScreenLines.isEmpty()
        ) {

            showMessagePanel(
                "중요한 내용만",
                "현재 화면에서 읽을 수 있는 정보가 없습니다."
            )

            return
        }


        val screenText =
            currentScreenLines.joinToString(
                "\n"
            )


        showMessagePanel(
            "중요한 내용만",
            "현재 화면의 중요한 정보를 분석하고 있습니다..."
        )


        Thread {

            var connection:
                HttpURLConnection? = null


            try {

                val url =
                    URL(
                        "$SERVER_BASE_URL/screen/important"
                    )


                connection =
                    url.openConnection()
                        as HttpURLConnection


                connection.requestMethod =
                    "POST"

                connection.connectTimeout =
                    10000

                connection.readTimeout =
                    30000

                connection.doOutput =
                    true


                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
                )


                val requestJson =
                    JSONObject().apply {

                        put(
                            "screen_text",
                            screenText
                        )
                    }


                connection.outputStream.use { output ->

                    output.write(
                        requestJson
                            .toString()
                            .toByteArray(
                                Charsets.UTF_8
                            )
                    )

                    output.flush()
                }


                val responseCode =
                    connection.responseCode


                val stream =
                    if (
                        responseCode in
                        200..299
                    ) {

                        connection.inputStream

                    } else {

                        connection.errorStream
                    }


                val responseText =
                    stream
                        ?.bufferedReader(
                            Charsets.UTF_8
                        )
                        ?.use {
                            it.readText()
                        }
                        ?: ""


                if (
                    responseCode in
                    200..299
                ) {

                    val json =
                        JSONObject(
                            responseText
                        )


                    val answer =
                        json.optString(
                            "answer",
                            "서버 응답을 읽을 수 없습니다."
                        )


                    mainHandler.post {

                        showImportantResultPanel(
                            answer
                        )


                        if (
                            isTtsReady &&
                            autoReadImportantResult
                        ) {

                            speakText(
                                answer
                            )
                        }
                    }


                } else {

                    mainHandler.post {

                        showMessagePanel(
                            "중요한 내용만",
                            "서버 오류가 발생했습니다.\n\n응답 코드: $responseCode"
                        )
                    }
                }


            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "중요 정보 API 연결 실패",
                    e
                )


                mainHandler.post {

                    showMessagePanel(
                        "중요한 내용만",
                        "서버에 연결할 수 없습니다.\n\nFastAPI 서버와 adb reverse 상태를 확인해주세요."
                    )
                }


            } finally {

                connection?.disconnect()
            }

        }.start()
    }


    // =========================================================
    // 중요한 정보 결과
    // =========================================================

    private fun showImportantResultPanel(
        answer: String
    ) {

        readableTextSizeSp =
            profileTextSizeSp


        showZoomTextPanel(
            titleText =
                "중요한 내용만",

            guideText =
                "AI가 현재 화면에서 핵심 정보만 정리했습니다.",

            contentText =
                answer,

            readButtonText =
                "🔊 핵심 내용 읽어주기",

            onRead = {

                if (
                    !isTtsReady
                ) {

                    showMessagePanel(
                        "음성 읽기",
                        "음성 읽기 기능을 준비하지 못했습니다."
                    )


                } else {

                    removeResultPanel()


                    showTtsControlPanel(
                        textToRead =
                            answer,

                        statusMessage =
                            "AI가 정리한 핵심 내용을 읽고 있습니다."
                    )


                    speakText(
                        answer
                    )
                }
            }
        )
    }


    // =========================================================
    // 공통 큰 글자 패널
    // =========================================================

    private fun showZoomTextPanel(
        titleText: String,
        guideText: String,
        contentText: String,
        readButtonText: String?,
        onRead: (() -> Unit)?
    ) {

        removeResultPanel()


        val panel =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(18),
                    dp(18),
                    dp(18),
                    dp(18)
                )

                background =
                    createRoundedBackground(
                        Color.rgb(
                            12,
                            12,
                            12
                        ),
                        Color.rgb(
                            75,
                            155,
                            255
                        ),
                        24
                    )
            }


        val header =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }


        val title =
            TextView(this).apply {

                text =
                    titleText

                textSize =
                    23f

                setTextColor(
                    Color.rgb(
                        100,
                        190,
                        255
                    )
                )
            }


        header.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )


        val sizeLabel =
            TextView(this).apply {

                text =
                    "${readableTextSizeSp.toInt()}sp"

                textSize =
                    16f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setPadding(
                    dp(10),
                    dp(6),
                    dp(10),
                    dp(6)
                )

                background =
                    createRoundedBackground(
                        Color.rgb(
                            40,
                            40,
                            40
                        ),
                        Color.rgb(
                            80,
                            80,
                            80
                        ),
                        12
                    )
            }


        header.addView(
            sizeLabel
        )


        panel.addView(
            header
        )


        val guide =
            TextView(this).apply {

                text =
                    guideText

                textSize =
                    14f

                setTextColor(
                    Color.LTGRAY
                )

                setPadding(
                    0,
                    dp(10),
                    0,
                    dp(10)
                )
            }


        panel.addView(
            guide
        )


        val zoomRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL
            }


        val decreaseButton =
            Button(this).apply {

                text =
                    "A−"

                textSize =
                    20f
            }


        val increaseButton =
            Button(this).apply {

                text =
                    "A+"

                textSize =
                    20f
            }


        val decreaseParams =
            LinearLayout.LayoutParams(
                0,
                dp(58),
                1f
            )


        decreaseParams.marginEnd =
            dp(6)


        val increaseParams =
            LinearLayout.LayoutParams(
                0,
                dp(58),
                1f
            )


        increaseParams.marginStart =
            dp(6)


        zoomRow.addView(
            decreaseButton,
            decreaseParams
        )


        zoomRow.addView(
            increaseButton,
            increaseParams
        )


        panel.addView(
            zoomRow
        )


        val scrollView =
            ScrollView(this)


        val content =
            TextView(this).apply {

                text =
                    contentText


                setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    readableTextSizeSp
                )


                setTextColor(
                    Color.WHITE
                )


                setLineSpacing(
                    dp(10).toFloat(),
                    1.3f
                )


                setPadding(
                    dp(2),
                    dp(18),
                    dp(2),
                    dp(20)
                )
            }


        scrollView.addView(
            content
        )


        panel.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )


        fun updateTextSize(
            newSize: Float
        ) {

            readableTextSizeSp =
                newSize.coerceIn(
                    MIN_TEXT_SIZE_SP,
                    MAX_TEXT_SIZE_SP
                )


            content.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                readableTextSizeSp
            )


            sizeLabel.text =
                "${readableTextSizeSp.toInt()}sp"
        }


        decreaseButton.setOnClickListener {

            updateTextSize(
                readableTextSizeSp -
                    TEXT_SIZE_STEP_SP
            )
        }


        increaseButton.setOnClickListener {

            updateTextSize(
                readableTextSizeSp +
                    TEXT_SIZE_STEP_SP
            )
        }


        val scaleDetector =
            ScaleGestureDetector(
                this,
                object :
                    ScaleGestureDetector
                        .SimpleOnScaleGestureListener() {


                    override fun onScale(
                        detector: ScaleGestureDetector
                    ): Boolean {

                        updateTextSize(
                            readableTextSizeSp *
                                detector.scaleFactor
                        )

                        return true
                    }
                }
            )


        scrollView.setOnTouchListener { _, event ->

            scaleDetector.onTouchEvent(
                event
            )


            event.pointerCount >= 2 ||
                scaleDetector.isInProgress
        }


        if (
            readButtonText != null &&
            onRead != null
        ) {

            val readButton =
                Button(this).apply {

                    text =
                        readButtonText

                    textSize =
                        18f

                    setOnClickListener {

                        onRead()
                    }
                }


            val readParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(58)
                )


            readParams.topMargin =
                dp(8)

            readParams.bottomMargin =
                dp(8)


            panel.addView(
                readButton,
                readParams
            )
        }


        val backButton =
            Button(this).apply {

                text =
                    "HALO 메뉴로 돌아가기"

                textSize =
                    17f

                setOnClickListener {

                    textToSpeech?.stop()

                    removeResultPanel()

                    showActionMenu()
                }
            }


        panel.addView(
            backButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            )
        )


        val params =
            WindowManager.LayoutParams(
                dp(370),
                dp(650),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )


        params.gravity =
            Gravity.CENTER


        try {

            windowManager.addView(
                panel,
                params
            )


            resultPanel =
                panel


        } catch (e: Exception) {

            Log.e(
                TAG,
                "확대 패널 표시 실패",
                e
            )
        }
    }


    // =========================================================
    // TTS 초기화
    // =========================================================

    private fun initializeTextToSpeech() {

        if (
            textToSpeech != null
        ) {
            return
        }


        textToSpeech =
            TextToSpeech(this) { status ->

                if (
                    status ==
                    TextToSpeech.SUCCESS
                ) {

                    val result =
                        textToSpeech?.setLanguage(
                            Locale.KOREAN
                        )


                    isTtsReady =
                        result !=
                            TextToSpeech.LANG_MISSING_DATA &&
                            result !=
                            TextToSpeech.LANG_NOT_SUPPORTED


                    if (
                        isTtsReady
                    ) {

                        textToSpeech?.setSpeechRate(
                            0.9f
                        )

                        textToSpeech?.setPitch(
                            1.0f
                        )
                    }


                } else {

                    isTtsReady =
                        false
                }
            }
    }


    // =========================================================
    // 현재 화면 TTS
    // =========================================================

    private fun startReadingCurrentScreen() {

        if (
            currentScreenLines.isEmpty()
        ) {

            showMessagePanel(
                "읽어줘",
                "현재 화면에서 읽을 수 있는 정보가 없습니다."
            )

            return
        }


        if (
            !isTtsReady
        ) {

            showMessagePanel(
                "읽어줘",
                "음성 읽기 기능을 준비하지 못했습니다.\n\n휴대폰의 TTS 설정을 확인해주세요."
            )

            return
        }


        val text =
            currentScreenLines.joinToString(
                ".\n"
            )


        showTtsControlPanel(
            textToRead =
                text,

            statusMessage =
                "현재 화면의 내용을 읽고 있습니다."
        )


        speakText(
            text
        )
    }


    // =========================================================
    // TTS 실행
    // =========================================================

    private fun speakText(
        text: String
    ) {

        if (
            !isTtsReady
        ) {
            return
        }


        textToSpeech?.stop()


        val chunks =
            splitTextForTts(
                text
            )


        chunks.forEachIndexed { index, chunk ->

            val mode =
                if (
                    index == 0
                ) {

                    TextToSpeech.QUEUE_FLUSH

                } else {

                    TextToSpeech.QUEUE_ADD
                }


            textToSpeech?.speak(
                chunk,
                mode,
                null,
                "halo_tts_$index"
            )
        }
    }


    // =========================================================
    // TTS 긴 문자열 나누기
    // =========================================================

    private fun splitTextForTts(
        text: String
    ): List<String> {

        val maxLength =
            3000


        var remaining =
            text.trim()


        if (
            remaining.isEmpty()
        ) {

            return emptyList()
        }


        if (
            remaining.length <=
            maxLength
        ) {

            return listOf(
                remaining
            )
        }


        val chunks =
            mutableListOf<String>()


        while (
            remaining.length >
            maxLength
        ) {

            val candidate =
                remaining.substring(
                    0,
                    maxLength
                )


            var cutIndex =
                maxOf(
                    candidate.lastIndexOf('\n'),
                    candidate.lastIndexOf(". "),
                    candidate.lastIndexOf(' ')
                )


            if (
                cutIndex <
                maxLength / 2
            ) {

                cutIndex =
                    maxLength
            }


            val chunk =
                remaining.substring(
                    0,
                    cutIndex
                ).trim()


            if (
                chunk.isNotEmpty()
            ) {

                chunks.add(
                    chunk
                )
            }


            remaining =
                remaining.substring(
                    cutIndex
                ).trim()
        }


        if (
            remaining.isNotEmpty()
        ) {

            chunks.add(
                remaining
            )
        }


        return chunks
    }


    // =========================================================
    // TTS 제어 패널
    // =========================================================

    private fun showTtsControlPanel(
        textToRead: String,
        statusMessage: String
    ) {

        removeResultPanel()


        val panel =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(20),
                    dp(20),
                    dp(20),
                    dp(20)
                )

                background =
                    createRoundedBackground(
                        Color.rgb(
                            12,
                            12,
                            12
                        ),
                        Color.rgb(
                            75,
                            155,
                            255
                        ),
                        24
                    )
            }


        val title =
            TextView(this).apply {

                text =
                    "읽어줘"

                textSize =
                    24f

                setTextColor(
                    Color.rgb(
                        100,
                        190,
                        255
                    )
                )

                setPadding(
                    0,
                    0,
                    0,
                    dp(18)
                )
            }


        panel.addView(
            title
        )


        val status =
            TextView(this).apply {

                text =
                    statusMessage

                textSize =
                    30f

                setTextColor(
                    Color.WHITE
                )

                setLineSpacing(
                    dp(8).toFloat(),
                    1.25f
                )
            }


        panel.addView(
            status,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )


        val replayButton =
            Button(this).apply {

                text =
                    "처음부터 다시 읽기"

                textSize =
                    18f

                setOnClickListener {

                    status.text =
                        "처음부터 다시 읽고 있습니다."

                    speakText(
                        textToRead
                    )
                }
            }


        val replayParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            )


        replayParams.bottomMargin =
            dp(10)


        panel.addView(
            replayButton,
            replayParams
        )


        val stopButton =
            Button(this).apply {

                text =
                    "읽기 중지"

                textSize =
                    18f

                setOnClickListener {

                    textToSpeech?.stop()

                    status.text =
                        "읽기를 중지했습니다."
                }
            }


        val stopParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            )


        stopParams.bottomMargin =
            dp(10)


        panel.addView(
            stopButton,
            stopParams
        )


        val backButton =
            Button(this).apply {

                text =
                    "HALO 메뉴로 돌아가기"

                textSize =
                    17f

                setOnClickListener {

                    textToSpeech?.stop()

                    removeResultPanel()

                    showActionMenu()
                }
            }


        panel.addView(
            backButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            )
        )


        val params =
            WindowManager.LayoutParams(
                dp(360),
                dp(430),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )


        params.gravity =
            Gravity.CENTER


        try {

            windowManager.addView(
                panel,
                params
            )


            resultPanel =
                panel


        } catch (e: Exception) {

            Log.e(
                TAG,
                "TTS 패널 표시 실패",
                e
            )
        }
    }


    // =========================================================
    // SpeechRecognizer 초기화
    // =========================================================

    private fun initializeSpeechRecognizer() {

        if (
            speechRecognizer != null
        ) {
            return
        }


        if (
            !SpeechRecognizer.isRecognitionAvailable(
                this
            )
        ) {

            Log.e(
                TAG,
                "SpeechRecognizer 사용 불가"
            )

            return
        }


        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(
                this
            )


        speechRecognizer?.setRecognitionListener(

            object :
                RecognitionListener {


                override fun onReadyForSpeech(
                    params: Bundle?
                ) {
                }


                override fun onBeginningOfSpeech() {
                }


                override fun onRmsChanged(
                    rmsdB: Float
                ) {
                }


                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
                }


                override fun onEndOfSpeech() {
                }


                override fun onError(
                    error: Int
                ) {

                    isListeningForQuestion =
                        false


                    mainHandler.post {

                        showMessagePanel(
                            "질문하기",
                            "질문을 인식하지 못했습니다.\n\n다시 질문하기를 눌러주세요."
                        )
                    }
                }


                override fun onResults(
                    results: Bundle?
                ) {

                    isListeningForQuestion =
                        false


                    val matches =
                        results?.getStringArrayList(
                            SpeechRecognizer
                                .RESULTS_RECOGNITION
                        )


                    val question =
                        matches
                            ?.firstOrNull()
                            ?.trim()


                    if (
                        question.isNullOrEmpty()
                    ) {

                        mainHandler.post {

                            showMessagePanel(
                                "질문하기",
                                "질문을 인식하지 못했습니다."
                            )
                        }

                        return
                    }


                    mainHandler.post {

                        showMessagePanel(
                            "질문하기",
                            "질문\n\n$question\n\n답변을 분석하고 있습니다..."
                        )
                    }


                    requestScreenQuestion(
                        question
                    )
                }


                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                }


                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {
                }
            }
        )
    }


    // =========================================================
    // 질문 듣기 시작
    // =========================================================

    private fun startQuestionListening() {

        textToSpeech?.stop()


        if (
            currentScreenLines.isEmpty()
        ) {

            showMessagePanel(
                "질문하기",
                "현재 화면에서 읽을 수 있는 정보가 없습니다."
            )

            return
        }


        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {

            showMessagePanel(
                "질문하기",
                "마이크 권한이 필요합니다.\n\nHaloVision 앱 설정에서 마이크 권한을 허용해주세요."
            )

            return
        }


        if (
            speechRecognizer == null
        ) {

            initializeSpeechRecognizer()
        }


        if (
            speechRecognizer == null
        ) {

            showMessagePanel(
                "질문하기",
                "이 기기에서 음성 인식을 사용할 수 없습니다."
            )

            return
        }


        if (
            isListeningForQuestion
        ) {

            speechRecognizer?.cancel()

            isListeningForQuestion =
                false
        }


        removeResultPanel()


        showMessagePanel(
            "질문하기",
            "질문을 말씀해주세요.\n\n예: \"발표가 몇 시야?\""
        )


        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "ko-KR"
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "ko-KR"
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    1
                )
            }


        isListeningForQuestion =
            true


        speechRecognizer?.startListening(
            intent
        )
    }


    // =========================================================
    // 질문 API
    // =========================================================

    private fun requestScreenQuestion(
        question: String
    ) {

        val screenText =
            currentScreenLines.joinToString(
                "\n"
            )


        Thread {

            var connection:
                HttpURLConnection? = null


            try {

                val url =
                    URL(
                        "$SERVER_BASE_URL/screen/question"
                    )


                connection =
                    url.openConnection()
                        as HttpURLConnection


                connection.requestMethod =
                    "POST"

                connection.connectTimeout =
                    10000

                connection.readTimeout =
                    30000

                connection.doOutput =
                    true


                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
                )


                val requestJson =
                    JSONObject().apply {

                        put(
                            "screen_text",
                            screenText
                        )

                        put(
                            "question",
                            question
                        )
                    }


                connection.outputStream.use { output ->

                    output.write(
                        requestJson
                            .toString()
                            .toByteArray(
                                Charsets.UTF_8
                            )
                    )

                    output.flush()
                }


                val responseCode =
                    connection.responseCode


                val stream =
                    if (
                        responseCode in
                        200..299
                    ) {

                        connection.inputStream

                    } else {

                        connection.errorStream
                    }


                val responseText =
                    stream
                        ?.bufferedReader(
                            Charsets.UTF_8
                        )
                        ?.use {
                            it.readText()
                        }
                        ?: ""


                if (
                    responseCode in
                    200..299
                ) {

                    val json =
                        JSONObject(
                            responseText
                        )


                    val answer =
                        json.optString(
                            "answer",
                            "답변을 가져오지 못했습니다."
                        )


                    mainHandler.post {

                        showQuestionAnswerPanel(
                            question =
                                question,

                            answer =
                                answer
                        )


                        if (
                            isTtsReady &&
                            autoReadQuestionAnswer
                        ) {

                            speakText(
                                answer
                            )
                        }
                    }


                } else {

                    mainHandler.post {

                        showMessagePanel(
                            "질문하기",
                            "서버 오류가 발생했습니다.\n\n응답 코드: $responseCode"
                        )
                    }
                }


            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "질문 API 연결 실패",
                    e
                )


                mainHandler.post {

                    showMessagePanel(
                        "질문하기",
                        "서버에 연결할 수 없습니다.\n\nFastAPI 서버와 adb reverse 상태를 확인해주세요."
                    )
                }


            } finally {

                connection?.disconnect()
            }

        }.start()
    }


    // =========================================================
    // 질문 답변 화면
    // =========================================================

    private fun showQuestionAnswerPanel(
        question: String,
        answer: String
    ) {

        removeResultPanel()


        // Vision Profile 기본 크기로 시작
        readableTextSizeSp =
            profileTextSizeSp


        val panel =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(18),
                    dp(18),
                    dp(18),
                    dp(18)
                )

                background =
                    createRoundedBackground(
                        Color.rgb(
                            12,
                            12,
                            12
                        ),
                        Color.rgb(
                            75,
                            155,
                            255
                        ),
                        24
                    )
            }


        val header =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }


        val title =
            TextView(this).apply {

                text =
                    "질문 답변"

                textSize =
                    23f

                setTextColor(
                    Color.rgb(
                        100,
                        190,
                        255
                    )
                )
            }


        header.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )


        val sizeLabel =
            TextView(this).apply {

                text =
                    "${readableTextSizeSp.toInt()}sp"

                textSize =
                    16f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setPadding(
                    dp(10),
                    dp(6),
                    dp(10),
                    dp(6)
                )

                background =
                    createRoundedBackground(
                        Color.rgb(
                            40,
                            40,
                            40
                        ),
                        Color.rgb(
                            80,
                            80,
                            80
                        ),
                        12
                    )
            }


        header.addView(
            sizeLabel
        )


        panel.addView(
            header
        )


        val questionView =
            TextView(this).apply {

                text =
                    "질문: $question"

                textSize =
                    17f

                setTextColor(
                    Color.LTGRAY
                )

                setPadding(
                    0,
                    dp(10),
                    0,
                    dp(10)
                )
            }


        panel.addView(
            questionView
        )


        val zoomRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL
            }


        val decreaseButton =
            Button(this).apply {

                text =
                    "A−"

                textSize =
                    20f
            }


        val increaseButton =
            Button(this).apply {

                text =
                    "A+"

                textSize =
                    20f
            }


        val decreaseParams =
            LinearLayout.LayoutParams(
                0,
                dp(58),
                1f
            )


        decreaseParams.marginEnd =
            dp(6)


        val increaseParams =
            LinearLayout.LayoutParams(
                0,
                dp(58),
                1f
            )


        increaseParams.marginStart =
            dp(6)


        zoomRow.addView(
            decreaseButton,
            decreaseParams
        )


        zoomRow.addView(
            increaseButton,
            increaseParams
        )


        panel.addView(
            zoomRow
        )


        val scrollView =
            ScrollView(this)


        val answerView =
            TextView(this).apply {

                text =
                    answer


                setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    readableTextSizeSp
                )


                setTextColor(
                    Color.WHITE
                )


                setLineSpacing(
                    dp(10).toFloat(),
                    1.3f
                )


                setPadding(
                    dp(2),
                    dp(18),
                    dp(2),
                    dp(20)
                )
            }


        scrollView.addView(
            answerView
        )


        panel.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )


        fun updateAnswerSize(
            newSize: Float
        ) {

            readableTextSizeSp =
                newSize.coerceIn(
                    MIN_TEXT_SIZE_SP,
                    MAX_TEXT_SIZE_SP
                )


            answerView.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                readableTextSizeSp
            )


            sizeLabel.text =
                "${readableTextSizeSp.toInt()}sp"
        }


        decreaseButton.setOnClickListener {

            updateAnswerSize(
                readableTextSizeSp -
                    TEXT_SIZE_STEP_SP
            )
        }


        increaseButton.setOnClickListener {

            updateAnswerSize(
                readableTextSizeSp +
                    TEXT_SIZE_STEP_SP
            )
        }


        val scaleDetector =
            ScaleGestureDetector(
                this,
                object :
                    ScaleGestureDetector
                        .SimpleOnScaleGestureListener() {


                    override fun onScale(
                        detector: ScaleGestureDetector
                    ): Boolean {

                        updateAnswerSize(
                            readableTextSizeSp *
                                detector.scaleFactor
                        )

                        return true
                    }
                }
            )


        scrollView.setOnTouchListener { _, event ->

            scaleDetector.onTouchEvent(
                event
            )

            event.pointerCount >= 2 ||
                scaleDetector.isInProgress
        }


        val replayAnswerButton =
            Button(this).apply {

                text =
                    "🔊 답변 다시 듣기"

                textSize =
                    18f

                setOnClickListener {

                    if (
                        isTtsReady
                    ) {

                        speakText(
                            answer
                        )

                    } else {

                        showMessagePanel(
                            "음성 읽기",
                            "음성 읽기 기능을 사용할 수 없습니다."
                        )
                    }
                }
            }


        val replayParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            )


        replayParams.topMargin =
            dp(8)

        replayParams.bottomMargin =
            dp(8)


        panel.addView(
            replayAnswerButton,
            replayParams
        )


        val askAgainButton =
            Button(this).apply {

                text =
                    "🎤 다시 질문하기"

                textSize =
                    18f

                setOnClickListener {

                    textToSpeech?.stop()

                    removeResultPanel()

                    startQuestionListening()
                }
            }


        val askAgainParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            )


        askAgainParams.bottomMargin =
            dp(8)


        panel.addView(
            askAgainButton,
            askAgainParams
        )


        val backButton =
            Button(this).apply {

                text =
                    "HALO 메뉴로 돌아가기"

                textSize =
                    17f

                setOnClickListener {

                    textToSpeech?.stop()

                    removeResultPanel()

                    showActionMenu()
                }
            }


        panel.addView(
            backButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            )
        )


        val params =
            WindowManager.LayoutParams(
                dp(370),
                dp(650),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )


        params.gravity =
            Gravity.CENTER


        try {

            windowManager.addView(
                panel,
                params
            )


            resultPanel =
                panel


        } catch (e: Exception) {

            Log.e(
                TAG,
                "질문 답변 패널 표시 실패",
                e
            )
        }
    }


    // =========================================================
    // 일반 메시지 패널
    // =========================================================

    private fun showMessagePanel(
        panelTitle: String,
        message: String
    ) {

        removeResultPanel()


        val panel =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(20),
                    dp(20),
                    dp(20),
                    dp(20)
                )

                background =
                    createRoundedBackground(
                        Color.rgb(
                            18,
                            18,
                            18
                        ),
                        Color.rgb(
                            75,
                            155,
                            255
                        ),
                        24
                    )
            }


        val title =
            TextView(this).apply {

                text =
                    panelTitle

                textSize =
                    23f

                setTextColor(
                    Color.rgb(
                        100,
                        190,
                        255
                    )
                )

                setPadding(
                    0,
                    0,
                    0,
                    dp(16)
                )
            }


        panel.addView(
            title
        )


        val messageView =
            TextView(this).apply {

                text =
                    message

                textSize =
                    18f

                setTextColor(
                    Color.WHITE
                )

                setLineSpacing(
                    dp(5).toFloat(),
                    1.2f
                )
            }


        panel.addView(
            messageView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )


        val backButton =
            Button(this).apply {

                text =
                    "HALO 메뉴로 돌아가기"

                textSize =
                    17f

                setOnClickListener {

                    if (
                        isListeningForQuestion
                    ) {

                        speechRecognizer?.cancel()

                        isListeningForQuestion =
                            false
                    }


                    removeResultPanel()

                    showActionMenu()
                }
            }


        panel.addView(
            backButton
        )


        val params =
            WindowManager.LayoutParams(
                dp(350),
                dp(360),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )


        params.gravity =
            Gravity.CENTER


        try {

            windowManager.addView(
                panel,
                params
            )


            resultPanel =
                panel


        } catch (e: Exception) {

            Log.e(
                TAG,
                "메시지 패널 표시 실패",
                e
            )
        }
    }


    // =========================================================
    // Overlay 제거
    // =========================================================

    private fun removeActionMenu() {

        actionMenu?.let { view ->

            try {

                windowManager.removeView(
                    view
                )

            } catch (_: Exception) {
            }
        }


        actionMenu =
            null
    }


    private fun removeResultPanel() {

        resultPanel?.let { view ->

            try {

                windowManager.removeView(
                    view
                )

            } catch (_: Exception) {
            }
        }


        resultPanel =
            null
    }


    // =========================================================
    // 둥근 배경
    // =========================================================

    private fun createRoundedBackground(
        backgroundColor: Int,
        borderColor: Int,
        radiusDp: Int
    ): GradientDrawable {

        return GradientDrawable().apply {

            shape =
                GradientDrawable.RECTANGLE


            cornerRadius =
                dp(
                    radiusDp
                ).toFloat()


            setColor(
                backgroundColor
            )


            setStroke(
                dp(2),
                borderColor
            )
        }
    }


    // =========================================================
    // dp → px
    // =========================================================

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources
                    .displayMetrics
                    .density
            ).toInt()
    }
}