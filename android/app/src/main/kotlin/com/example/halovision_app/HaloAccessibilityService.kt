package com.example.halovision_app

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
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
import kotlin.math.abs

class HaloAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "HALO_ACCESS"

        // 보기 편하게 글자 크기
        private const val DEFAULT_TEXT_SIZE_SP = 32f
        private const val MIN_TEXT_SIZE_SP = 30f
        private const val MAX_TEXT_SIZE_SP = 72f
        private const val TEXT_SIZE_STEP_SP = 4f
    }

    private lateinit var windowManager: WindowManager

    // 화면 가장자리 HALO 버튼
    private var floatingButton: TextView? = null

    // HALO 기능 메뉴
    private var actionMenu: LinearLayout? = null

    // 기능 결과 패널
    private var resultPanel: LinearLayout? = null

    // HALO를 누른 순간의 현재 화면 텍스트
    private var currentScreenLines: List<String> = emptyList()

    // 현재 보기 편하게 글자 크기
    private var readableTextSizeSp: Float = DEFAULT_TEXT_SIZE_SP


    // =========================================================
    // AccessibilityService 시작
    // =========================================================

    override fun onServiceConnected() {
        super.onServiceConnected()

        Log.d(TAG, "Halo AccessibilityService 연결됨")

        windowManager =
            getSystemService(WINDOW_SERVICE) as WindowManager

        showFloatingHaloButton()
    }


    // =========================================================
    // 화면이 바뀔 때 자동 분석하지 않음
    //
    // HALO 버튼을 눌렀을 때만 현재 화면을 읽음
    // =========================================================

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 의도적으로 비워둠
    }


    override fun onInterrupt() {
        Log.d(TAG, "Halo AccessibilityService 중단됨")
    }


    override fun onDestroy() {

        removeActionMenu()
        removeResultPanel()

        floatingButton?.let { button ->

            try {
                windowManager.removeView(button)
            } catch (_: Exception) {
            }
        }

        floatingButton = null

        super.onDestroy()
    }


    // =========================================================
    // HALO 플로팅 버튼
    // =========================================================

    private fun showFloatingHaloButton() {

        if (floatingButton != null) {
            return
        }

        val button = TextView(this).apply {

            text = "HALO"

            textSize = 13f

            setTextColor(Color.WHITE)

            gravity = Gravity.CENTER

            background = GradientDrawable().apply {

                shape = GradientDrawable.OVAL

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


        val params = WindowManager.LayoutParams(

            dp(64),
            dp(64),

            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,

            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,

            PixelFormat.TRANSLUCENT
        )


        params.gravity =
            Gravity.TOP or Gravity.END

        params.x = dp(16)

        params.y = dp(250)


        // =====================================================
        // 플로팅 버튼 이동 + 클릭
        // =====================================================

        var initialX = 0
        var initialY = 0

        var initialTouchX = 0f
        var initialTouchY = 0f

        var moved = false


        button.setOnTouchListener { _, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    initialX = params.x
                    initialY = params.y

                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    moved = false

                    true
                }


                MotionEvent.ACTION_MOVE -> {

                    val dx =
                        (event.rawX - initialTouchX).toInt()

                    val dy =
                        (event.rawY - initialTouchY).toInt()


                    if (
                        abs(dx) > 10 ||
                        abs(dy) > 10
                    ) {
                        moved = true
                    }


                    // Gravity.END 기준이므로 X 방향 반대
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

            floatingButton = button

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

        Log.d(
            TAG,
            "HALO 버튼 클릭"
        )

        removeActionMenu()
        removeResultPanel()

        // 메뉴가 뜨기 전에 원래 앱 화면을 확보
        currentScreenLines =
            captureCurrentScreen()


        Log.d(
            TAG,
            "현재 화면 텍스트 ${currentScreenLines.size}개 확보"
        )


        currentScreenLines.forEach { line ->

            Log.d(
                TAG,
                line
            )
        }


        showActionMenu()
    }


    // =========================================================
    // 현재 화면 Node Tree 읽기
    // =========================================================

    private fun captureCurrentScreen(): List<String> {

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
    // Accessibility Node Tree 재귀 탐색
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


        if (!nodeText.isNullOrEmpty()) {

            result.add(
                nodeText
            )
        }


        val nodeDescription =
            node.contentDescription
                ?.toString()
                ?.trim()


        if (!nodeDescription.isNullOrEmpty()) {

            result.add(
                nodeDescription
            )
        }


        for (
            index in 0 until node.childCount
        ) {

            collectTexts(
                node = node.getChild(index),
                result = result
            )
        }
    }


    // =========================================================
    // HALO 기능 선택 메뉴
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
                        radiusDp = 24
                    )
            }


        // =====================================================
        // 제목
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
        // 안내
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


        val contextStatus =
            TextView(this).apply {

                text =
                    if (currentScreenLines.isEmpty()) {

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
                title = "🔍  보기 편하게",
                subtitle = "큰 글자와 확대 기능으로 확인"
            )


        readableButton.setOnClickListener {

            removeActionMenu()

            if (currentScreenLines.isEmpty()) {

                showMessagePanel(
                    panelTitle = "보기 편하게",
                    message =
                        "현재 화면에서 읽을 수 있는 텍스트가 없습니다."
                )

                return@setOnClickListener
            }


            showReadablePanel(
                currentScreenLines.joinToString(
                    separator = "\n\n"
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
                title = "★  중요한 내용만",
                subtitle = "필요한 핵심 정보만 간단하게"
            )


        importantButton.setOnClickListener {

            removeActionMenu()

            showMessagePanel(
                panelTitle = "중요한 내용만",
                message =
                    "현재 화면 정보는 정상적으로 확보했습니다.\n\n" +
                    "다음 단계에서 FastAPI와 연결하여 " +
                    "AI가 핵심 정보만 추출하도록 구현합니다."
            )
        }


        panel.addView(
            importantButton
        )


        // =====================================================
        // 읽어줘
        // =====================================================

        val readButton =
            createActionButton(
                title = "🔊  읽어줘",
                subtitle = "현재 화면 내용을 음성으로 확인"
            )


        readButton.setOnClickListener {

            removeActionMenu()

            showMessagePanel(
                panelTitle = "읽어줘",
                message =
                    "현재 화면 정보는 확보되어 있습니다.\n\n" +
                    "다음 단계에서 TTS를 연결해 " +
                    "음성으로 읽도록 구현합니다."
            )
        }


        panel.addView(
            readButton
        )


        // =====================================================
        // 질문하기
        // =====================================================

        val questionButton =
            createActionButton(
                title = "💬  질문하기",
                subtitle = "현재 화면에 대해 이어서 질문"
            )


        questionButton.setOnClickListener {

            removeActionMenu()

            showMessagePanel(
                panelTitle = "질문하기",
                message =
                    "현재 화면 Context는 저장되어 있습니다.\n\n" +
                    "이후 ScreenSession 기반 연속 질문 기능을 연결합니다."
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

                setTextColor(
                    Color.BLACK
                )

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
    // 기능 버튼 생성
    // =========================================================

    private fun createActionButton(
        title: String,
        subtitle: String
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
                        radiusDp = 16
                    )

                isClickable =
                    true

                isFocusable =
                    true
            }


        val titleView =
            TextView(this).apply {

                text =
                    title

                textSize =
                    18f

                setTextColor(
                    Color.WHITE
                )
            }


        val subtitleView =
            TextView(this).apply {

                text =
                    subtitle

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
    // 보기 편하게
    //
    // 기본 32sp
    // 최소 30sp
    // 최대 72sp
    // A-/A+
    // 핀치 확대/축소
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
                        radiusDp = 24
                    )
            }


        // =====================================================
        // 상단 제목 + 현재 글자 크기
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
                        radiusDp = 12
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
        // A- / A+ 버튼
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
        // 텍스트 영역
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
        // 글자 크기 변경 함수
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


            Log.d(
                TAG,
                "보기 편하게 글자 크기: $readableTextSizeSp sp"
            )
        }


        // =====================================================
        // A- 버튼
        // =====================================================

        decreaseButton.setOnClickListener {

            updateTextSize(
                readableTextSizeSp -
                    TEXT_SIZE_STEP_SP
            )
        }


        // =====================================================
        // A+ 버튼
        // =====================================================

        increaseButton.setOnClickListener {

            updateTextSize(
                readableTextSizeSp +
                    TEXT_SIZE_STEP_SP
            )
        }


        // =====================================================
        // 핀치 확대 / 축소
        // =====================================================

        val scaleGestureDetector =
            ScaleGestureDetector(
                this,
                object :
                    ScaleGestureDetector.SimpleOnScaleGestureListener() {

                    override fun onScale(
                        detector: ScaleGestureDetector
                    ): Boolean {

                        val scaleFactor =
                            detector.scaleFactor


                        val newSize =
                            readableTextSizeSp *
                                scaleFactor


                        updateTextSize(
                            newSize
                        )

                        return true
                    }
                }
            )


        scrollView.setOnTouchListener { _, event ->

            scaleGestureDetector.onTouchEvent(
                event
            )

            // 두 손가락일 때만 확대 제스처가 터치를 사용
            // 한 손가락일 때는 ScrollView 스크롤 유지
            event.pointerCount >= 2 ||
                scaleGestureDetector.isInProgress
        }


        // =====================================================
        // 메뉴로 돌아가기
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
                "보기 편하게 패널 표시 실패",
                e
            )
        }
    }


    // =========================================================
    // 안내용 패널
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
                        radiusDp = 24
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
    // 둥근 배경 생성
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
                dp(radiusDp).toFloat()

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
                resources.displayMetrics.density
            ).toInt()
    }
}