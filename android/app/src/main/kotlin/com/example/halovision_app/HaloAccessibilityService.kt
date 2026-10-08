package com.example.halovision_app

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
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
    }


    // =========================================================
    // 기본 객체
    // =========================================================

    private lateinit var windowManager: WindowManager

    private val mainHandler =
        Handler(Looper.getMainLooper())


    // =========================================================
    // TTS
    // =========================================================

    private var textToSpeech: TextToSpeech? = null

    private var isTtsReady: Boolean = false


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
    // 현재 확대 글자 크기
    // =========================================================

    private var readableTextSizeSp: Float =
        DEFAULT_TEXT_SIZE_SP


    // =========================================================
    // AccessibilityService 연결
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


        initializeTextToSpeech()

        showFloatingHaloButton()
    }


    // =========================================================
    // 화면 변화 자동 분석 안 함
    // =========================================================

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {

        // 사용자가 HALO를 눌렀을 때만
        // 현재 화면 Node Tree를 읽음
    }


    override fun onInterrupt() {

        Log.d(
            TAG,
            "Halo AccessibilityService 중단됨"
        )
    }


    // =========================================================
    // Service 종료
    // =========================================================

    override fun onDestroy() {

        removeActionMenu()

        removeResultPanel()


        floatingButton?.let { button ->

            try {

                windowManager.removeView(
                    button
                )

            } catch (_: Exception) {
            }
        }


        floatingButton =
            null


        // TTS 정리
        textToSpeech?.stop()

        textToSpeech?.shutdown()

        textToSpeech =
            null

        isTtsReady =
            false


        super.onDestroy()
    }


    // =========================================================
    // HALO 플로팅 버튼
    // =========================================================

    private fun showFloatingHaloButton() {

        if (floatingButton != null) {
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
                                20,
                                80,
                                190
                            )
                        )


                        setStroke(
                            dp(2),
                            Color.rgb(
                                100,
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


        // =====================================================
        // 드래그 + 클릭
        // =====================================================

        var initialX = 0
        var initialY = 0

        var initialTouchX = 0f
        var initialTouchY = 0f

        var moved = false


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

                    } catch (
                        e: Exception
                    ) {

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

        } catch (
            e: Exception
        ) {

            Log.e(
                TAG,
                "HALO 플로팅 버튼 표시 실패",
                e
            )
        }
    }


    // =========================================================
    // HALO 클릭
    // =========================================================

    private fun onHaloButtonClicked() {

        textToSpeech?.stop()

        removeActionMenu()

        removeResultPanel()


        // Overlay가 뜨기 전에
        // 원래 앱의 화면을 먼저 읽음
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


        val screenTexts =
            mutableListOf<String>()


        collectTexts(
            node = rootNode,
            result = screenTexts
        )


        return screenTexts
            .map { value ->

                value.trim()
            }
            .filter { value ->

                value.isNotEmpty()
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

        if (node == null) {
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


        val nodeDescription =
            node.contentDescription
                ?.toString()
                ?.trim()


        if (
            !nodeDescription.isNullOrEmpty()
        ) {

            result.add(
                nodeDescription
            )
        }


        for (
            index in
            0 until node.childCount
        ) {

            collectTexts(

                node =
                    node.getChild(
                        index
                    ),

                result =
                    result
            )
        }
    }


    // =========================================================
    // HALO 메뉴
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

                        backgroundColor =
                            Color.rgb(
                                18,
                                18,
                                18
                            ),

                        borderColor =
                            Color.rgb(
                                75,
                                155,
                                255
                            ),

                        radiusDp =
                            24
                    )
            }


        // =====================================================
        // HALO 제목
        // =====================================================

        val title =
            TextView(this).apply {

                text =
                    "HALO"

                textSize =
                    26f

                setTextColor(
                    Color.rgb(
                        100,
                        190,
                        255
                    )
                )

                gravity =
                    Gravity.CENTER


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


        // =====================================================
        // 설명
        // =====================================================

        val description =
            TextView(this).apply {

                text =
                    "무엇을 도와드릴까요?"

                textSize =
                    19f

                setTextColor(
                    Color.WHITE
                )

                gravity =
                    Gravity.CENTER


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


        // =====================================================
        // 화면 Context 상태
        // =====================================================

        val contextStatus =
            TextView(this).apply {

                text =
                    if (
                        currentScreenLines
                            .isEmpty()
                    ) {

                        "현재 화면에서 읽을 수 있는 정보를 찾지 못했습니다."

                    } else {

                        "현재 화면 정보를 불러왔습니다."
                    }


                textSize =
                    14f


                setTextColor(
                    Color.LTGRAY
                )


                gravity =
                    Gravity.CENTER


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

                buttonTitle =
                    "🔍  보기 편하게",

                buttonSubtitle =
                    "큰 글자와 확대 기능으로 확인"
            )


        readableButton
            .setOnClickListener {

                removeActionMenu()


                if (
                    currentScreenLines
                        .isEmpty()
                ) {

                    showMessagePanel(

                        panelTitle =
                            "보기 편하게",

                        message =
                            "현재 화면에서 읽을 수 있는 텍스트가 없습니다."
                    )


                    return@setOnClickListener
                }


                showReadablePanel(

                    currentScreenLines
                        .joinToString(
                            separator =
                                "\n\n"
                        )
                )
            }


        panel.addView(
            readableButton
        )


        // =====================================================
        // 중요한 내용만
        // =====================================================

        val importantButton =
            createActionButton(

                buttonTitle =
                    "★  중요한 내용만",

                buttonSubtitle =
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

                buttonTitle =
                    "🔊  읽어줘",

                buttonSubtitle =
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

                buttonTitle =
                    "💬  질문하기",

                buttonSubtitle =
                    "현재 화면에 대해 이어서 질문"
            )


        questionButton
            .setOnClickListener {

                removeActionMenu()


                showMessagePanel(

                    panelTitle =
                        "질문하기",

                    message =
                        "현재 화면 Context는 저장되어 있습니다.\n\n" +
                            "이후 ScreenSession 기반 질문 기능을 연결합니다."
                )
            }


        panel.addView(
            questionButton
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

                LinearLayout.LayoutParams.MATCH_PARENT,

                dp(52)
            )


        closeParams.topMargin =
            dp(14)


        panel.addView(
            closeButton,
            closeParams
        )


        // =====================================================
        // Overlay
        // =====================================================

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

        } catch (
            e: Exception
        ) {

            Log.e(
                TAG,
                "HALO 메뉴 표시 실패",
                e
            )
        }
    }


    // =========================================================
    // HALO 메뉴 버튼 생성
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

                        backgroundColor =
                            Color.rgb(
                                38,
                                38,
                                38
                            ),

                        borderColor =
                            Color.rgb(
                                70,
                                70,
                                70
                            ),

                        radiusDp =
                            16
                    )


                isClickable =
                    true

                isFocusable =
                    true
            }


        val titleView =
            TextView(this).apply {

                text =
                    buttonTitle

                textSize =
                    18f

                setTextColor(
                    Color.WHITE
                )
            }


        val subtitleView =
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
            titleView
        )


        container.addView(
            subtitleView
        )


        val params =
            LinearLayout.LayoutParams(

                LinearLayout.LayoutParams
                    .MATCH_PARENT,

                LinearLayout.LayoutParams
                    .WRAP_CONTENT
            )


        params.bottomMargin =
            dp(10)


        container.layoutParams =
            params


        return container
    }


    // =========================================================
    // 보기 편하게
    // =========================================================

    private fun showReadablePanel(
        screenText: String
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

                        backgroundColor =
                            Color.rgb(
                                12,
                                12,
                                12
                            ),

                        borderColor =
                            Color.rgb(
                                75,
                                155,
                                255
                            ),

                        radiusDp =
                            24
                    )
            }


        // =====================================================
        // 제목 + 글자 크기
        // =====================================================

        val headerRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }


        val title =
            TextView(this).apply {

                text =
                    "보기 편하게"

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


        headerRow.addView(

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

                setTextColor(
                    Color.WHITE
                )

                gravity =
                    Gravity.CENTER


                setPadding(
                    dp(10),
                    dp(6),
                    dp(10),
                    dp(6)
                )


                background =
                    createRoundedBackground(

                        backgroundColor =
                            Color.rgb(
                                40,
                                40,
                                40
                            ),

                        borderColor =
                            Color.rgb(
                                80,
                                80,
                                80
                            ),

                        radiusDp =
                            12
                    )
            }


        headerRow.addView(
            sizeLabel
        )


        panel.addView(
            headerRow
        )


        // =====================================================
        // 확대 안내
        // =====================================================

        val zoomGuide =
            TextView(this).apply {

                text =
                    "두 손가락을 벌리거나 모아서 글자 크기를 조절하세요."

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
            zoomGuide
        )


        // =====================================================
        // A- / A+
        // =====================================================

        val zoomButtonRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER
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


        zoomButtonRow.addView(
            decreaseButton,
            decreaseParams
        )


        zoomButtonRow.addView(
            increaseButton,
            increaseParams
        )


        panel.addView(
            zoomButtonRow
        )


        // =====================================================
        // 텍스트
        // =====================================================

        val scrollView =
            ScrollView(this)


        val content =
            TextView(this).apply {

                text =
                    screenText


                setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    readableTextSizeSp
                )


                setTextColor(
                    Color.WHITE
                )


                setLineSpacing(
                    dp(10).toFloat(),
                    1.25f
                )


                setPadding(
                    dp(2),
                    dp(16),
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


        // =====================================================
        // 글자 크기 변경
        // =====================================================

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
        // 핀치 줌
        // =====================================================

        val scaleGestureDetector =
            ScaleGestureDetector(

                this,

                object :
                    ScaleGestureDetector
                        .SimpleOnScaleGestureListener() {

                    override fun onScale(
                        detector:
                            ScaleGestureDetector
                    ): Boolean {

                        val newSize =

                            readableTextSizeSp *
                                detector.scaleFactor


                        updateTextSize(
                            newSize
                        )


                        return true
                    }
                }
            )


        scrollView
            .setOnTouchListener { _, event ->

                scaleGestureDetector
                    .onTouchEvent(
                        event
                    )


                event.pointerCount >= 2 ||
                    scaleGestureDetector
                        .isInProgress
            }


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

                LinearLayout.LayoutParams.MATCH_PARENT,

                dp(56)
            )


        backParams.topMargin =
            dp(10)


        panel.addView(
            backButton,
            backParams
        )


        // =====================================================
        // Overlay
        // =====================================================

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

        } catch (
            e: Exception
        ) {

            Log.e(
                TAG,
                "보기 편하게 패널 표시 실패",
                e
            )
        }
    }


    // =========================================================
    // 중요한 내용만 → FastAPI → GPT
    // =========================================================

    private fun requestImportantScreen() {

        if (
            currentScreenLines.isEmpty()
        ) {

            showMessagePanel(

                panelTitle =
                    "중요한 내용만",

                message =
                    "현재 화면에서 읽을 수 있는 정보가 없습니다."
            )

            return
        }


        val screenText =

            currentScreenLines
                .joinToString(
                    separator =
                        "\n"
                )


        // 로딩 화면
        showMessagePanel(

            panelTitle =
                "중요한 내용만",

            message =
                "현재 화면의 중요한 정보를 분석하고 있습니다..."
        )


        Thread {

            var connection:
                HttpURLConnection? = null


            try {

                // adb reverse 사용 중
                val url =
                    URL(
                        "http://127.0.0.1:8000/screen/important"
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


                val requestBytes =
                    requestJson
                        .toString()
                        .toByteArray(
                            Charsets.UTF_8
                        )


                connection.outputStream
                    .use { outputStream ->

                        outputStream.write(
                            requestBytes
                        )

                        outputStream.flush()
                    }


                val responseCode =
                    connection.responseCode


                val responseStream =

                    if (
                        responseCode in 200..299
                    ) {

                        connection.inputStream

                    } else {

                        connection.errorStream
                    }


                val responseText =
                    responseStream
                        ?.bufferedReader(
                            Charsets.UTF_8
                        )
                        ?.use { reader ->

                            reader.readText()
                        }
                        ?: ""


                Log.d(
                    TAG,
                    "FastAPI 응답 코드: $responseCode"
                )


                Log.d(
                    TAG,
                    "FastAPI 응답: $responseText"
                )


                if (
                    responseCode in 200..299
                ) {

                    val responseJson =
                        JSONObject(
                            responseText
                        )


                    val answer =
                        responseJson.optString(

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

                            panelTitle =
                                "중요한 내용만",

                            message =
                                "서버 오류가 발생했습니다.\n\n" +
                                    "응답 코드: $responseCode"
                        )
                    }
                }


            } catch (
                e: Exception
            ) {

                Log.e(
                    TAG,
                    "FastAPI 연결 실패",
                    e
                )


                mainHandler.post {

                    showMessagePanel(

                        panelTitle =
                            "중요한 내용만",

                        message =
                            "서버에 연결할 수 없습니다.\n\n" +
                                "FastAPI 서버와 adb reverse 상태를 확인해주세요."
                    )
                }

            } finally {

                connection
                    ?.disconnect()
            }

        }.start()
    }


    // =========================================================
    // 중요한 내용만 - AI 결과 확대 화면
    // =========================================================

    private fun showImportantResultPanel(
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

                        backgroundColor =
                            Color.rgb(
                                12,
                                12,
                                12
                            ),

                        borderColor =
                            Color.rgb(
                                75,
                                155,
                                255
                            ),

                        radiusDp =
                            24
                    )
            }


        // =====================================================
        // 제목 + 크기
        // =====================================================

        val headerRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }


        val titleView =
            TextView(this).apply {

                text =
                    "중요한 내용만"

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


        headerRow.addView(

            titleView,

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

                setTextColor(
                    Color.WHITE
                )

                gravity =
                    Gravity.CENTER


                setPadding(
                    dp(10),
                    dp(6),
                    dp(10),
                    dp(6)
                )


                background =
                    createRoundedBackground(

                        backgroundColor =
                            Color.rgb(
                                40,
                                40,
                                40
                            ),

                        borderColor =
                            Color.rgb(
                                80,
                                80,
                                80
                            ),

                        radiusDp =
                            12
                    )
            }


        headerRow.addView(
            sizeLabel
        )


        panel.addView(
            headerRow
        )


        // =====================================================
        // 설명
        // =====================================================

        val guideText =
            TextView(this).apply {

                text =
                    "AI가 현재 화면에서 핵심 정보만 정리했습니다."

                textSize =
                    16f

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
            guideText
        )


        // =====================================================
        // A- / A+
        // =====================================================

        val zoomButtonRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER
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


        zoomButtonRow.addView(
            decreaseButton,
            decreaseParams
        )


        zoomButtonRow.addView(
            increaseButton,
            increaseParams
        )


        panel.addView(
            zoomButtonRow
        )


        // =====================================================
        // AI 결과
        // =====================================================

        val scrollView =
            ScrollView(this)


        val content =
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


        // =====================================================
        // 글자 크기 변경
        // =====================================================

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

        val scaleGestureDetector =
            ScaleGestureDetector(

                this,

                object :
                    ScaleGestureDetector
                        .SimpleOnScaleGestureListener() {

                    override fun onScale(
                        detector:
                            ScaleGestureDetector
                    ): Boolean {

                        val newSize =
                            readableTextSizeSp *
                                detector.scaleFactor


                        updateTextSize(
                            newSize
                        )


                        return true
                    }
                }
            )


        scrollView
            .setOnTouchListener { _, event ->

                scaleGestureDetector
                    .onTouchEvent(
                        event
                    )


                event.pointerCount >= 2 ||
                    scaleGestureDetector
                        .isInProgress
            }
        


        // =====================================================
    // AI 핵심 내용 읽어주기
    // =====================================================

    val readImportantButton =
        Button(this).apply {

            text =
                "🔊 핵심 내용 읽어주기"

            textSize =
                18f


            setOnClickListener {

                if (!isTtsReady) {

                    showMessagePanel(

                        panelTitle =
                            "음성 읽기",

                        message =
                            "음성 읽기 기능을 준비하지 못했습니다.\n\n" +
                                "휴대폰의 TTS 설정을 확인해주세요."
                    )

                    return@setOnClickListener
                }


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


    val readImportantParams =
        LinearLayout.LayoutParams(

            LinearLayout.LayoutParams.MATCH_PARENT,

            dp(58)
        )


    readImportantParams.bottomMargin =
        dp(10)


    panel.addView(
        readImportantButton,
        readImportantParams
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

                LinearLayout.LayoutParams.MATCH_PARENT,

                dp(56)
            )


        backParams.topMargin =
            dp(10)


        panel.addView(
            backButton,
            backParams
        )


        // =====================================================
        // Overlay
        // =====================================================

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

        } catch (
            e: Exception
        ) {

            Log.e(
                TAG,
                "AI 결과 패널 표시 실패",
                e
            )
        }
    }


    // =========================================================
    // Android TTS 초기화
    // =========================================================

    private fun initializeTextToSpeech() {

        if (textToSpeech != null) {
            return
        }


        textToSpeech =
            TextToSpeech(this) { status ->

                if (
                    status ==
                    TextToSpeech.SUCCESS
                ) {

                    val languageResult =
                        textToSpeech?.setLanguage(
                            Locale.KOREAN
                        )


                    isTtsReady =
                        languageResult !=
                            TextToSpeech.LANG_MISSING_DATA &&
                            languageResult !=
                            TextToSpeech.LANG_NOT_SUPPORTED


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
                            "한국어 TTS를 사용할 수 없음"
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
    // 현재 화면 읽어주기
    // =========================================================

    private fun startReadingCurrentScreen() {

        if (
            currentScreenLines.isEmpty()
        ) {

            showMessagePanel(

                panelTitle =
                    "읽어줘",

                message =
                    "현재 화면에서 읽을 수 있는 정보가 없습니다."
            )

            return
        }


        if (
            !isTtsReady
        ) {

            showMessagePanel(

                panelTitle =
                    "읽어줘",

                message =
                    "음성 읽기 기능을 준비하지 못했습니다.\n\n" +
                        "휴대폰의 TTS 설정을 확인해주세요."
            )

            return
        }


        val screenText =
            currentScreenLines
                .joinToString(
                    separator =
                        ".\n"
                )


        showTtsControlPanel( 
            textToRead = screenText,
            statusMessage = "현재 화면의 내용을 읽고 있습니다."
        )   


        speakText(
            screenText
        )
    }


    // =========================================================
    // 실제 TTS 출력
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
            .forEachIndexed { index, chunk ->

                val queueMode =

                    if (
                        index == 0
                    ) {

                        TextToSpeech.QUEUE_FLUSH

                    } else {

                        TextToSpeech.QUEUE_ADD
                    }


                textToSpeech
                    ?.speak(

                        chunk,

                        queueMode,

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


            val newlineIndex =
                candidate.lastIndexOf(
                    '\n'
                )


            val periodIndex =
                candidate.lastIndexOf(
                    ". "
                )


            val spaceIndex =
                candidate.lastIndexOf(
                    ' '
                )


            var cutIndex =
                maxOf(
                    newlineIndex,
                    periodIndex,
                    spaceIndex
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
// TTS 제어 Overlay
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

                    backgroundColor =
                        Color.rgb(
                            12,
                            12,
                            12
                        ),

                    borderColor =
                        Color.rgb(
                            75,
                            155,
                            255
                        ),

                    radiusDp =
                        24
                )
        }


    // =====================================================
    // 제목
    // =====================================================

    val titleView =
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
        titleView
    )


    // =====================================================
    // 상태
    // =====================================================

    val statusView =
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


            setPadding(
                0,
                dp(10),
                0,
                dp(25)
            )
        }


    panel.addView(

        statusView,

        LinearLayout.LayoutParams(

            LinearLayout.LayoutParams.MATCH_PARENT,

            0,

            1f
        )
    )


    // =====================================================
    // 처음부터 다시 읽기
    // =====================================================

    val replayButton =
        Button(this).apply {

            text =
                "처음부터 다시 읽기"

            textSize =
                18f


            setOnClickListener {

                statusView.text =
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


    // =====================================================
    // 읽기 중지
    // =====================================================

    val stopButton =
        Button(this).apply {

            text =
                "읽기 중지"

            textSize =
                18f


            setOnClickListener {

                textToSpeech
                    ?.stop()


                statusView.text =
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


    // =====================================================
    // HALO 메뉴
    // =====================================================

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

            LinearLayout.LayoutParams.MATCH_PARENT,

            dp(58)
        )
    )


    // =====================================================
    // Overlay
    // =====================================================

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

    } catch (
        e: Exception
    ) {

        Log.e(
            TAG,
            "TTS 패널 표시 실패",
            e
        )
    }
}


    // =========================================================
    // 안내 / 오류 패널
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

                        backgroundColor =
                            Color.rgb(
                                18,
                                18,
                                18
                            ),

                        borderColor =
                            Color.rgb(
                                75,
                                155,
                                255
                            ),

                        radiusDp =
                            24
                    )
            }


        val titleView =
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
            titleView
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

        } catch (
            e: Exception
        ) {

            Log.e(
                TAG,
                "HALO 안내 패널 표시 실패",
                e
            )
        }
    }


    // =========================================================
    // 메뉴 제거
    // =========================================================

    private fun removeActionMenu() {

        actionMenu?.let { panel ->

            try {

                windowManager.removeView(
                    panel
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

        resultPanel?.let { panel ->

            try {

                windowManager.removeView(
                    panel
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
                    dp(radiusDp)
                        .toFloat()


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