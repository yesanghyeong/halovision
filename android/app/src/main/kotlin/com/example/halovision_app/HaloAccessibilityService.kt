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

        private const val DEFAULT_TEXT_SIZE_SP = 32f
        private const val MIN_TEXT_SIZE_SP = 30f
        private const val MAX_TEXT_SIZE_SP = 72f
        private const val TEXT_SIZE_STEP_SP = 4f

        private const val SERVER_BASE_URL =
            "http://127.0.0.1:8000"

        private const val DEFAULT_MAGNIFIER_SCALE = 2.0f

        // 돋보기가 종료 영역에 일정 시간 머물면 종료
        private const val MAGNIFIER_DISMISS_DELAY_MS = 300L
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
    // 큰 글자 크기
    // =========================================================

    private var readableTextSizeSp =
        DEFAULT_TEXT_SIZE_SP
    
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


        // 큰 글자 화면 최초 크기에 적용
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
    // TTS
    // =========================================================

    private var textToSpeech: TextToSpeech? = null

    private var isTtsReady =
        false


    // =========================================================
    // 음성 질문
    // =========================================================

    private var speechRecognizer: SpeechRecognizer? = null

    private var isListeningForQuestion =
        false


    // =========================================================
    // 창 돋보기
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

        loadVisionProfile()

        initializeTextToSpeech()

        initializeSpeechRecognizer()

        initializeMagnificationListener()

        showFloatingHaloButton()
    }


    // =========================================================
    // 화면 이벤트 자동 처리 안 함
    //
    // 사용자가 HALO를 눌렀을 때만 화면을 읽음
    // =========================================================

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {
        // intentionally empty
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

            magnificationChangedListener
                ?.let { listener ->

                    try {

                        magnificationController
                            .removeListener(
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
            Gravity.TOP or
                Gravity.END


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

            when (
                event.action
            ) {

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


                    // END 기준이라 X는 반대로 이동
                    params.x =
                        initialX - dx


                    params.y =
                        initialY + dy


                    try {

                        windowManager
                            .updateViewLayout(
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


            Log.d(
                TAG,
                "HALO 플로팅 버튼 표시 완료"
            )


        } catch (e: Exception) {

            Log.e(
                TAG,
                "HALO 플로팅 버튼 표시 실패",
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


        // 현재 실제 앱 화면을 저장
        currentScreenLines =
            captureCurrentScreen()


        Log.d(
            TAG,
            "현재 화면 텍스트 ${currentScreenLines.size}개 확보"
        )


        showActionMenu()
    }


    // =========================================================
    // 현재 화면 Node Tree 읽기
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
    // Node Tree 텍스트 수집
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


        // =====================================================
        // 보기 편하게
        // =====================================================

        val readableButton =
            createActionButton(
                "🔍  보기 편하게",
                "돋보기 또는 큰 글자로 확인"
            )


        readableButton
            .setOnClickListener {

                removeActionMenu()

                showReadableOptionsMenu()
            }


        panel.addView(
            readableButton
        )


        // =====================================================
        // 중요한 내용만
        // =====================================================

        val importantButton =
            createActionButton(
                "★  중요한 내용만",
                "AI가 필요한 핵심 정보만 정리"
            )


        importantButton
            .setOnClickListener {

                removeActionMenu()

                requestImportantScreen()
            }


        panel.addView(
            importantButton
        )


        // =====================================================
        // 읽어줘
        // =====================================================

        val readButton =
            createActionButton(
                "🔊  읽어줘",
                "현재 화면 내용을 음성으로 확인"
            )


        readButton
            .setOnClickListener {

                removeActionMenu()

                startReadingCurrentScreen()
            }


        panel.addView(
            readButton
        )


        // =====================================================
        // 질문하기
        // =====================================================

        val questionButton =
            createActionButton(
                "💬  질문하기",
                "현재 화면에 대해 음성으로 질문"
            )


        questionButton
            .setOnClickListener {

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
                "글자 크기와 확대 설정"
            )


        visionProfileButton.setOnClickListener {

            removeActionMenu()

            showVisionProfileTextSizeMenu()
        }


        panel.addView(
            visionProfileButton
        )


        // =====================================================
        // 닫기
        // =====================================================

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
                LinearLayout
                    .LayoutParams
                    .MATCH_PARENT,
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
                WindowManager.LayoutParams
                    .WRAP_CONTENT,
                WindowManager.LayoutParams
                    .TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams
                    .FLAG_NOT_FOCUSABLE,
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
    // 메뉴 버튼
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
                LinearLayout
                    .LayoutParams
                    .MATCH_PARENT,
                LinearLayout
                    .LayoutParams
                    .WRAP_CONTENT
            )


        params.bottomMargin =
            dp(10)


        container.layoutParams =
            params


        return container
    }

    // =========================================================
    // Vision Profile - 기본 글자 크기 설정
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
                    dp(8)
                )
            }


        panel.addView(
            title
        )


        val description =
            TextView(this).apply {

                text =
                    "기본 글자 크기를 선택해주세요."

                textSize =
                    18f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setPadding(
                    0,
                    0,
                    0,
                    dp(10)
                )
            }


        panel.addView(
            description
        )


        val currentSetting =
            TextView(this).apply {

                text =
                    "현재 설정: ${profileTextSizeSp.toInt()}sp"

                textSize =
                    17f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.LTGRAY
                )

                setPadding(
                    0,
                    0,
                    0,
                    dp(18)
                )
            }


        panel.addView(
            currentSetting
        )


        // =====================================================
        // 글자 크기 선택 함수
        // =====================================================

        fun selectTextSize(
            size: Float
        ) {

            saveVisionProfile(
                textSizeSp = size,
                magnifierScale = profileMagnifierScale,
                readQuestionAutomatically =
                    autoReadQuestionAnswer,
                readImportantAutomatically =
                    autoReadImportantResult
            )


            currentSetting.text =
                "현재 설정: ${size.toInt()}sp"
        }


        // =====================================================
        // 32sp
        // =====================================================

        val size32Button =
            Button(this).apply {

                text =
                    "32sp"

                textSize =
                    18f

                setOnClickListener {

                    selectTextSize(
                        32f
                    )
                }
            }


        val optionParams1 =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            )


        optionParams1.bottomMargin =
            dp(8)


        panel.addView(
            size32Button,
            optionParams1
        )


        // =====================================================
        // 40sp
        // =====================================================

        val size40Button =
            Button(this).apply {

                text =
                    "40sp"

                textSize =
                    18f

                setOnClickListener {

                    selectTextSize(
                        40f
                    )
                }
            }


        val optionParams2 =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            )


        optionParams2.bottomMargin =
            dp(8)


        panel.addView(
            size40Button,
            optionParams2
        )


        // =====================================================
        // 48sp
        // =====================================================

        val size48Button =
            Button(this).apply {

                text =
                    "48sp"

                textSize =
                    18f

                setOnClickListener {

                    selectTextSize(
                        48f
                    )
                }
            }


        val optionParams3 =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            )


        optionParams3.bottomMargin =
            dp(8)


        panel.addView(
            size48Button,
            optionParams3
        )


        // =====================================================
        // 56sp
        // =====================================================

        val size56Button =
            Button(this).apply {

                text =
                    "56sp"

                textSize =
                    18f

                setOnClickListener {

                    selectTextSize(
                        56f
                    )
                }
            }


        val optionParams4 =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            )


        optionParams4.bottomMargin =
            dp(14)


        panel.addView(
            size56Button,
            optionParams4
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
                "Vision Profile 화면 표시 실패",
                e
            )
        }
    }

    // =========================================================
    // 보기 편하게 - 선택 화면
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


        // =====================================================
        // 창 돋보기
        // =====================================================

        val magnifierButton =
            createActionButton(
                "🔎  돋보기로 보기",
                "원래 화면을 이동 가능한 돋보기로 확대"
            )


        magnifierButton
            .setOnClickListener {

                removeResultPanel()

                startWindowMagnifier()
            }


        panel.addView(
            magnifierButton
        )


        // =====================================================
        // 큰 글자로 보기
        // =====================================================

        val largeTextButton =
            createActionButton(
                "📝  큰 글자로 보기",
                "화면의 글자를 추출하여 크게 표시"
            )


        largeTextButton
            .setOnClickListener {

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
                    currentScreenLines
                        .joinToString(
                            "\n\n"
                        )
                )
            }


        panel.addView(
            largeTextButton
        )


        // =====================================================
        // 돌아가기
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


        val backParams =
            LinearLayout.LayoutParams(
                LinearLayout
                    .LayoutParams
                    .MATCH_PARENT,
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
                WindowManager.LayoutParams
                    .WRAP_CONTENT,
                WindowManager.LayoutParams
                    .TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams
                    .FLAG_NOT_FOCUSABLE,
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
    // 창 돋보기 변경 리스너
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


                // Android 13 이상
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


                    // 창 돋보기가 꺼졌거나
                    // 다른 확대 모드로 바뀐 경우
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


                        mainHandler
                            .removeCallbacks(
                                magnifierDismissRunnable
                            )


                        hideMagnifierTrashZone()


                        return
                    }


                    checkMagnifierTrashZone(
                        region
                    )
                }


                // 구형 Fullscreen Magnification 콜백
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

                    // Window Magnification에서는 사용하지 않음
                }
            }


        try {

            magnificationController
                .addListener(
                    listener,
                    mainHandler
                )


            magnificationChangedListener =
                listener


            Log.d(
                TAG,
                "돋보기 변경 리스너 등록 완료"
            )


        } catch (e: Exception) {

            Log.e(
                TAG,
                "돋보기 변경 리스너 등록 실패",
                e
            )
        }
    }


    // =========================================================
    // 이동 가능한 창 돋보기 시작
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


            val centerX =
                bounds.centerX()
                    .toFloat()


            val centerY =
                bounds.centerY()
                    .toFloat()


            val config =
                MagnificationConfig
                    .Builder()
                    .setMode(
                        MagnificationConfig
                            .MAGNIFICATION_MODE_WINDOW
                    )
                    .setScale(
                        profileMagnifierScale
                    )
                    .setCenterX(
                        centerX
                    )
                    .setCenterY(
                        centerY
                    )
                    .build()


            val success =
                magnificationController
                    .setMagnificationConfig(
                        config,
                        true
                    )


            Log.d(
                TAG,
                "창 돋보기 시작 / 성공: $success"
            )


            if (
                success
            ) {

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


        mainHandler
            .removeCallbacks(
                magnifierDismissRunnable
            )


        // 종료 콜백이 들어와도
        // 중복 처리하지 않도록 먼저 false
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


            Log.d(
                TAG,
                "창 돋보기 종료 / 성공: $success"
            )


            if (
                success
            ) {

                hideMagnifierTrashZone()


            } else {

                // 실패했으면 다시 활성 상태
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
    // 쓰레기통 종료 영역 표시
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
                WindowManager.LayoutParams
                    .TYPE_ACCESSIBILITY_OVERLAY,

                // 화면 조작을 방해하지 않음
                WindowManager.LayoutParams
                    .FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams
                        .FLAG_NOT_TOUCHABLE,

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
// 돋보기와 쓰레기통 영역 충돌 확인
//
// 쓰레기통 위에서 계속 이동 중이면 종료하지 않고,
// 이동이 멈춘 뒤 일정 시간이 지나면 종료한다.
// → 사용자 입장에서는 "놓으면 종료"처럼 동작
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


    // ---------------------------------------------------------
    // 쓰레기통 감지 영역
    // ---------------------------------------------------------

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
        magnifierBounds.right >= detectionLeft &&
            magnifierBounds.left <= detectionRight


    val verticalOverlap =
        magnifierBounds.bottom >= detectionTop


    val overTrash =
        horizontalOverlap &&
            verticalOverlap


    // =========================================================
    // 쓰레기통 영역 안
    // =========================================================

    if (overTrash) {

        // 처음 진입했을 때만 UI 변경
        if (!isMagnifierOverTrash) {

            isMagnifierOverTrash =
                true


            magnifierTrashView
                ?.apply {

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


        // -----------------------------------------------------
        // 핵심
        //
        // 돋보기가 움직일 때마다 이 함수가 다시 호출되므로
        // 기존 종료 예약을 계속 취소한다.
        //
        // 사용자가 손을 떼어 움직임이 멈추면
        // 더 이상 콜백이 들어오지 않고
        // 마지막 예약만 실행된다.
        // -----------------------------------------------------

        mainHandler.removeCallbacks(
            magnifierDismissRunnable
        )


        mainHandler.postDelayed(
            magnifierDismissRunnable,
            MAGNIFIER_DISMISS_DELAY_MS
        )


    } else {

        // =====================================================
        // 쓰레기통 영역 밖
        // =====================================================

        mainHandler.removeCallbacks(
            magnifierDismissRunnable
        )


        if (isMagnifierOverTrash) {

            isMagnifierOverTrash =
                false


            magnifierTrashView
                ?.apply {

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
    // 쓰레기통 제거
    // =========================================================

    private fun hideMagnifierTrashZone() {

        mainHandler
            .removeCallbacks(
                magnifierDismissRunnable
            )


        magnifierTrashView
            ?.let { view ->

                try {

                    if (
                        ::windowManager
                            .isInitialized
                    ) {

                        windowManager
                            .removeView(
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
            currentScreenLines
                .joinToString(
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


                connection
                    .setRequestProperty(
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


                connection
                    .outputStream
                    .use { output ->

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

                connection
                    ?.disconnect()
            }

        }.start()
    }


    // =========================================================
    // 중요한 정보 결과
    // =========================================================

    private fun showImportantResultPanel(
        answer: String
    ) {

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
    // 공통 확대 텍스트 화면
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
                LinearLayout
                    .LayoutParams
                    .WRAP_CONTENT,
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


        // =====================================================
        // A- / A+
        // =====================================================

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


        // =====================================================
        // 텍스트
        // =====================================================

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
                LinearLayout
                    .LayoutParams
                    .MATCH_PARENT,
                0,
                1f
            )
        )


        fun updateTextSize(
            newSize: Float
        ) {

            readableTextSizeSp =
                newSize
                    .coerceIn(
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


        decreaseButton
            .setOnClickListener {

                updateTextSize(
                    readableTextSizeSp -
                        TEXT_SIZE_STEP_SP
                )
            }


        increaseButton
            .setOnClickListener {

                updateTextSize(
                    readableTextSizeSp +
                        TEXT_SIZE_STEP_SP
                )
            }


        // =====================================================
        // 핀치 확대
        // =====================================================

        val scaleDetector =
            ScaleGestureDetector(
                this,
                object :
                    ScaleGestureDetector
                        .SimpleOnScaleGestureListener() {


                    override fun onScale(
                        detector:
                            ScaleGestureDetector
                    ): Boolean {

                        updateTextSize(
                            readableTextSizeSp *
                                detector
                                    .scaleFactor
                        )


                        return true
                    }
                }
            )


        scrollView
            .setOnTouchListener { _, event ->

                scaleDetector
                    .onTouchEvent(
                        event
                    )


                event.pointerCount >= 2 ||
                    scaleDetector.isInProgress
            }


        // =====================================================
        // 선택적 TTS 버튼
        // =====================================================

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
                    LinearLayout
                        .LayoutParams
                        .MATCH_PARENT,
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

                    textToSpeech
                        ?.stop()


                    removeResultPanel()


                    showActionMenu()
                }
            }


        panel.addView(
            backButton,
            LinearLayout.LayoutParams(
                LinearLayout
                    .LayoutParams
                    .MATCH_PARENT,
                dp(56)
            )
        )


        val params =
            WindowManager.LayoutParams(
                dp(370),
                dp(650),
                WindowManager.LayoutParams
                    .TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams
                    .FLAG_NOT_FOCUSABLE,
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
                        textToSpeech
                            ?.setLanguage(
                                Locale.KOREAN
                            )


                    isTtsReady =
                        result !=
                            TextToSpeech
                                .LANG_MISSING_DATA &&
                            result !=
                            TextToSpeech
                                .LANG_NOT_SUPPORTED


                    if (
                        isTtsReady
                    ) {

                        textToSpeech
                            ?.setSpeechRate(
                                0.9f
                            )


                        textToSpeech
                            ?.setPitch(
                                1.0f
                            )


                        Log.d(
                            TAG,
                            "HALO TTS 초기화 성공"
                        )


                    } else {

                        Log.e(
                            TAG,
                            "한국어 TTS 사용 불가"
                        )
                    }


                } else {

                    isTtsReady =
                        false


                    Log.e(
                        TAG,
                        "HALO TTS 초기화 실패"
                    )
                }
            }
    }


    // =========================================================
    // 현재 화면 읽기
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
            currentScreenLines
                .joinToString(
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


        textToSpeech
            ?.stop()


        val chunks =
            splitTextForTts(
                text
            )


        chunks
            .forEachIndexed {
                index,
                chunk ->


                val mode =
                    if (
                        index == 0
                    ) {

                        TextToSpeech
                            .QUEUE_FLUSH

                    } else {

                        TextToSpeech
                            .QUEUE_ADD
                    }


                textToSpeech
                    ?.speak(
                        chunk,
                        mode,
                        null,
                        "halo_tts_$index"
                    )
            }
    }


    // =========================================================
    // 긴 TTS 텍스트 분할
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
                    candidate
                        .lastIndexOf(
                            '\n'
                        ),
                    candidate
                        .lastIndexOf(
                            ". "
                        ),
                    candidate
                        .lastIndexOf(
                            ' '
                        )
                )


            if (
                cutIndex <
                maxLength / 2
            ) {

                cutIndex =
                    maxLength
            }


            val chunk =
                remaining
                    .substring(
                        0,
                        cutIndex
                    )
                    .trim()


            if (
                chunk.isNotEmpty()
            ) {

                chunks.add(
                    chunk
                )
            }


            remaining =
                remaining
                    .substring(
                        cutIndex
                    )
                    .trim()
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
    // TTS 제어 화면
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
                LinearLayout
                    .LayoutParams
                    .MATCH_PARENT,
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
                LinearLayout
                    .LayoutParams
                    .MATCH_PARENT,
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

                    textToSpeech
                        ?.stop()


                    status.text =
                        "읽기를 중지했습니다."
                }
            }


        val stopParams =
            LinearLayout.LayoutParams(
                LinearLayout
                    .LayoutParams
                    .MATCH_PARENT,
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

                    textToSpeech
                        ?.stop()


                    removeResultPanel()


                    showActionMenu()
                }
            }


        panel.addView(
            backButton,
            LinearLayout.LayoutParams(
                LinearLayout
                    .LayoutParams
                    .MATCH_PARENT,
                dp(58)
            )
        )


        val params =
            WindowManager.LayoutParams(
                dp(360),
                dp(430),
                WindowManager.LayoutParams
                    .TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams
                    .FLAG_NOT_FOCUSABLE,
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
            !SpeechRecognizer
                .isRecognitionAvailable(
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
            SpeechRecognizer
                .createSpeechRecognizer(
                    this
                )


        speechRecognizer
            ?.setRecognitionListener(

                object :
                    RecognitionListener {


                    override fun onReadyForSpeech(
                        params: Bundle?
                    ) {

                        Log.d(
                            TAG,
                            "질문 음성 인식 준비"
                        )
                    }


                    override fun onBeginningOfSpeech() {

                        Log.d(
                            TAG,
                            "질문 음성 입력 시작"
                        )
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

                        Log.d(
                            TAG,
                            "질문 음성 입력 종료"
                        )
                    }


                    override fun onError(
                        error: Int
                    ) {

                        isListeningForQuestion =
                            false


                        Log.e(
                            TAG,
                            "음성 인식 오류: $error"
                        )


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
                            results
                                ?.getStringArrayList(
                                    SpeechRecognizer
                                        .RESULTS_RECOGNITION
                                )


                        val question =
                            matches
                                ?.firstOrNull()
                                ?.trim()


                        if (
                            question
                                .isNullOrEmpty()
                        ) {

                            mainHandler.post {

                                showMessagePanel(
                                    "질문하기",
                                    "질문을 인식하지 못했습니다."
                                )
                            }


                            return
                        }


                        Log.d(
                            TAG,
                            "인식된 질문: $question"
                        )


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
    // 질문 음성 입력 시작
    // =========================================================

    private fun startQuestionListening() {

        textToSpeech
            ?.stop()


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
                Manifest.permission
                    .RECORD_AUDIO
            ) !=
            PackageManager
                .PERMISSION_GRANTED
        ) {

            showMessagePanel(
                "질문하기",
                "마이크 권한이 필요합니다.\n\nHaloVision 앱 설정에서 마이크 권한을 허용해주세요."
            )


            return
        }


        if (
            speechRecognizer ==
            null
        ) {

            initializeSpeechRecognizer()
        }


        if (
            speechRecognizer ==
            null
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

            speechRecognizer
                ?.cancel()


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
                RecognizerIntent
                    .ACTION_RECOGNIZE_SPEECH
            ).apply {


                putExtra(
                    RecognizerIntent
                        .EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent
                        .LANGUAGE_MODEL_FREE_FORM
                )


                putExtra(
                    RecognizerIntent
                        .EXTRA_LANGUAGE,
                    "ko-KR"
                )


                putExtra(
                    RecognizerIntent
                        .EXTRA_LANGUAGE_PREFERENCE,
                    "ko-KR"
                )


                putExtra(
                    RecognizerIntent
                        .EXTRA_PARTIAL_RESULTS,
                    false
                )


                putExtra(
                    RecognizerIntent
                        .EXTRA_MAX_RESULTS,
                    1
                )
            }


        isListeningForQuestion =
            true


        speechRecognizer
            ?.startListening(
                intent
            )
    }


    // =========================================================
    // 질문 → FastAPI → GPT
    // =========================================================

    private fun requestScreenQuestion(
        question: String
    ) {

        val screenText =
            currentScreenLines
                .joinToString(
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


                connection
                    .setRequestProperty(
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


                connection
                    .outputStream
                    .use { output ->

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
                    connection
                        .responseCode


                val stream =
                    if (
                        responseCode in
                        200..299
                    ) {

                        connection
                            .inputStream

                    } else {

                        connection
                            .errorStream
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


                Log.d(
                    TAG,
                    "질문 API 응답: $responseText"
                )


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


                        // 질문 답변 자동 읽기
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

                connection
                    ?.disconnect()
            }

        }.start()
    }


    // =========================================================
    // 질문 답변 전용 화면
    // =========================================================

    private fun showQuestionAnswerPanel(
        question: String,
        answer: String
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
                LinearLayout
                    .LayoutParams
                    .WRAP_CONTENT,
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


        // =====================================================
        // 크기 버튼
        // =====================================================

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
                LinearLayout
                    .LayoutParams
                    .MATCH_PARENT,
                0,
                1f
            )
        )


        fun updateAnswerSize(
            newSize: Float
        ) {

            readableTextSizeSp =
                newSize
                    .coerceIn(
                        MIN_TEXT_SIZE_SP,
                        MAX_TEXT_SIZE_SP
                    )


            answerView
                .setTextSize(
                    TypedValue
                        .COMPLEX_UNIT_SP,
                    readableTextSizeSp
                )


            sizeLabel.text =
                "${readableTextSizeSp.toInt()}sp"
        }


        decreaseButton
            .setOnClickListener {

                updateAnswerSize(
                    readableTextSizeSp -
                        TEXT_SIZE_STEP_SP
                )
            }


        increaseButton
            .setOnClickListener {

                updateAnswerSize(
                    readableTextSizeSp +
                        TEXT_SIZE_STEP_SP
                )
            }


        // =====================================================
        // 핀치 확대
        // =====================================================

        val scaleDetector =
            ScaleGestureDetector(
                this,
                object :
                    ScaleGestureDetector
                        .SimpleOnScaleGestureListener() {


                    override fun onScale(
                        detector:
                            ScaleGestureDetector
                    ): Boolean {

                        updateAnswerSize(
                            readableTextSizeSp *
                                detector.scaleFactor
                        )


                        return true
                    }
                }
            )


        scrollView
            .setOnTouchListener { _, event ->

                scaleDetector
                    .onTouchEvent(
                        event
                    )


                event.pointerCount >= 2 ||
                    scaleDetector.isInProgress
            }


        // =====================================================
        // 답변 다시 듣기
        // =====================================================

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
                LinearLayout
                    .LayoutParams
                    .MATCH_PARENT,
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


        // =====================================================
        // 다시 질문하기
        // =====================================================

        val askAgainButton =
            Button(this).apply {

                text =
                    "🎤 다시 질문하기"

                textSize =
                    18f


                setOnClickListener {

                    textToSpeech
                        ?.stop()


                    removeResultPanel()


                    // 기존 currentScreenLines 유지
                    startQuestionListening()
                }
            }


        val askAgainParams =
            LinearLayout.LayoutParams(
                LinearLayout
                    .LayoutParams
                    .MATCH_PARENT,
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

                    textToSpeech
                        ?.stop()


                    removeResultPanel()


                    showActionMenu()
                }
            }


        panel.addView(
            backButton,
            LinearLayout.LayoutParams(
                LinearLayout
                    .LayoutParams
                    .MATCH_PARENT,
                dp(56)
            )
        )


        val params =
            WindowManager.LayoutParams(
                dp(370),
                dp(650),
                WindowManager.LayoutParams
                    .TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams
                    .FLAG_NOT_FOCUSABLE,
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
    // 일반 메시지 화면
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
                LinearLayout
                    .LayoutParams
                    .MATCH_PARENT,
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

                        speechRecognizer
                            ?.cancel()


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
                WindowManager.LayoutParams
                    .TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams
                    .FLAG_NOT_FOCUSABLE,
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
    // 메뉴 제거
    // =========================================================

    private fun removeActionMenu() {

        actionMenu
            ?.let { view ->

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


    // =========================================================
    // 결과 패널 제거
    // =========================================================

    private fun removeResultPanel() {

        resultPanel
            ?.let { view ->

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

        return GradientDrawable()
            .apply {

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