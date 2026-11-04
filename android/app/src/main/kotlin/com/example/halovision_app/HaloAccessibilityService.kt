package com.example.halovision_app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.MagnificationConfig
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Region
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowInsets
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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
        private const val MAGNIFIER_DISMISS_DELAY_MS = 300L
        private const val TARGET_HIGHLIGHT_DURATION_MS = 12000L
        private const val TARGET_SCROLL_MAX_ATTEMPTS = 3
        private const val TARGET_SCROLL_WAIT_MS = 650L

        // OCR 전처리: 최대 2배 확대하되 큰 화면에서 메모리 과사용 방지
        private const val OCR_TARGET_SCALE = 2.0f
        private const val OCR_MAX_PIXELS = 9_000_000f
        private const val OCR_CONTRAST = 1.35f

        private const val SERVER_BASE_URL = "https://halovision.onrender.com"

        private const val VISION_PROFILE_PREFS = "halo_vision_profile"
        private const val KEY_PROFILE_TEXT_SIZE = "profile_text_size"
        private const val KEY_PROFILE_MAGNIFIER_SCALE = "profile_magnifier_scale"
        private const val KEY_AUTO_READ_QUESTION = "auto_read_question"
        private const val KEY_AUTO_READ_IMPORTANT = "auto_read_important"
        private const val KEY_TTS_SPEECH_RATE = "tts_speech_rate"

        private const val KEY_HALO_RUNNING = "halo_runtime_enabled"

        private var activeInstance: HaloAccessibilityService? = null

        fun isHaloRunningPreference(
            context: android.content.Context
        ): Boolean {
            return context
                .getSharedPreferences(
                    VISION_PROFILE_PREFS,
                    android.content.Context.MODE_PRIVATE
                )
                .getBoolean(
                    KEY_HALO_RUNNING,
                    true
                )
        }

        fun setHaloRunningFromApp(
            context: android.content.Context,
            enabled: Boolean
        ): Boolean {
            context
                .getSharedPreferences(
                    VISION_PROFILE_PREFS,
                    android.content.Context.MODE_PRIVATE
                )
                .edit()
                .putBoolean(
                    KEY_HALO_RUNNING,
                    enabled
                )
                .apply()

            val service =
                activeInstance

            if (
                service != null
            ) {
                service.mainHandler.post {
                    service.applyHaloRunningState(
                        enabled
                    )
                }
            }

            /*
             * 설정값 저장 자체는 성공.
             * 접근성 권한이 켜져 있다면 서비스가 연결된 뒤에도
             * 이 값을 읽어 같은 상태를 유지한다.
             */
            return true
        }
    }

    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var floatingButton: TextView? = null
    private var actionMenu: LinearLayout? = null
    private var resultPanel: LinearLayout? = null

    private data class HaloScreenNode(
        val id: Int,
        val text: String,
        val contentDescription: String,
        val className: String,
        val clickable: Boolean,
        val enabled: Boolean,
        val visibleToUser: Boolean,
        val bounds: Rect
    ) {
        fun displayLabel(): String {
            return when {
                text.isNotBlank() -> text
                contentDescription.isNotBlank() -> contentDescription
                else -> "화면 요소"
            }
        }
    }

    private var currentScreenLines: List<String> = emptyList()
    private var currentScreenNodes: List<HaloScreenNode> = emptyList()

    // 접근성 권한은 유지하되 HALO 기능 자체의 실행 여부를 제어한다.
    private var isHaloRuntimeEnabled: Boolean = true

    private var targetHighlightView: View? = null
    private var targetHighlightLabelView: TextView? = null

    private var localTextRecognizer: TextRecognizer? = null

    private var profileTextSizeSp = DEFAULT_TEXT_SIZE_SP
    private var profileMagnifierScale = DEFAULT_MAGNIFIER_SCALE
    private var autoReadQuestionAnswer = true
    private var autoReadImportantResult = false
    private var ttsSpeechRate = 0.9f
    private var readableTextSizeSp = DEFAULT_TEXT_SIZE_SP

    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningForQuestion = false

    // =========================================================
    // 성능 측정용 런타임 통계
    //
    // 앱 기능에는 영향을 주지 않고 Logcat에만 기록한다.
    // 서비스가 다시 시작되면 0부터 시작한다.
    // =========================================================

    private var metricReadableTotal = 0
    private var metricReadableOcr = 0
    private var metricReadableNodeTree = 0

    private var metricAiTotal = 0
    private var metricAiVision = 0
    private var metricAiNodeOnly = 0

    private var metricFailureTotal = 0

    private var metricReadableLatencyTotalMs = 0L
    private var metricAiLatencyTotalMs = 0L

    private var magnifierTrashView: TextView? = null
    private var isHaloWindowMagnifierActive = false
    private var isMagnifierOverTrash = false
    private var magnificationChangedListener:
        AccessibilityService.MagnificationController.OnMagnificationChangedListener? = null

    private val magnifierDismissRunnable = Runnable {
        if (isHaloWindowMagnifierActive && isMagnifierOverTrash) {
            stopWindowMagnifier()
        }
    }

    private val targetHighlightHideRunnable = Runnable {
        removeTargetHighlight()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        activeInstance =
            this

        Log.d(TAG, "Halo AccessibilityService 연결됨")

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        loadVisionProfile()

        isHaloRuntimeEnabled =
            isHaloRunningPreference(
                this
            )

        if (
            isHaloRuntimeEnabled
        ) {
            initializeTextToSpeech()
            initializeLocalTextRecognizer()
            initializeSpeechRecognizer()
            initializeMagnificationListener()
            showFloatingHaloButton()
        } else {
            Log.d(
                TAG,
                "HaloVision 기능 꺼짐 상태로 서비스 연결됨"
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 사용자가 HALO 버튼을 눌렀을 때만 화면을 읽는다.
    }

    override fun onInterrupt() {
        Log.d(TAG, "Halo AccessibilityService 중단됨")
    }

    override fun onDestroy() {
        if (
            activeInstance ===
            this
        ) {
            activeInstance =
                null
        }

        mainHandler.removeCallbacks(magnifierDismissRunnable)
        mainHandler.removeCallbacks(targetHighlightHideRunnable)
        removeTargetHighlight()
        hideMagnifierTrashZone()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            magnificationChangedListener?.let {
                try { magnificationController.removeListener(it) } catch (_: Exception) {}
            }
        }
        magnificationChangedListener = null

        removeActionMenu()
        removeResultPanel()

        removeFloatingHaloButton()

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isTtsReady = false

        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListeningForQuestion = false

        localTextRecognizer?.close()
        localTextRecognizer = null

        currentScreenLines = emptyList()
        currentScreenNodes = emptyList()

        super.onDestroy()
    }

    private fun loadVisionProfile() {
        val prefs = getSharedPreferences(VISION_PROFILE_PREFS, MODE_PRIVATE)

        profileTextSizeSp = prefs.getFloat(
            KEY_PROFILE_TEXT_SIZE,
            DEFAULT_TEXT_SIZE_SP
        ).coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)

        profileMagnifierScale = prefs.getFloat(
            KEY_PROFILE_MAGNIFIER_SCALE,
            DEFAULT_MAGNIFIER_SCALE
        ).coerceIn(1.5f, 4.0f)

        autoReadQuestionAnswer = prefs.getBoolean(KEY_AUTO_READ_QUESTION, true)
        autoReadImportantResult = prefs.getBoolean(KEY_AUTO_READ_IMPORTANT, false)
        ttsSpeechRate = prefs.getFloat(KEY_TTS_SPEECH_RATE, 0.9f).coerceIn(0.6f, 1.4f)
        readableTextSizeSp = profileTextSizeSp
    }

    private fun saveVisionProfile(
        textSizeSp: Float,
        magnifierScale: Float,
        readQuestionAutomatically: Boolean,
        readImportantAutomatically: Boolean
    ) {
        val safeTextSize = textSizeSp.coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)
        val safeScale = magnifierScale.coerceIn(1.5f, 4.0f)

        getSharedPreferences(VISION_PROFILE_PREFS, MODE_PRIVATE)
            .edit()
            .putFloat(KEY_PROFILE_TEXT_SIZE, safeTextSize)
            .putFloat(KEY_PROFILE_MAGNIFIER_SCALE, safeScale)
            .putBoolean(KEY_AUTO_READ_QUESTION, readQuestionAutomatically)
            .putBoolean(KEY_AUTO_READ_IMPORTANT, readImportantAutomatically)
            .apply()

        profileTextSizeSp = safeTextSize
        profileMagnifierScale = safeScale
        autoReadQuestionAnswer = readQuestionAutomatically
        autoReadImportantResult = readImportantAutomatically
        readableTextSizeSp = safeTextSize
    }

    private fun saveTtsSpeechRate(
        rate: Float
    ) {
        val safeRate =
            rate.coerceIn(
                0.6f,
                1.4f
            )

        getSharedPreferences(
            VISION_PROFILE_PREFS,
            MODE_PRIVATE
        )
            .edit()
            .putFloat(
                KEY_TTS_SPEECH_RATE,
                safeRate
            )
            .apply()

        ttsSpeechRate =
            safeRate

        if (
            isTtsReady
        ) {
            textToSpeech
                ?.setSpeechRate(
                    ttsSpeechRate
                )
        }
    }

    private fun formatSpeechRate(
        rate: Float
    ): String {
        return when {
            rate <= 0.75f -> "느리게"
            rate <= 1.0f -> "보통"
            rate <= 1.2f -> "빠르게"
            else -> "매우 빠르게"
        }
    }

    // =========================================================
    // 앱 메인 화면에서 HALO 기능 ON / OFF
    //
    // OFF:
    // - 접근성 권한 자체는 유지
    // - HALO 버튼 제거
    // - 열려 있는 HALO UI 제거
    // - TTS / 음성 인식 중지
    // - 현재 화면 문맥 폐기
    //
    // ON:
    // - 필요한 런타임 구성요소 확인
    // - HALO 버튼 즉시 다시 표시
    // =========================================================

    private fun applyHaloRunningState(
        enabled: Boolean
    ) {
        isHaloRuntimeEnabled =
            enabled

        getSharedPreferences(
            VISION_PROFILE_PREFS,
            MODE_PRIVATE
        )
            .edit()
            .putBoolean(
                KEY_HALO_RUNNING,
                enabled
            )
            .apply()

        if (
            enabled
        ) {
            loadVisionProfile()
            initializeTextToSpeech()
            initializeLocalTextRecognizer()
            initializeSpeechRecognizer()
            initializeMagnificationListener()

            showFloatingHaloButton()

            Log.d(
                TAG,
                "HALO_RUNTIME | enabled=true"
            )

            return
        }

        /*
         * 기능 OFF 상태에서는 사용자가 명시적으로 다시 켜기 전까지
         * 화면 분석 / OCR / Screenshot / GPT 요청으로 들어갈 UI가 없다.
         */
        textToSpeech
            ?.stop()

        if (
            isListeningForQuestion
        ) {
            speechRecognizer
                ?.cancel()

            isListeningForQuestion =
                false
        }

        mainHandler.removeCallbacks(
            magnifierDismissRunnable
        )

        mainHandler.removeCallbacks(
            targetHighlightHideRunnable
        )

        removeTargetHighlight()
        hideMagnifierTrashZone()

        if (
            isHaloWindowMagnifierActive
        ) {
            stopWindowMagnifier()
        }

        removeActionMenu()
        removeResultPanel()
        removeFloatingHaloButton()

        currentScreenLines =
            emptyList()

        currentScreenNodes =
            emptyList()

        Log.d(
            TAG,
            "HALO_RUNTIME | enabled=false"
        )
    }

    private fun removeFloatingHaloButton() {
        floatingButton
            ?.let { button ->
                try {
                    if (
                        ::windowManager.isInitialized
                    ) {
                        windowManager.removeView(
                            button
                        )
                    }
                } catch (
                    _: Exception
                ) {
                }
            }

        floatingButton =
            null
    }

    private fun showFloatingHaloButton() {
        if (
            !isHaloRuntimeEnabled
        ) {
            return
        }

        if (floatingButton != null) return

        val button = TextView(this).apply {
            text = "HALO"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(42, 87, 190))
                setStroke(dp(3), Color.rgb(110, 190, 255))
            }
        }

        val params = WindowManager.LayoutParams(
            dp(64),
            dp(64),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.END
        params.x = dp(16)
        params.y = dp(250)

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
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true

                    params.x = initialX - dx
                    params.y = initialY + dy

                    try {
                        windowManager.updateViewLayout(button, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "HALO 버튼 이동 오류", e)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) onHaloButtonClicked()
                    true
                }

                else -> false
            }
        }

        try {
            windowManager.addView(button, params)
            floatingButton = button
        } catch (e: Exception) {
            Log.e(TAG, "HALO 버튼 표시 실패", e)
        }
    }

    private fun onHaloButtonClicked() {
        if (
            !isHaloRuntimeEnabled
        ) {
            return
        }

        textToSpeech?.stop()
        removeTargetHighlight()

        if (isListeningForQuestion) {
            speechRecognizer?.cancel()
            isListeningForQuestion = false
        }

        removeActionMenu()
        removeResultPanel()

        refreshCurrentScreenContext(
            source = "halo_open"
        )

        showActionMenu()
    }

    // =========================================================
    // 현재 시점의 Node Tree를 다시 확보
    //
    // 카메라 미리보기나 동적 화면은 HALO를 연 뒤에도 계속 변할 수 있으므로
    // 기능을 실제로 실행하는 순간에도 다시 호출한다.
    // =========================================================

    private fun refreshCurrentScreenContext(
        source: String
    ) {
        currentScreenLines =
            captureCurrentScreen()

        Log.d(
            TAG,
            "SCREEN CONTEXT REFRESH / source=$source / nodes=${currentScreenLines.size}"
        )
    }

    private fun captureCurrentScreen(): List<String> {
        val lines =
            mutableListOf<String>()

        val nodes =
            mutableListOf<HaloScreenNode>()

        val nextId =
            intArrayOf(
                1
            )

        val roots =
            mutableListOf<AccessibilityNodeInfo>()

        rootInActiveWindow
            ?.let {
                roots.add(
                    it
                )
            }

        /*
         * 일부 Samsung Internet / WebView 조합에서는
         * rootInActiveWindow가 주소창/툴바 쪽 Window를 우선 반환하고
         * 실제 웹 본문이 다른 Application Window에 남아 있을 수 있다.
         *
         * 그래서 현재 application window의 root도 함께 확인한다.
         */
        try {
            windows
                .filter {
                    it.type ==
                        android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
                }
                .mapNotNull {
                    it.root
                }
                .forEach { root ->
                    val packageName =
                        root.packageName
                            ?.toString()
                            .orEmpty()

                    val isSystemUi =
                        packageName ==
                            "com.android.systemui"

                    if (
                        !isSystemUi &&
                        roots.none {
                            it == root
                        }
                    ) {
                        roots.add(
                            root
                        )
                    }
                }
        } catch (
            e: Exception
        ) {
            Log.d(
                TAG,
                "SCREEN WINDOWS 추가 수집 실패 / ${e.message}"
            )
        }

        for (
            root in roots
        ) {
            collectScreenData(
                node = root,
                lines = lines,
                nodes = nodes,
                nextId = nextId
            )
        }

        val uniqueNodes =
            nodes
                .distinctBy { node ->
                    "${node.text}|${node.contentDescription}|" +
                        "${node.bounds.left},${node.bounds.top}," +
                        "${node.bounds.right},${node.bounds.bottom}"
                }

        currentScreenNodes =
            uniqueNodes

        val cleanLines =
            lines
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotEmpty()
                }
                .distinct()

        Log.d(
            TAG,
            "SCREEN NODE SNAPSHOT / roots=${roots.size} / " +
                "text_lines=${cleanLines.size} / " +
                "positioned_nodes=${uniqueNodes.size}"
        )

        return cleanLines
    }

    private fun collectScreenData(
        node: AccessibilityNodeInfo?,
        lines: MutableList<String>,
        nodes: MutableList<HaloScreenNode>,
        nextId: IntArray
    ) {
        if (node == null) {
            return
        }

        val nodeText =
            node.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val description =
            node.contentDescription
                ?.toString()
                ?.trim()
                .orEmpty()

        if (nodeText.isNotBlank()) {
            lines.add(
                nodeText
            )
        }

        if (description.isNotBlank()) {
            lines.add(
                description
            )
        }

        if (
            nodeText.isNotBlank() ||
            description.isNotBlank()
        ) {
            val bounds =
                Rect()

            node.getBoundsInScreen(
                bounds
            )

            if (
                bounds.width() > 0 &&
                bounds.height() > 0
            ) {
                nodes.add(
                    HaloScreenNode(
                        id = nextId[0]++,
                        text = nodeText,
                        contentDescription = description,
                        className = node.className
                            ?.toString()
                            .orEmpty(),
                        clickable = node.isClickable,
                        enabled = node.isEnabled,
                        visibleToUser = node.isVisibleToUser,
                        bounds = Rect(bounds)
                    )
                )
            }
        }

        for (
            i in 0 until node.childCount
        ) {
            collectScreenData(
                node = node.getChild(i),
                lines = lines,
                nodes = nodes,
                nextId = nextId
            )
        }
    }

    // =========================================================
    // 질문 기반 UI 요소 찾기
    //
    // 서버 응답 형식은 바꾸지 않고,
    // 사용자의 질문 + AI 답변과 현재 Accessibility Node 라벨을
    // 로컬에서 비교해 대상 버튼/요소를 찾는다.
    //
    // 예:
    // "결제하기 버튼 어디야?" → text="결제하기"
    // "장바구니 찾아줘"       → contentDescription="장바구니"
    // =========================================================

    // =========================================================
    // UI 위치 찾기 의도가 명확한 질문인지 확인
    //
    // 일반 질문:
    //   "라인업이 뭐야?"            -> false
    //   "결제 금액이 얼마야?"       -> false
    //
    // 위치 찾기 질문:
    //   "라인업 위치 알려줘"        -> true
    //   "결제하기 버튼 찾아줘"      -> true
    //   "장바구니 버튼 어디야?"     -> true
    //   "어디를 눌러야 해?"         -> true
    // =========================================================

    private fun isUiTargetRequest(
        question: String
    ): Boolean {
        val compact =
            question
                .lowercase(
                    Locale.ROOT
                )
                .replace(
                    Regex("\\s+"),
                    ""
                )

        if (
            compact.isBlank()
        ) {
            return false
        }

        /*
         * 이 표현들은 사용자가 "화면에서 위치를 찾아 달라"고
         * 직접 요청한 경우로 본다.
         */
        val explicitLocationPhrases =
            listOf(
                "찾아줘",
                "찾아주세요",
                "위치알려줘",
                "위치알려주세요",
                "위치알려",
                "위치찾아줘",
                "위치찾아주세요",
                "위치찾아",
                "어디를눌러",
                "어디눌러",
                "어디를터치",
                "어디터치",
                "강조해줘",
                "강조해주세요",
                "표시해줘",
                "표시해주세요",
                "위치표시",
                "어디에있는지알려"
            )

        if (
            explicitLocationPhrases.any {
                compact.contains(
                    it
                )
            }
        ) {
            Log.d(
                TAG,
                "HALO_TARGET_INTENT | enabled=true | reason=explicit_phrase"
            )

            return true
        }

        /*
         * "어디야?", "어디 있어?"는 일상적인 질문에도 많이 쓰이므로
         * 버튼/메뉴/탭 등 UI 요소를 함께 언급했을 때만 허용한다.
         */
        val uiElementWords =
            listOf(
                "버튼",
                "메뉴",
                "탭",
                "아이콘",
                "항목",
                "링크",
                "선택",
                "입력창",
                "검색창",
                "체크박스",
                "스위치"
            )

        val wherePhrases =
            listOf(
                "어디야",
                "어디있어",
                "어디에있어",
                "어디에있나요",
                "어디에있지",
                "어디에있는"
            )

        val hasUiElementWord =
            uiElementWords.any {
                compact.contains(
                    it
                )
            }

        val hasWherePhrase =
            wherePhrases.any {
                compact.contains(
                    it
                )
            }

        val enabled =
            hasUiElementWord &&
                hasWherePhrase

        Log.d(
            TAG,
            "HALO_TARGET_INTENT | enabled=$enabled | " +
                "ui_word=$hasUiElementWord | where_phrase=$hasWherePhrase"
        )

        return enabled
    }

    // =========================================================
    // 위치 찾기 질문에서 실제 대상 단어 추출
    //
    // GPT 답변에 "메뉴", "화면", "버튼" 같은 일반 단어가 포함됐다는
    // 이유만으로 엉뚱한 UI 요소를 강조하지 않도록 사용한다.
    // =========================================================

    private fun extractUiTargetTerms(
        question: String
    ): List<String> {
        var working =
            question
                .lowercase(
                    Locale.ROOT
                )

        val commandPhrases =
            listOf(
                "찾아 주세요",
                "찾아주세요",
                "찾아 줘",
                "찾아줘",
                "위치 알려 주세요",
                "위치 알려주세요",
                "위치 알려 줘",
                "위치 알려줘",
                "위치 알려",
                "위치 찾아 주세요",
                "위치 찾아주세요",
                "위치 찾아 줘",
                "위치 찾아줘",
                "위치 찾아",
                "어디에 있나요",
                "어디에 있어",
                "어디 있어",
                "어디야",
                "어디에 있는지 알려줘",
                "어디에 있는지 알려 주세요",
                "어디를 눌러야 해",
                "어디를 눌러",
                "어디 눌러",
                "어디를 터치",
                "어디 터치",
                "강조해 주세요",
                "강조해주세요",
                "강조해 줘",
                "강조해줘",
                "표시해 주세요",
                "표시해주세요",
                "표시해 줘",
                "표시해줘"
            )

        for (
            phrase in commandPhrases
        ) {
            working =
                working.replace(
                    phrase,
                    " "
                )
        }

        val stopWords =
            setOf(
                "위치",
                "버튼",
                "항목",
                "화면",
                "어디",
                "알려",
                "알려줘",
                "알려주세요",
                "찾아",
                "찾아줘",
                "찾아주세요",
                "눌러",
                "눌러야",
                "터치",
                "강조",
                "표시",
                "해줘",
                "해주세요",
                "좀",
                "여기",
                "저기"
            )

        val terms =
            Regex("[가-힣a-z0-9]+")
                .findAll(
                    working
                )
                .map {
                    it.value.trim()
                }
                .filter {
                    it.length >= 2
                }
                .filter {
                    it !in stopWords
                }
                .distinct()
                .sortedByDescending {
                    it.length
                }
                .take(
                    5
                )
                .toList()

        Log.d(
            TAG,
            "HALO_TARGET_TERMS | ${terms.joinToString(",")}"
        )

        return terms
    }

    private fun normalizeTargetText(
        value: String
    ): String {
        return value
            .lowercase(
                Locale.ROOT
            )
            .replace(
                Regex("[^가-힣a-z0-9]"),
                ""
            )
    }

    private fun scoreTargetNode(
        node: HaloScreenNode,
        question: String,
        answer: String
    ): Int {
        if (
            !node.enabled ||
            !node.visibleToUser
        ) {
            return 0
        }

        val visibleScreen =
            windowManager
                .currentWindowMetrics
                .bounds

        if (
            !Rect.intersects(
                visibleScreen,
                node.bounds
            )
        ) {
            return 0
        }

        val targetTerms =
            extractUiTargetTerms(
                question
            )
                .map {
                    normalizeTargetText(
                        it
                    )
                }
                .filter {
                    it.length >= 2
                }

        val answerNormalized =
            normalizeTargetText(
                answer
            )

        val labels =
            listOf(
                node.text,
                node.contentDescription
            )
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        var bestScore =
            0

        for (
            rawLabel in labels
        ) {
            val label =
                normalizeTargetText(
                    rawLabel
                )

            if (
                label.length < 2
            ) {
                continue
            }

            var score =
                0

            var matchedQuestionTarget =
                false

            /*
             * 가장 중요한 규칙:
             * 질문에서 추출한 대상 단어와 실제 Node 라벨이 맞아야 한다.
             *
             * 예)
             * 질문: "축구 버튼 찾아줘"
             * targetTerms = ["축구"]
             *
             * label="축구"  -> 강한 매칭
             * label="메뉴"  -> 0점
             */
            for (
                term in targetTerms
            ) {
                if (
                    label == term
                ) {
                    score +=
                        260

                    matchedQuestionTarget =
                        true

                } else if (
                    label.contains(
                        term
                    ) ||
                    term.contains(
                        label
                    )
                ) {
                    score +=
                        190

                    matchedQuestionTarget =
                        true
                }
            }

            /*
             * 질문에 구체적인 대상 단어가 존재하는데 이 Node가
             * 그 단어와 전혀 관계없으면 GPT 답변만으로 후보가 될 수 없다.
             *
             * 이 규칙이 "축구"를 찾는데 상단 "메뉴"를 강조하는 문제를 막는다.
             */
            if (
                targetTerms.isNotEmpty() &&
                !matchedQuestionTarget
            ) {
                continue
            }

            /*
             * "어디를 눌러야 해?"처럼 질문에 구체적인 대상 명칭이 없을 때만
             * GPT 답변을 보조적으로 사용한다.
             */
            if (
                targetTerms.isEmpty()
            ) {
                val genericLabels =
                    setOf(
                        "메뉴",
                        "버튼",
                        "화면",
                        "위치",
                        "항목",
                        "아이콘"
                    )

                if (
                    label !in genericLabels &&
                    answerNormalized.contains(
                        label
                    )
                ) {
                    score +=
                        95
                }
            } else if (
                answerNormalized.contains(
                    label
                )
            ) {
                /*
                 * 이미 질문의 대상 단어와 일치한 후보끼리 비교할 때만
                 * GPT 답변 언급을 작은 보너스로 사용한다.
                 */
                score +=
                    25
            }

            if (
                node.clickable
            ) {
                score +=
                    20
            }

            val classLower =
                node.className
                    .lowercase(
                        Locale.ROOT
                    )

            if (
                classLower.contains(
                    "button"
                )
            ) {
                score +=
                    12
            }

            val screenBounds =
                windowManager
                    .currentWindowMetrics
                    .bounds

            val nodeArea =
                node.bounds.width()
                    .toLong() *
                    node.bounds.height()
                        .toLong()

            val screenArea =
                screenBounds.width()
                    .toLong() *
                    screenBounds.height()
                        .toLong()

            if (
                screenArea > 0L &&
                nodeArea <
                screenArea / 3L
            ) {
                score +=
                    5
            }

            bestScore =
                maxOf(
                    bestScore,
                    score
                )
        }

        return bestScore
    }

    private fun findTargetNodeForQuestion(
        question: String,
        answer: String
    ): HaloScreenNode? {
        if (
            currentScreenNodes.isEmpty()
        ) {
            return null
        }

        var bestNode:
            HaloScreenNode? =
            null

        var bestScore =
            0

        for (
            node in currentScreenNodes
        ) {
            val score =
                scoreTargetNode(
                    node = node,
                    question = question,
                    answer = answer
                )

            if (
                score > bestScore
            ) {
                bestScore =
                    score

                bestNode =
                    node
            }
        }

        /*
         * 약한 우연 일치는 무시.
         * 질문에 직접 라벨이 들어가면 보통 120점 이상,
         * AI 답변만으로 잡아도 clickable/button이면 약 85점 전후.
         */
        val accepted =
            bestScore >=
                100

        Log.d(
            TAG,
            if (
                accepted &&
                bestNode != null
            ) {
                "HALO_TARGET | matched=true | id=${bestNode.id} | " +
                    "label=${bestNode.displayLabel().take(40)} | " +
                    "score=$bestScore | clickable=${bestNode.clickable} | " +
                    "bounds=${bestNode.bounds.left},${bestNode.bounds.top}," +
                    "${bestNode.bounds.right},${bestNode.bounds.bottom}"
            } else {
                "HALO_TARGET | matched=false | best_score=$bestScore"
            }
        )

        return if (
            accepted
        ) {
            bestNode
        } else {
            null
        }
    }

    // =========================================================
    // 화면 밖 UI 요소 자동 탐색
    //
    // 위치 찾기 질문인데 현재 보이는 Node에서 대상을 찾지 못하면:
    // 1. 현재 앱의 scrollable node 탐색
    // 2. 아래 방향으로 최대 3회 스크롤
    // 3. 각 스크롤 후 Node Tree 재수집
    // 4. 대상이 보이면 즉시 중단 + 강조
    //
    // 실제 버튼 클릭은 절대 수행하지 않는다.
    // =========================================================

    private fun findScrollableNode(
        node: AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        if (
            node == null
        ) {
            return null
        }

        var bestNode:
            AccessibilityNodeInfo? =
            null

        var bestArea =
            0L

        fun visit(
            current: AccessibilityNodeInfo?
        ) {
            if (
                current == null
            ) {
                return
            }

            val canScrollForward =
                current.actionList.any { action ->
                    action.id ==
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                }

            if (
                current.isScrollable ||
                canScrollForward
            ) {
                val bounds =
                    Rect()

                current.getBoundsInScreen(
                    bounds
                )

                val area =
                    bounds.width()
                        .coerceAtLeast(
                            0
                        )
                        .toLong() *
                        bounds.height()
                            .coerceAtLeast(
                                0
                            )
                            .toLong()

                /*
                 * 작은 가로 탭/캐러셀보다 화면 대부분을 차지하는
                 * 본문 ScrollView / WebView / RecyclerView를 우선한다.
                 */
                if (
                    area >
                    bestArea
                ) {
                    bestArea =
                        area

                    bestNode =
                        current
                }
            }

            for (
                i in 0 until current.childCount
            ) {
                visit(
                    current.getChild(
                        i
                    )
                )
            }
        }

        visit(
            node
        )

        return bestNode
    }

    private fun performTargetSearchScroll(
        onComplete: (Boolean) -> Unit
    ) {
        val root =
            rootInActiveWindow

        if (
            root != null
        ) {
            val scrollable =
                findScrollableNode(
                    root
                )

            if (
                scrollable != null
            ) {
                val actionSuccess =
                    try {
                        scrollable.performAction(
                            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        )
                    } catch (
                        e: Exception
                    ) {
                        Log.e(
                            TAG,
                            "UI 대상 ACTION_SCROLL_FORWARD 실패",
                            e
                        )

                        false
                    }

                if (
                    actionSuccess
                ) {
                    Log.d(
                        TAG,
                        "HALO_TARGET_SCROLL | success=true | method=node_action"
                    )

                    onComplete(
                        true
                    )

                    return
                }
            }
        }

        /*
         * 앱이 ACTION_SCROLL_FORWARD를 노출하지 않는 경우
         * 실제 손가락으로 위로 미는 것과 같은 Accessibility Gesture를 실행한다.
         *
         * 콘텐츠는 아래쪽으로 이동하지 않고,
         * 페이지가 아래 내용을 보여주도록 화면을 위로 Swipe 한다.
         */
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.N
        ) {
            Log.d(
                TAG,
                "HALO_TARGET_SCROLL | success=false | reason=gesture_not_supported"
            )

            onComplete(
                false
            )

            return
        }

        val bounds =
            windowManager
                .currentWindowMetrics
                .bounds

        val centerX =
            bounds.centerX()
                .toFloat()

        val startY =
            (
                bounds.top +
                    bounds.height() *
                    0.78f
                )

        val endY =
            (
                bounds.top +
                    bounds.height() *
                    0.30f
                )

        val gesturePath =
            Path().apply {
                moveTo(
                    centerX,
                    startY
                )

                lineTo(
                    centerX,
                    endY
                )
            }

        val gesture =
            GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        gesturePath,
                        0L,
                        420L
                    )
                )
                .build()

        val dispatched =
            dispatchGesture(
                gesture,
                object :
                    GestureResultCallback() {

                    override fun onCompleted(
                        gestureDescription: GestureDescription?
                    ) {
                        super.onCompleted(
                            gestureDescription
                        )

                        Log.d(
                            TAG,
                            "HALO_TARGET_SCROLL | success=true | method=gesture_swipe"
                        )

                        onComplete(
                            true
                        )
                    }

                    override fun onCancelled(
                        gestureDescription: GestureDescription?
                    ) {
                        super.onCancelled(
                            gestureDescription
                        )

                        Log.d(
                            TAG,
                            "HALO_TARGET_SCROLL | success=false | method=gesture_swipe"
                        )

                        onComplete(
                            false
                        )
                    }
                },
                mainHandler
            )

        if (
            !dispatched
        ) {
            Log.d(
                TAG,
                "HALO_TARGET_SCROLL | success=false | reason=dispatch_rejected"
            )

            onComplete(
                false
            )
        }
    }

    private fun findTargetWithAutoScroll(
        question: String,
        answer: String,
        attempt: Int = 0,
        onFound: (HaloScreenNode) -> Unit,
        onNotFound: () -> Unit
    ) {
        /*
         * Overlay가 Node Tree 수집을 방해하지 않도록
         * 탐색 중에는 결과 패널을 제거한다.
         */
        removeResultPanel()
        removeTargetHighlight()

        refreshCurrentScreenContext(
            source = "target_search_$attempt"
        )

        val target =
            findTargetNodeForQuestion(
                question = question,
                answer = answer
            )

        if (
            target != null
        ) {
            Log.d(
                TAG,
                "HALO_TARGET_SEARCH | found=true | attempt=$attempt"
            )

            onFound(
                target
            )

            return
        }

        if (
            attempt >=
            TARGET_SCROLL_MAX_ATTEMPTS
        ) {
            Log.d(
                TAG,
                "HALO_TARGET_SEARCH | found=false | reason=max_attempts"
            )

            onNotFound()

            return
        }

        showMessagePanel(
            "버튼 위치 찾기",
            "현재 화면에서 찾지 못해 아래쪽 화면도 확인하고 있습니다...\n\n" +
                "${attempt + 1}/${TARGET_SCROLL_MAX_ATTEMPTS}회 탐색 중"
        )

        /*
         * 방금 띄운 안내 Overlay를 제거한 뒤 실제 앱을 스크롤한다.
         */
        mainHandler.postDelayed(
            {
                removeResultPanel()

                performTargetSearchScroll { scrolled ->
                    if (
                        !scrolled
                    ) {
                        Log.d(
                            TAG,
                            "HALO_TARGET_SEARCH | found=false | reason=cannot_scroll"
                        )

                        onNotFound()

                        return@performTargetSearchScroll
                    }

                    /*
                     * 실제 스크롤/스와이프가 끝난 뒤 새 화면이 그려질 시간을 주고
                     * 다시 Node Tree를 수집한다.
                     */
                    mainHandler.postDelayed(
                        {
                            findTargetWithAutoScroll(
                                question = question,
                                answer = answer,
                                attempt = attempt + 1,
                                onFound = onFound,
                                onNotFound = onNotFound
                            )
                        },
                        TARGET_SCROLL_WAIT_MS
                    )
                }
            },
            120L
        )
    }

    // =========================================================
    // Accessibility screen 좌표 → Overlay 좌표 보정값
    //
    // 일부 Samsung / Android 기기에서는 getBoundsInScreen()과
    // TYPE_ACCESSIBILITY_OVERLAY의 좌표 원점 사이에 상태바 inset 차이가
    // 생겨 강조 박스가 실제 요소보다 아래로 표시될 수 있다.
    // =========================================================

    private fun getOverlayOriginOffset(): Pair<Int, Int> {
        /*
         * AccessibilityNode의 bounds는 실제 화면 좌표이고,
         * TYPE_ACCESSIBILITY_OVERLAY의 y=0이 어느 위치에서 시작하는지는
         * Samsung 기기/One UI 버전에 따라 차이가 생길 수 있다.
         *
         * 이미 화면에 떠 있는 HALO 버튼의 실제 screen 좌표와
         * WindowManager.LayoutParams.y를 비교하면 이 기기의
         * Overlay 좌표 원점을 런타임에 직접 측정할 수 있다.
         */
        val button =
            floatingButton
                ?: return Pair(
                    0,
                    0
                )

        return try {
            val location =
                IntArray(
                    2
                )

            button.getLocationOnScreen(
                location
            )

            val layoutParams =
                button.layoutParams as?
                    WindowManager.LayoutParams

            if (
                layoutParams == null
            ) {
                Pair(
                    0,
                    0
                )
            } else {
                val measuredY =
                    location[1] -
                        layoutParams.y

                /*
                 * TOP|END gravity의 x는 오른쪽 기준이라 x 보정값 계산에는
                 * 적합하지 않다. 일반 스마트폰의 좌/우 system inset은
                 * portrait에서 대부분 0이므로 x는 0으로 유지한다.
                 */
                val safeY =
                    if (
                        kotlin.math.abs(
                            measuredY
                        ) <=
                        dp(
                            80
                        )
                    ) {
                        measuredY
                    } else {
                        0
                    }

                Log.d(
                    TAG,
                    "HALO_TARGET_OFFSET_CALIBRATION | " +
                        "button_screen_y=${location[1]} | " +
                        "layout_y=${layoutParams.y} | " +
                        "offset_y=$safeY"
                )

                Pair(
                    0,
                    safeY
                )
            }
        } catch (
            e: Exception
        ) {
            Log.e(
                TAG,
                "Overlay 좌표 원점 측정 실패",
                e
            )

            Pair(
                0,
                0
            )
        }
    }

    // =========================================================
    // 대상 UI 요소 시각적 강조
    //
    // 실제 다른 앱의 버튼을 변형하지 않고,
    // Accessibility Overlay로 색/테두리를 덧씌운다.
    // FLAG_NOT_TOUCHABLE이므로 실제 버튼은 사용자가 직접 누를 수 있다.
    // =========================================================

    private fun showTargetHighlight(
        target: HaloScreenNode
    ) {
        removeTargetHighlight()

        val screenBounds =
            windowManager
                .currentWindowMetrics
                .bounds

        val overlayOriginOffset =
            getOverlayOriginOffset()

        /*
         * target.bounds는 실제 화면 좌표.
         * 기기에서 직접 측정한 Overlay 좌표 원점 차이만큼 보정한다.
         */
        val overlayOffsetX =
            overlayOriginOffset.first

        val overlayOffsetY =
            overlayOriginOffset.second

        Log.d(
            TAG,
            "HALO_TARGET_OFFSET | x=$overlayOffsetX | y=$overlayOffsetY"
        )

        val padding =
            dp(12)

        val left =
            (
                target.bounds.left -
                    padding
                ).coerceAtLeast(
                screenBounds.left
            )

        val top =
            (
                target.bounds.top -
                    padding
                ).coerceAtLeast(
                screenBounds.top
            )

        val right =
            (
                target.bounds.right +
                    padding
                ).coerceAtMost(
                screenBounds.right
            )

        val bottom =
            (
                target.bounds.bottom +
                    padding
                ).coerceAtMost(
                screenBounds.bottom
            )

        val width =
            (right - left)
                .coerceAtLeast(
                    dp(48)
                )

        val height =
            (bottom - top)
                .coerceAtLeast(
                    dp(48)
                )

        val highlight =
            View(this).apply {
                background =
                    GradientDrawable().apply {
                        shape =
                            GradientDrawable.RECTANGLE

                        cornerRadius =
                            dp(16)
                                .toFloat()

                        setColor(
                            Color.argb(
                                70,
                                255,
                                215,
                                0
                            )
                        )

                        setStroke(
                            dp(6),
                            Color.rgb(
                                255,
                                215,
                                0
                            )
                        )
                    }
            }

        val highlightParams =
            WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity =
                    Gravity.TOP or
                        Gravity.START

                x =
                    (
                        left -
                            overlayOffsetX
                        ).coerceAtLeast(
                        0
                    )

                y =
                    (
                        top -
                            overlayOffsetY
                        ).coerceAtLeast(
                        0
                    )
            }

        val labelText =
            "▼ HALO가 찾은 위치\n${target.displayLabel().take(30)}"

        val label =
            TextView(this).apply {
                text =
                    labelText

                textSize =
                    18f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.BLACK
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
                            255,
                            215,
                            0
                        ),
                        Color.WHITE,
                        14
                    )
            }

        val labelWidth =
            minOf(
                dp(300),
                screenBounds.width() -
                    dp(24)
            )

        val estimatedLabelHeight =
            dp(72)

        val labelY =
            if (
                top >=
                estimatedLabelHeight +
                    dp(12)
            ) {
                top -
                    estimatedLabelHeight -
                    dp(6)
            } else {
                (
                    bottom +
                        dp(6)
                    ).coerceAtMost(
                    screenBounds.bottom -
                        estimatedLabelHeight -
                        dp(6)
                )
            }

        val labelX =
            (
                target.bounds.centerX() -
                    labelWidth / 2
                ).coerceIn(
                screenBounds.left +
                    dp(6),
                maxOf(
                    screenBounds.left +
                        dp(6),
                    screenBounds.right -
                        labelWidth -
                        dp(6)
                )
            )

        val labelParams =
            WindowManager.LayoutParams(
                labelWidth,
                estimatedLabelHeight,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity =
                    Gravity.TOP or
                        Gravity.START

                x =
                    (
                        labelX -
                            overlayOffsetX
                        ).coerceAtLeast(
                        0
                    )

                y =
                    (
                        labelY -
                            overlayOffsetY
                        ).coerceAtLeast(
                        0
                    )
            }

        try {
            windowManager.addView(
                highlight,
                highlightParams
            )

            targetHighlightView =
                highlight

            windowManager.addView(
                label,
                labelParams
            )

            targetHighlightLabelView =
                label

            mainHandler.removeCallbacks(
                targetHighlightHideRunnable
            )

            mainHandler.postDelayed(
                targetHighlightHideRunnable,
                TARGET_HIGHLIGHT_DURATION_MS
            )
        } catch (
            e: Exception
        ) {
            Log.e(
                TAG,
                "대상 UI 강조 표시 실패",
                e
            )

            removeTargetHighlight()
        }
    }

    private fun removeTargetHighlight() {
        mainHandler.removeCallbacks(
            targetHighlightHideRunnable
        )

        targetHighlightView
            ?.let { view ->
                try {
                    if (
                        ::windowManager.isInitialized
                    ) {
                        windowManager.removeView(
                            view
                        )
                    }
                } catch (
                    _: Exception
                ) {
                }
            }

        targetHighlightLabelView
            ?.let { view ->
                try {
                    if (
                        ::windowManager.isInitialized
                    ) {
                        windowManager.removeView(
                            view
                        )
                    }
                } catch (
                    _: Exception
                ) {
                }
            }

        targetHighlightView =
            null

        targetHighlightLabelView =
            null
    }

    // =========================================================
    // 대상 요소가 발견된 질문 결과
    //
    // 큰 중앙 패널 대신 화면 위/아래의 작은 패널을 띄워
    // 사용자가 강조된 실제 버튼을 직접 누를 수 있게 한다.
    // =========================================================

    private fun showTargetQuestionPanel(
        question: String,
        answer: String,
        target: HaloScreenNode
    ) {
        removeResultPanel()
        showTargetHighlight(
            target
        )

        val panel =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16),
                    dp(14),
                    dp(16),
                    dp(14)
                )

                background =
                    createRoundedBackground(
                        Color.rgb(
                            18,
                            18,
                            18
                        ),
                        Color.rgb(
                            255,
                            215,
                            0
                        ),
                        20
                    )
            }

        panel.addView(
            TextView(this).apply {
                text =
                    "🎯 ${target.displayLabel().take(24)} 위치를 표시했어요"

                textSize =
                    20f

                setTextColor(
                    Color.rgb(
                        255,
                        215,
                        0
                    )
                )
            }
        )

        panel.addView(
            TextView(this).apply {
                text =
                    answer

                textSize =
                    18f

                setTextColor(
                    Color.WHITE
                )

                maxLines =
                    3

                setPadding(
                    0,
                    dp(6),
                    0,
                    dp(8)
                )
            }
        )

        val controls =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
            }

        controls.addView(
            Button(this).apply {
                text =
                    "🔊 다시 듣기"

                textSize =
                    15f

                setOnClickListener {
                    speakText(
                        answer
                    )
                }
            },
            LinearLayout.LayoutParams(
                0,
                dp(52),
                1f
            ).apply {
                marginEnd =
                    dp(4)
            }
        )

        controls.addView(
            Button(this).apply {
                text =
                    "🎤 다시 질문"

                textSize =
                    15f

                setOnClickListener {
                    textToSpeech?.stop()
                    removeTargetHighlight()
                    removeResultPanel()
                    startQuestionListening()
                }
            },
            LinearLayout.LayoutParams(
                0,
                dp(52),
                1f
            ).apply {
                marginStart =
                    dp(4)
            }
        )

        panel.addView(
            controls
        )

        val screenBounds =
            windowManager
                .currentWindowMetrics
                .bounds

        val targetIsTopHalf =
            target.bounds.centerY() <
                screenBounds.centerY()

        val params =
            WindowManager.LayoutParams(
                minOf(
                    dp(380),
                    screenBounds.width() -
                        dp(24)
                ),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity =
                    if (
                        targetIsTopHalf
                    ) {
                        Gravity.BOTTOM or
                            Gravity.CENTER_HORIZONTAL
                    } else {
                        Gravity.TOP or
                            Gravity.CENTER_HORIZONTAL
                    }

                y =
                    dp(22)
            }

        try {
            applyProfileButtonTextSizes(
                panel
            )

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
                "대상 질문 패널 표시 실패",
                e
            )

            showQuestionAnswerPanel(
                question = question,
                answer = answer
            )
        }
    }

    private fun hideHaloOverlaysForCapture(): Triple<Int?, Int?, Int?> {
        val oldFloating = floatingButton?.visibility
        val oldResult = resultPanel?.visibility
        val oldMenu = actionMenu?.visibility

        floatingButton?.visibility = View.INVISIBLE
        resultPanel?.visibility = View.INVISIBLE
        actionMenu?.visibility = View.INVISIBLE

        return Triple(oldFloating, oldResult, oldMenu)
    }

    private fun restoreHaloOverlays(
        oldState: Triple<Int?, Int?, Int?>
    ) {
        floatingButton?.visibility = oldState.first ?: View.VISIBLE
        resultPanel?.visibility = oldState.second ?: View.VISIBLE
        actionMenu?.visibility = oldState.third ?: View.VISIBLE
    }

    private fun captureScreenshotBase64(
        onReady: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onError("화면 이미지 분석은 Android 11 이상에서 사용할 수 있습니다.")
            return
        }

        val oldState = hideHaloOverlaysForCapture()

        mainHandler.postDelayed({
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(
                        screenshot: AccessibilityService.ScreenshotResult
                    ) {
                        val hardwareBuffer = screenshot.hardwareBuffer

                        try {
                            val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                                hardwareBuffer,
                                screenshot.colorSpace
                            )

                            if (hardwareBitmap == null) {
                                restoreHaloOverlays(oldState)
                                onError("화면 이미지를 변환하지 못했습니다.")
                                return
                            }

                            val softwareBitmap = hardwareBitmap.copy(
                                Bitmap.Config.ARGB_8888,
                                false
                            )

                            if (softwareBitmap == null) {
                                restoreHaloOverlays(oldState)
                                onError("화면 이미지 처리를 완료하지 못했습니다.")
                                return
                            }

                            restoreHaloOverlays(oldState)

                            Thread {
                                try {
                                    val output = ByteArrayOutputStream()

                                    if (!softwareBitmap.compress(
                                            Bitmap.CompressFormat.JPEG,
                                            85,
                                            output
                                        )
                                    ) {
                                        output.close()
                                        softwareBitmap.recycle()
                                        mainHandler.post {
                                            onError("화면 이미지 압축에 실패했습니다.")
                                        }
                                        return@Thread
                                    }

                                    val bytes = output.toByteArray()
                                    output.close()

                                    val base64 = Base64.encodeToString(
                                        bytes,
                                        Base64.NO_WRAP
                                    )

                                    Log.d(TAG, "HALO SCREENSHOT READY / bytes=${bytes.size}")

                                    softwareBitmap.recycle()

                                    mainHandler.post {
                                        onReady(base64)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Screenshot Base64 변환 실패", e)
                                    softwareBitmap.recycle()
                                    mainHandler.post {
                                        onError("화면 이미지 처리 중 오류가 발생했습니다.")
                                    }
                                }
                            }.start()
                        } catch (e: Exception) {
                            restoreHaloOverlays(oldState)
                            Log.e(TAG, "Screenshot 변환 실패", e)
                            onError("화면 이미지를 가져오지 못했습니다.")
                        } finally {
                            hardwareBuffer.close()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        restoreHaloOverlays(oldState)
                        Log.e(TAG, "Screenshot 실패 / code=$errorCode")
                        onError("현재 화면을 캡처하지 못했습니다. 오류 코드: $errorCode")
                    }
                }
            )
        }, 150L)
    }

    private fun initializeLocalTextRecognizer() {
        if (localTextRecognizer != null) return

        try {
            localTextRecognizer = TextRecognition.getClient(
                KoreanTextRecognizerOptions.Builder().build()
            )
            Log.d(TAG, "ML Kit Korean OCR 초기화 완료")
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit OCR 초기화 실패", e)
        }
    }

    // =========================================================
    // OCR 결과 텍스트 정리
    // =========================================================

    private fun extractOcrText(
        result: com.google.mlkit.vision.text.Text
    ): String {
        return result.textBlocks
            .joinToString("\n\n") { block ->
                block.lines
                    .joinToString("\n") { line ->
                        line.text
                            .replace(Regex("\\s+"), " ")
                            .trim()
                    }
                    .trim()
            }
            .trim()
    }

    // =========================================================
    // OCR 품질 점수
    //
    // 단순히 글자 수만 많은 결과를 고르지 않고,
    // 실제 한글/영문/숫자 비율과 토큰/줄 수를 함께 본다.
    // =========================================================

    private fun calculateOcrQualityScore(
        text: String
    ): Float {
        val clean = text.trim()

        if (clean.isBlank()) {
            return 0f
        }

        val meaningfulCharacters =
            clean.count { char ->
                char.isLetterOrDigit()
            }

        val suspiciousCharacters =
            clean.count { char ->
                !char.isLetterOrDigit() &&
                    !char.isWhitespace() &&
                    char !in ".,:;!?%+-/()[]{}₩$€£@#&*'\"·•~_"
            }

        val tokens =
            Regex("[가-힣A-Za-z0-9]+")
                .findAll(clean)
                .map { it.value }
                .filter { it.isNotBlank() }
                .toList()

        val usefulLines =
            clean.lines()
                .map { it.trim() }
                .count { line ->
                    line.count { it.isLetterOrDigit() } >= 2
                }

        val uniqueTokenCount =
            tokens
                .map { it.lowercase(Locale.ROOT) }
                .toSet()
                .size

        return (
            meaningfulCharacters * 1.0f +
                uniqueTokenCount * 2.5f +
                usefulLines * 2.0f -
                suspiciousCharacters * 2.0f
            ).coerceAtLeast(0f)
    }

    // =========================================================
    // OCR 전처리
    //
    // 1. 최대 2배 확대
    // 2. Grayscale
    // 3. 대비 강화
    //
    // 큰 화면은 최대 픽셀 수를 제한해서 OOM 위험을 줄인다.
    // =========================================================

    private fun createEnhancedOcrBitmap(
        source: Bitmap
    ): Bitmap {
        val originalPixels =
            source.width.toFloat() *
                source.height.toFloat()

        val memorySafeScale =
            if (originalPixels <= 0f) {
                1f
            } else {
                kotlin.math.sqrt(
                    OCR_MAX_PIXELS /
                        originalPixels
                )
            }

        val actualScale =
            minOf(
                OCR_TARGET_SCALE,
                memorySafeScale
            ).coerceAtLeast(1f)

        val targetWidth =
            (source.width * actualScale)
                .toInt()
                .coerceAtLeast(1)

        val targetHeight =
            (source.height * actualScale)
                .toInt()
                .coerceAtLeast(1)

        Log.d(
            TAG,
            "OCR enhanced bitmap / scale=${"%.2f".format(Locale.US, actualScale)} " +
                "/ ${source.width}x${source.height} → ${targetWidth}x${targetHeight}"
        )

        val enhanced =
            Bitmap.createBitmap(
                targetWidth,
                targetHeight,
                Bitmap.Config.ARGB_8888
            )

        val grayscale =
            ColorMatrix().apply {
                setSaturation(0f)
            }

        val translate =
            (-0.5f * OCR_CONTRAST + 0.5f) *
                255f

        val contrast =
            ColorMatrix(
                floatArrayOf(
                    OCR_CONTRAST, 0f, 0f, 0f, translate,
                    0f, OCR_CONTRAST, 0f, 0f, translate,
                    0f, 0f, OCR_CONTRAST, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )

        grayscale.postConcat(
            contrast
        )

        val paint =
            Paint(
                Paint.ANTI_ALIAS_FLAG or
                    Paint.FILTER_BITMAP_FLAG
            ).apply {
                colorFilter =
                    ColorMatrixColorFilter(
                        grayscale
                    )
            }

        val canvas =
            Canvas(
                enhanced
            )

        canvas.drawBitmap(
            source,
            null,
            android.graphics.Rect(
                0,
                0,
                targetWidth,
                targetHeight
            ),
            paint
        )

        return enhanced
    }

    // =========================================================
    // 원본 OCR vs 전처리 OCR 결과 선택
    // =========================================================

    private fun chooseBestOcrPass(
        originalText: String,
        enhancedText: String
    ): String {
        val originalScore =
            calculateOcrQualityScore(
                originalText
            )

        val enhancedScore =
            calculateOcrQualityScore(
                enhancedText
            )

        Log.d(
            TAG,
            "OCR PASS SCORE / original=$originalScore / enhanced=$enhancedScore"
        )

        if (originalText.isBlank()) {
            return enhancedText
        }

        if (enhancedText.isBlank()) {
            return originalText
        }

        /*
         * 두 결과가 비슷하면 원본을 유지한다.
         * 전처리 결과가 확실히 좋아졌을 때만 교체해서
         * 대비 강화로 생기는 오인식 증가를 줄인다.
         */
        val enhancedClearlyBetter =
            enhancedScore >=
                originalScore * 1.08f &&
                enhancedScore >=
                originalScore + 6f

        return if (enhancedClearlyBetter) {
            Log.d(
                TAG,
                "OCR PASS SELECTED = ENHANCED"
            )

            enhancedText
        } else {
            Log.d(
                TAG,
                "OCR PASS SELECTED = ORIGINAL"
            )

            originalText
        }
    }

    // =========================================================
    // 현재 화면 Screenshot → 로컬 ML Kit OCR 2-Pass
    //
    // Pass 1: 원본 Screenshot
    // Pass 2: 최대 2배 확대 + Grayscale + 대비 강화
    //
    // 서버 전송 없음 / GPT 호출 없음 / 파일 저장 없음
    // =========================================================

    private fun recognizeHaloCameraPreviewTextLocally(
        recognizer: TextRecognizer,
        onReady: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        Log.d(
            TAG,
            "HALO CAMERA OCR | source=CAMERAX_PREVIEW"
        )

        HaloCameraActivity
            .captureCurrentPreviewBitmap { previewBitmap ->

                if (
                    previewBitmap == null ||
                    previewBitmap.width <= 0 ||
                    previewBitmap.height <= 0
                ) {
                    try {
                        previewBitmap
                            ?.recycle()
                    } catch (_: Exception) {
                    }

                    onError(
                        "카메라 화면을 가져오지 못했습니다."
                    )

                    return@captureCurrentPreviewBitmap
                }

                Log.d(
                    TAG,
                    "HALO CAMERA OCR | bitmap=${previewBitmap.width}x${previewBitmap.height}"
                )

                val originalImage =
                    InputImage.fromBitmap(
                        previewBitmap,
                        0
                    )

                recognizer
                    .process(
                        originalImage
                    )
                    .addOnSuccessListener { originalResult ->
                        val originalText =
                            extractOcrText(
                                originalResult
                            )

                        Thread {
                            try {
                                val enhancedBitmap =
                                    createEnhancedOcrBitmap(
                                        previewBitmap
                                    )

                                mainHandler.post {
                                    val enhancedImage =
                                        InputImage.fromBitmap(
                                            enhancedBitmap,
                                            0
                                        )

                                    recognizer
                                        .process(
                                            enhancedImage
                                        )
                                        .addOnSuccessListener { enhancedResult ->
                                            val enhancedText =
                                                extractOcrText(
                                                    enhancedResult
                                                )

                                            val selectedText =
                                                chooseBestOcrPass(
                                                    originalText,
                                                    enhancedText
                                                )
                                                    .trim()

                                            Log.d(
                                                TAG,
                                                "HALO CAMERA OCR SUCCESS | " +
                                                    "original_chars=${originalText.length} | " +
                                                    "enhanced_chars=${enhancedText.length} | " +
                                                    "selected_chars=${selectedText.length}"
                                            )

                                            try {
                                                enhancedBitmap.recycle()
                                            } catch (_: Exception) {
                                            }

                                            try {
                                                previewBitmap.recycle()
                                            } catch (_: Exception) {
                                            }

                                            if (
                                                selectedText.isBlank()
                                            ) {
                                                onError(
                                                    "카메라에서 읽을 수 있는 글자를 찾지 못했습니다."
                                                )
                                            } else {
                                                onReady(
                                                    selectedText
                                                )
                                            }
                                        }
                                        .addOnFailureListener { enhancedError ->
                                            Log.e(
                                                TAG,
                                                "HALO CAMERA OCR enhanced 실패 → original 사용",
                                                enhancedError
                                            )

                                            try {
                                                enhancedBitmap.recycle()
                                            } catch (_: Exception) {
                                            }

                                            try {
                                                previewBitmap.recycle()
                                            } catch (_: Exception) {
                                            }

                                            if (
                                                originalText.isBlank()
                                            ) {
                                                onError(
                                                    "카메라에서 읽을 수 있는 글자를 찾지 못했습니다."
                                                )
                                            } else {
                                                onReady(
                                                    originalText
                                                )
                                            }
                                        }
                                }
                            } catch (
                                e: Exception
                            ) {
                                Log.e(
                                    TAG,
                                    "HALO CAMERA OCR 전처리 실패",
                                    e
                                )

                                try {
                                    previewBitmap.recycle()
                                } catch (_: Exception) {
                                }

                                mainHandler.post {
                                    if (
                                        originalText.isBlank()
                                    ) {
                                        onError(
                                            "카메라에서 읽을 수 있는 글자를 찾지 못했습니다."
                                        )
                                    } else {
                                        onReady(
                                            originalText
                                        )
                                    }
                                }
                            }
                        }.start()
                    }
                    .addOnFailureListener { error ->
                        Log.e(
                            TAG,
                            "HALO CAMERA OCR original 실패",
                            error
                        )

                        try {
                            previewBitmap.recycle()
                        } catch (_: Exception) {
                        }

                        onError(
                            "카메라 화면의 글자를 인식하지 못했습니다."
                        )
                    }
            }
    }

    private fun recognizeCurrentScreenTextLocally(
        onReady: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.R
        ) {
            onError(
                "화면 글자 인식은 Android 11 이상에서 사용할 수 있습니다."
            )

            return
        }

        if (
            localTextRecognizer == null
        ) {
            initializeLocalTextRecognizer()
        }

        val recognizer =
            localTextRecognizer

        if (
            recognizer == null
        ) {
            onError(
                "글자 인식 기능을 준비하지 못했습니다."
            )

            return
        }

        /*
         * HaloVision 전용 카메라에서는 Accessibility Screenshot 대신
         * CameraX PreviewView의 실제 현재 프레임을 OCR한다.
         * XCover5처럼 Screenshot 합성 방식이 다른 기기에서도 안정적이다.
         */
        if (
            HaloCameraActivity.isActive()
        ) {
            recognizeHaloCameraPreviewTextLocally(
                recognizer = recognizer,
                onReady = onReady,
                onError = onError
            )

            return
        }

        val oldState =
            hideHaloOverlaysForCapture()

        mainHandler.postDelayed(
            {
                Log.d(
                    TAG,
                    "로컬 OCR용 Screenshot 촬영 시작"
                )

                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object :
                        AccessibilityService.TakeScreenshotCallback {

                        override fun onSuccess(
                            screenshot:
                                AccessibilityService.ScreenshotResult
                        ) {
                            val hardwareBuffer =
                                screenshot.hardwareBuffer

                            try {
                                val hardwareBitmap =
                                    Bitmap.wrapHardwareBuffer(
                                        hardwareBuffer,
                                        screenshot.colorSpace
                                    )

                                if (
                                    hardwareBitmap == null
                                ) {
                                    restoreHaloOverlays(
                                        oldState
                                    )

                                    onError(
                                        "현재 화면을 이미지로 가져오지 못했습니다."
                                    )

                                    return
                                }

                                val softwareBitmap =
                                    hardwareBitmap.copy(
                                        Bitmap.Config.ARGB_8888,
                                        false
                                    )

                                if (
                                    softwareBitmap == null
                                ) {
                                    restoreHaloOverlays(
                                        oldState
                                    )

                                    onError(
                                        "화면 이미지 변환에 실패했습니다."
                                    )

                                    return
                                }

                                restoreHaloOverlays(
                                    oldState
                                )

                                Log.d(
                                    TAG,
                                    "OCR SCREENSHOT BITMAP | " +
                                        "${softwareBitmap.width}x${softwareBitmap.height}"
                                )

                                Log.d(
                                    TAG,
                                    "ML Kit OCR PASS 1 시작 / ORIGINAL"
                                )

                                val originalImage =
                                    InputImage.fromBitmap(
                                        softwareBitmap,
                                        0
                                    )

                                recognizer
                                    .process(
                                        originalImage
                                    )
                                    .addOnSuccessListener { originalResult ->
                                        val originalText =
                                            extractOcrText(
                                                originalResult
                                            )

                                        Log.d(
                                            TAG,
                                            "OCR PASS 1 완료 / chars=${originalText.length}"
                                        )

                                        /*
                                         * 전처리는 큰 Bitmap 연산이므로
                                         * UI Thread를 막지 않게 별도 Thread에서 만든다.
                                         */
                                        Thread {
                                            try {
                                                val enhancedBitmap =
                                                    createEnhancedOcrBitmap(
                                                        softwareBitmap
                                                    )

                                                mainHandler.post {
                                                    Log.d(
                                                        TAG,
                                                        "ML Kit OCR PASS 2 시작 / ENHANCED"
                                                    )

                                                    val enhancedImage =
                                                        InputImage.fromBitmap(
                                                            enhancedBitmap,
                                                            0
                                                        )

                                                    recognizer
                                                        .process(
                                                            enhancedImage
                                                        )
                                                        .addOnSuccessListener { enhancedResult ->
                                                            val enhancedText =
                                                                extractOcrText(
                                                                    enhancedResult
                                                                )

                                                            val selectedText =
                                                                chooseBestOcrPass(
                                                                    originalText,
                                                                    enhancedText
                                                                ).trim()

                                                            Log.d(
                                                                TAG,
                                                                "======================================"
                                                            )
                                                            Log.d(
                                                                TAG,
                                                                "LOCAL OCR 2-PASS SUCCESS"
                                                            )
                                                            Log.d(
                                                                TAG,
                                                                "original chars = ${originalText.length}"
                                                            )
                                                            Log.d(
                                                                TAG,
                                                                "enhanced chars = ${enhancedText.length}"
                                                            )
                                                            Log.d(
                                                                TAG,
                                                                "selected chars = ${selectedText.length}"
                                                            )
                                                            Log.d(
                                                                TAG,
                                                                "======================================"
                                                            )

                                                            enhancedBitmap.recycle()
                                                            softwareBitmap.recycle()

                                                            onReady(
                                                                selectedText
                                                            )
                                                        }
                                                        .addOnFailureListener { enhancedError ->
                                                            Log.e(
                                                                TAG,
                                                                "ML Kit OCR PASS 2 실패 → 원본 결과 사용",
                                                                enhancedError
                                                            )

                                                            enhancedBitmap.recycle()
                                                            softwareBitmap.recycle()

                                                            onReady(
                                                                originalText
                                                            )
                                                        }
                                                }
                                            } catch (
                                                e: Exception
                                            ) {
                                                Log.e(
                                                    TAG,
                                                    "OCR 전처리 실패 → 원본 결과 사용",
                                                    e
                                                )

                                                softwareBitmap.recycle()

                                                mainHandler.post {
                                                    onReady(
                                                        originalText
                                                    )
                                                }
                                            }
                                        }.start()
                                    }
                                    .addOnFailureListener { originalError ->
                                        Log.e(
                                            TAG,
                                            "ML Kit OCR PASS 1 실패 → 전처리 OCR 시도",
                                            originalError
                                        )

                                        /*
                                         * 원본 OCR이 실패해도 전처리 이미지로 한 번 더 시도.
                                         */
                                        Thread {
                                            try {
                                                val enhancedBitmap =
                                                    createEnhancedOcrBitmap(
                                                        softwareBitmap
                                                    )

                                                mainHandler.post {
                                                    val enhancedImage =
                                                        InputImage.fromBitmap(
                                                            enhancedBitmap,
                                                            0
                                                        )

                                                    recognizer
                                                        .process(
                                                            enhancedImage
                                                        )
                                                        .addOnSuccessListener { enhancedResult ->
                                                            val enhancedText =
                                                                extractOcrText(
                                                                    enhancedResult
                                                                ).trim()

                                                            enhancedBitmap.recycle()
                                                            softwareBitmap.recycle()

                                                            Log.d(
                                                                TAG,
                                                                "LOCAL OCR ENHANCED FALLBACK SUCCESS / chars=${enhancedText.length}"
                                                            )

                                                            onReady(
                                                                enhancedText
                                                            )
                                                        }
                                                        .addOnFailureListener { enhancedError ->
                                                            enhancedBitmap.recycle()
                                                            softwareBitmap.recycle()

                                                            Log.e(
                                                                TAG,
                                                                "ML Kit OCR PASS 2도 실패",
                                                                enhancedError
                                                            )

                                                            onError(
                                                                "화면의 글자를 인식하지 못했습니다."
                                                            )
                                                        }
                                                }
                                            } catch (
                                                e: Exception
                                            ) {
                                                softwareBitmap.recycle()

                                                Log.e(
                                                    TAG,
                                                    "OCR 전처리 생성 실패",
                                                    e
                                                )

                                                mainHandler.post {
                                                    onError(
                                                        "화면의 글자를 인식하지 못했습니다."
                                                    )
                                                }
                                            }
                                        }.start()
                                    }
                            } catch (
                                e: Exception
                            ) {
                                restoreHaloOverlays(
                                    oldState
                                )

                                Log.e(
                                    TAG,
                                    "로컬 OCR Screenshot 처리 실패",
                                    e
                                )

                                onError(
                                    "화면 글자 인식 중 오류가 발생했습니다."
                                )
                            } finally {
                                hardwareBuffer.close()
                            }
                        }

                        override fun onFailure(
                            errorCode: Int
                        ) {
                            restoreHaloOverlays(
                                oldState
                            )

                            Log.e(
                                TAG,
                                "OCR Screenshot 실패 / code=$errorCode"
                            )

                            onError(
                                "현재 화면을 캡처하지 못했습니다. 오류 코드: $errorCode"
                            )
                        }
                    }
                )
            },
            150L
        )
    }

    // =========================================================
    // 실제 본문이 아니라 브라우저/갤러리/카메라의 조작 UI만 수집된 경우 감지
    // =========================================================

    private fun isLikelyUiChromeOnly(
        value: String
    ): Boolean {
        val clean =
            value
                .trim()

        if (
            clean.isBlank()
        ) {
            return true
        }

        val lines =
            clean
                .lines()
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotBlank()
                }

        val normalized =
            clean
                .lowercase(
                    Locale.ROOT
                )

        val tokenCount =
            Regex(
                "[가-힣A-Za-z0-9]+"
            )
                .findAll(
                    normalized
                )
                .count()

        val meaningfulChars =
            clean.count {
                it.isLetterOrDigit()
            }

        val chromeKeywords =
            listOf(
                "북마크에 추가",
                "안전한 연결",
                "주소창",
                "새로고침",
                "뒤로가기",
                "앞으로가기",
                "상위 메뉴로 이동",
                "더보기",
                "공유",
                "편집",
                "삭제",
                "텍스트 추출",
                "카메라 닫기",
                "셔터",
                "플래시",
                "카메라 전환",
                "갤러리"
            )

        val chromeHits =
            chromeKeywords.count {
                normalized.contains(
                    it
                )
            }

        val containsUrl =
            Regex(
                """(?:https?://)?(?:[a-z0-9-]+\.)+[a-z]{2,}(?:/[^\s]*)?"""
            )
                .containsMatchIn(
                    normalized
                )

        /*
         * 본문이 충분히 길면 UI chrome으로 보지 않는다.
         * 짧은 텍스트가 브라우저/카메라 조작 라벨과 URL 위주일 때만 차단한다.
         */
        val shortContext =
            lines.size <=
                8 &&
                meaningfulChars <=
                140 &&
                tokenCount <=
                24

        val likelyChrome =
            shortContext &&
                (
                    chromeHits >=
                        1 ||
                        containsUrl
                    )

        Log.d(
            TAG,
            "READABLE_UI_CHROME_CHECK | likely=$likelyChrome | " +
                "lines=${lines.size} | chars=$meaningfulChars | " +
                "tokens=$tokenCount | chrome_hits=$chromeHits | url=$containsUrl"
        )

        return likelyChrome
    }

    private fun chooseBestReadableText(
        nodeText: String,
        ocrText: String
    ): Pair<String, Boolean> {
        val node = nodeText.trim()
        val ocr = ocrText.trim()

        if (node.isBlank() && ocr.isBlank()) {
            return Pair("", false)
        }

        if (node.isBlank()) {
            return Pair(ocr, true)
        }

        val nodeLooksLikeUiChrome =
            isLikelyUiChromeOnly(
                node
            )

        /*
         * 갤러리/브라우저/카메라의 UI 라벨만 잡힌 상황에서
         * OCR 결과가 있으면 무조건 실제 이미지/화면 OCR을 우선한다.
         */
        if (
            nodeLooksLikeUiChrome &&
            ocr.isNotBlank()
        ) {
            Log.d(
                TAG,
                "Readable source = LOCAL OCR / reason=node_ui_chrome"
            )

            return Pair(
                ocr,
                true
            )
        }

        /*
         * OCR도 비었고 Node Tree도 UI 조작 라벨뿐이면
         * '편집', '북마크', URL 등을 본문처럼 보여주지 않는다.
         */
        if (
            ocr.isBlank()
        ) {
            return if (
                nodeLooksLikeUiChrome
            ) {
                Pair(
                    "",
                    false
                )
            } else {
                Pair(
                    node,
                    false
                )
            }
        }

        val tokenRegex = Regex("[가-힣A-Za-z0-9]+")

        val nodeTokens = tokenRegex.findAll(
            node.lowercase(Locale.ROOT)
        ).map { it.value }.toSet()

        val ocrTokens = tokenRegex.findAll(
            ocr.lowercase(Locale.ROOT)
        ).map { it.value }.toSet()

        if (ocrTokens.isEmpty()) return Pair(node, false)

        val extraTokens = ocrTokens - nodeTokens
        val extraRatio = extraTokens.size.toFloat() / ocrTokens.size.toFloat()

        val nodeChars = node.count { it.isLetterOrDigit() }
        val ocrChars = ocr.count { it.isLetterOrDigit() }

        val useOcr =
            (extraTokens.size >= 2 && extraRatio >= 0.25f) ||
                (ocrChars >= nodeChars + 20)

        Log.d(
            TAG,
            if (useOcr) {
                "Readable source = LOCAL OCR / extraRatio=$extraRatio"
            } else {
                "Readable source = NODE TREE / extraRatio=$extraRatio"
            }
        )

        return if (useOcr) {
            Pair(ocr, true)
        } else {
            Pair(node, false)
        }
    }

    private fun resolveReadableText(
        onReady: (text: String, usedOcr: Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        val nodeText = currentScreenLines.joinToString("\n")

        recognizeCurrentScreenTextLocally(
            onReady = { ocrText ->
                val selected = chooseBestReadableText(
                    nodeText,
                    ocrText
                )

                if (selected.first.isBlank()) {
                    onError("현재 화면에서 읽을 수 있는 글자를 찾지 못했습니다.")
                    return@recognizeCurrentScreenTextLocally
                }

                onReady(
                    selected.first,
                    selected.second
                )
            },

            onError = { message ->
                val nodeLooksLikeUiChrome =
                    isLikelyUiChromeOnly(
                        nodeText
                    )

                if (
                    nodeText.isNotBlank() &&
                    !nodeLooksLikeUiChrome
                ) {
                    Log.d(
                        TAG,
                        "OCR 실패 → 실제 본문으로 판단된 Node Tree fallback"
                    )

                    onReady(
                        nodeText,
                        false
                    )
                } else {
                    Log.d(
                        TAG,
                        "OCR 실패 + Node Tree UI chrome → 잘못된 fallback 차단"
                    )

                    onError(
                        message
                    )
                }
            }
        )
    }

    // =========================================================
    // 성능 측정 Log Helper
    //
    // 출력 예:
    // HALO_METRIC | feature=large_text | source=LOCAL_OCR
    //             | latency_ms=812 | nodes=23 | chars=312
    //
    // HALO_METRIC_SUMMARY | readable_total=...
    // =========================================================

    private fun recordReadableMetric(
        feature: String,
        usedOcr: Boolean,
        latencyMs: Long,
        textLength: Int
    ) {
        metricReadableTotal += 1

        if (usedOcr) {
            metricReadableOcr += 1
        } else {
            metricReadableNodeTree += 1
        }

        metricReadableLatencyTotalMs +=
            latencyMs

        val source =
            if (usedOcr) {
                "LOCAL_OCR"
            } else {
                "NODE_TREE"
            }

        Log.d(
            TAG,
            "HALO_METRIC | feature=$feature | source=$source | " +
                "latency_ms=$latencyMs | nodes=${currentScreenLines.size} | " +
                "chars=$textLength"
        )

        logMetricSummary()
    }

    private fun recordAiMetric(
        feature: String,
        usedVision: Boolean,
        latencyMs: Long,
        answerLength: Int
    ) {
        metricAiTotal += 1

        if (usedVision) {
            metricAiVision += 1
        } else {
            metricAiNodeOnly += 1
        }

        metricAiLatencyTotalMs +=
            latencyMs

        val source =
            if (usedVision) {
                "GPT_VISION"
            } else {
                "NODE_TREE_ONLY"
            }

        Log.d(
            TAG,
            "HALO_METRIC | feature=$feature | source=$source | " +
                "latency_ms=$latencyMs | nodes=${currentScreenLines.size} | " +
                "answer_chars=$answerLength"
        )

        logMetricSummary()
    }

    private fun recordMetricFailure(
        feature: String,
        latencyMs: Long,
        reason: String
    ) {
        metricFailureTotal += 1

        val safeReason =
            reason
                .replace("\n", " ")
                .take(120)

        Log.d(
            TAG,
            "HALO_METRIC | feature=$feature | success=false | " +
                "latency_ms=$latencyMs | reason=$safeReason"
        )

        logMetricSummary()
    }

    private fun logMetricSummary() {
        val readableAvg =
            if (metricReadableTotal > 0) {
                metricReadableLatencyTotalMs /
                    metricReadableTotal
            } else {
                0L
            }

        val aiAvg =
            if (metricAiTotal > 0) {
                metricAiLatencyTotalMs /
                    metricAiTotal
            } else {
                0L
            }

        val ocrRate =
            if (metricReadableTotal > 0) {
                metricReadableOcr *
                    100f /
                    metricReadableTotal
            } else {
                0f
            }

        val visionRate =
            if (metricAiTotal > 0) {
                metricAiVision *
                    100f /
                    metricAiTotal
            } else {
                0f
            }

        Log.d(
            TAG,
            "HALO_METRIC_SUMMARY | " +
                "readable_total=$metricReadableTotal | " +
                "node_tree=$metricReadableNodeTree | " +
                "local_ocr=$metricReadableOcr | " +
                "ocr_rate=${"%.1f".format(Locale.US, ocrRate)}% | " +
                "avg_readable_ms=$readableAvg | " +
                "ai_total=$metricAiTotal | " +
                "node_only=$metricAiNodeOnly | " +
                "vision=$metricAiVision | " +
                "vision_rate=${"%.1f".format(Locale.US, visionRate)}% | " +
                "avg_ai_ms=$aiAvg | " +
                "failures=$metricFailureTotal"
        )
    }

    // =========================================================
    // HaloVision 전용 카메라 Preview 열기
    // =========================================================

    private fun openHaloCameraPreview() {
        try {
            val intent = Intent(
                this,
                HaloCameraActivity::class.java
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            startActivity(intent)

            Log.d(
                TAG,
                "HALO_CAMERA | preview_opened=true"
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "HALO_CAMERA | preview_opened=false",
                e
            )

            showMessagePanel(
                "카메라",
                "카메라 화면을 열 수 없습니다."
            )
        }
    }

    // =========================================================
    // HALO 메뉴
    // =========================================================

    private fun showActionMenu() {
        removeActionMenu()
        removeResultPanel()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = createRoundedBackground(
                Color.rgb(18, 18, 18),
                Color.rgb(75, 155, 255),
                24
            )
        }

        val title = TextView(this).apply {
            text = "HALO"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(100, 190, 255))
            setPadding(0, dp(4), 0, dp(8))
        }
        panel.addView(title)

        val readableButton = createActionButton(
            "🔍  보기 편하게"
        )
        readableButton.setOnClickListener {
            removeActionMenu()
            showReadableOptionsMenu()
        }
        panel.addView(readableButton)

        val importantButton = createActionButton(
            "★  중요한 내용만"
        )
        importantButton.setOnClickListener {
            removeActionMenu()

            refreshCurrentScreenContext(
                source = "important_start"
            )

            requestImportantScreen()
        }
        panel.addView(importantButton)

        val readButton = createActionButton(
            "🔊  화면 읽어줘"
        )
        readButton.setOnClickListener {
            removeActionMenu()

            refreshCurrentScreenContext(
                source = "read_aloud_start"
            )

            startReadingCurrentScreen()
        }
        panel.addView(readButton)

        val questionButton = createActionButton(
            "💬  질문하기"
        )
        questionButton.setOnClickListener {
            removeActionMenu()
            startQuestionListening()
        }
        panel.addView(questionButton)

        val settingsCameraRow =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER
            }

        val profileButton =
            createActionButton(
                "⚙  설정"
            )

        profileButton.setOnClickListener {
            removeActionMenu()
            showVisionProfileTextSizeMenu()
        }

        val cameraButton =
            createActionButton(
                "📷  카메라"
            )

        cameraButton.setOnClickListener {
            removeActionMenu()
            openHaloCameraPreview()
        }

        settingsCameraRow.addView(
            profileButton,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd =
                    dp(5)
            }
        )

        settingsCameraRow.addView(
            cameraButton,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart =
                    dp(5)
            }
        )

        panel.addView(
            settingsCameraRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin =
                    dp(10)
            }
        )

        val closeButton = Button(this).apply {
            text = "닫기"
            textSize = 17f
            setOnClickListener {
                removeActionMenu()
            }
        }

        panel.addView(
            closeButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                topMargin = dp(14)
            }
        )

        val params = WindowManager.LayoutParams(
            adaptiveOverlayWidth(340),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        try {
            applyProfileButtonTextSizes(panel)
            windowManager.addView(panel, params)
            actionMenu = panel
        } catch (e: Exception) {
            Log.e(TAG, "HALO 메뉴 표시 실패", e)
        }
    }

    private fun createActionButton(
        titleText: String
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = createRoundedBackground(
                Color.rgb(38, 38, 38),
                Color.rgb(70, 70, 70),
                16
            )
            isClickable = true
            isFocusable = true

            addView(TextView(this@HaloAccessibilityService).apply {
                text = titleText
                setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    configuredButtonTextSizeSp()
                )
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 3
                setSingleLine(false)
            })

            minimumHeight = configuredButtonHeightPx()

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }
    }

    // =========================================================
    // Vision Profile UI
    // =========================================================

    private fun showVisionProfileTextSizeMenu() {
        removeResultPanel()
        removeActionMenu()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = createRoundedBackground(
                Color.rgb(18, 18, 18),
                Color.rgb(75, 155, 255),
                24
            )
        }

        val scrollView = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(content)

        content.addView(TextView(this).apply {
            text = "설정"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(100, 190, 255))
            setPadding(0, dp(4), 0, dp(18))
        })

        content.addView(TextView(this).apply {
            text = "기본 글자 크기"
            textSize = 21f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(6))
        })

        val currentTextSetting = TextView(this).apply {
            text = "현재 설정: ${profileTextSizeSp.toInt()}sp"
            textSize = 17f
            setTextColor(Color.rgb(100, 190, 255))
            setPadding(0, 0, 0, dp(12))
        }
        content.addView(currentTextSetting)

        fun selectTextSize(size: Float) {
            saveVisionProfile(
                size,
                profileMagnifierScale,
                autoReadQuestionAnswer,
                autoReadImportantResult
            )
            currentTextSetting.text = "현재 설정: ${size.toInt()}sp"

            applyProfileButtonTextSizes(
                panel
            )
        }

        listOf(32f, 40f, 48f, 56f).forEach { size ->
            val button = Button(this).apply {
                text = "${size.toInt()}sp"
                textSize = size
                tag = "KEEP_TEXT_SIZE"
                isAllCaps = false
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
                setOnClickListener {
                    selectTextSize(size)
                }
            }

            content.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = if (size == 56f) dp(28) else dp(8)
                }
            )
        }

        content.addView(
            createDivider(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                bottomMargin = dp(22)
            }
        )

        content.addView(TextView(this).apply {
            text = "돋보기 기본 배율"
            textSize = 21f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(6))
        })

        val currentMagnifierSetting = TextView(this).apply {
            text = "현재 설정: ${formatMagnifierScale(profileMagnifierScale)}"
            textSize = 17f
            setTextColor(Color.rgb(100, 190, 255))
            setPadding(0, 0, 0, dp(12))
        }
        content.addView(currentMagnifierSetting)

        fun selectScale(scale: Float) {
            saveVisionProfile(
                profileTextSizeSp,
                scale,
                autoReadQuestionAnswer,
                autoReadImportantResult
            )
            currentMagnifierSetting.text =
                "현재 설정: ${formatMagnifierScale(scale)}"
        }

        content.addView(
            createHorizontalButtonRow(
                "1.5배",
                "2배",
                { selectScale(1.5f) },
                { selectScale(2.0f) }
            )
        )

        content.addView(
            createHorizontalButtonRow(
                "2.5배",
                "3배",
                { selectScale(2.5f) },
                { selectScale(3.0f) }
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        )

        content.addView(
            createDivider(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                bottomMargin = dp(22)
            }
        )

        content.addView(TextView(this).apply {
            text = "음성 설정"
            textSize = 21f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(10))
        })

        content.addView(TextView(this).apply {
            text = "읽는 속도"
            textSize = 19f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(8))
        })

        val currentSpeechRateSetting =
            TextView(this).apply {
                text =
                    "현재: ${formatSpeechRate(ttsSpeechRate)}"
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
                    dp(10)
                )
            }

        content.addView(
            currentSpeechRateSetting
        )

        fun selectSpeechRate(
            rate: Float
        ) {
            saveTtsSpeechRate(
                rate
            )

            currentSpeechRateSetting.text =
                "현재: ${formatSpeechRate(rate)}"

            if (
                isTtsReady
            ) {
                textToSpeech
                    ?.stop()
            }
        }

        content.addView(
            createHorizontalButtonRow(
                "느리게",
                "보통",
                {
                    selectSpeechRate(
                        0.7f
                    )
                },
                {
                    selectSpeechRate(
                        0.9f
                    )
                }
            )
        )

        content.addView(
            createHorizontalButtonRow(
                "빠르게",
                "매우 빠르게",
                {
                    selectSpeechRate(
                        1.1f
                    )
                },
                {
                    selectSpeechRate(
                        1.3f
                    )
                }
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin =
                    dp(8)

                bottomMargin =
                    dp(18)
            }
        )

        val questionToggle = Button(this)
        fun updateQuestionToggle() {
            questionToggle.text = if (autoReadQuestionAnswer) {
                "🔊 질문 답변 자동 읽기 : ON"
            } else {
                "🔇 질문 답변 자동 읽기 : OFF"
            }
        }

        questionToggle.apply {
            textSize = 18f
            isAllCaps = false
            setOnClickListener {
                autoReadQuestionAnswer = !autoReadQuestionAnswer
                saveVisionProfile(
                    profileTextSizeSp,
                    profileMagnifierScale,
                    autoReadQuestionAnswer,
                    autoReadImportantResult
                )
                updateQuestionToggle()
            }
        }
        updateQuestionToggle()

        content.addView(
            questionToggle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(62)
            ).apply {
                bottomMargin = dp(10)
            }
        )

        val importantToggle = Button(this)
        fun updateImportantToggle() {
            importantToggle.text = if (autoReadImportantResult) {
                "🔊 중요 정보 자동 읽기 : ON"
            } else {
                "🔇 중요 정보 자동 읽기 : OFF"
            }
        }

        importantToggle.apply {
            textSize = 18f
            isAllCaps = false
            setOnClickListener {
                autoReadImportantResult = !autoReadImportantResult
                saveVisionProfile(
                    profileTextSizeSp,
                    profileMagnifierScale,
                    autoReadQuestionAnswer,
                    autoReadImportantResult
                )
                updateImportantToggle()
            }
        }
        updateImportantToggle()

        content.addView(
            importantToggle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(62)
            ).apply {
                bottomMargin = dp(12)
            }
        )

        panel.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        panel.addView(
            Button(this).apply {
                text = "HALO 메뉴로 돌아가기"
                textSize = 17f
                setOnClickListener {
                    removeResultPanel()
                    showActionMenu()
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            )
        )

        val params = WindowManager.LayoutParams(
            adaptiveOverlayWidth(370),
            adaptiveOverlayHeight(680),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        try {
            applyProfileButtonTextSizes(panel)
            windowManager.addView(panel, params)
            resultPanel = panel
        } catch (e: Exception) {
            Log.e(TAG, "Vision Profile 화면 표시 실패", e)
        }
    }

    private fun configuredButtonTextSizeSp(): Float {
        return (
            profileTextSizeSp *
                0.60f
            ).coerceIn(
            18f,
            34f
        )
    }

    private fun configuredButtonHeightPx(): Int {
        val heightDp =
            (
                configuredButtonTextSizeSp() *
                    2.25f
                )
                .toInt()
                .coerceAtLeast(
                    58
                )

        return dp(
            heightDp
        )
    }

    private fun applyProfileButtonTextSizes(
        root: View
    ) {
        if (
            root is Button
        ) {
            if (
                root.tag !=
                    "KEEP_TEXT_SIZE"
            ) {
                root.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    configuredButtonTextSizeSp()
                )
            }

            /*
             * 큰 글자 설정 시 버튼 문구가 2~3줄이 되더라도
             * 기존의 56dp / 58dp / 62dp 고정 높이에 잘리지 않도록
             * 실제 텍스트 높이에 맞춰 버튼 자체가 늘어나게 한다.
             */
            root.isAllCaps =
                false

            root.setSingleLine(
                false
            )

            root.maxLines =
                3

            root.ellipsize =
                null

            root.gravity =
                Gravity.CENTER

            root.minHeight =
                configuredButtonHeightPx()

            root.setPadding(
                maxOf(
                    root.paddingLeft,
                    dp(10)
                ),
                maxOf(
                    root.paddingTop,
                    dp(12)
                ),
                maxOf(
                    root.paddingRight,
                    dp(10)
                ),
                maxOf(
                    root.paddingBottom,
                    dp(12)
                )
            )

            val params =
                root.layoutParams

            if (
                params != null
            ) {
                /*
                 * 고정된 버튼 높이를 전부 WRAP_CONTENT로 바꾼다.
                 * 가로 width/weight 값은 그대로 유지되므로
                 * 2열 버튼 구조도 깨지지 않는다.
                 */
                params.height =
                    ViewGroup.LayoutParams.WRAP_CONTENT

                root.layoutParams =
                    params
            }
        }

        if (
            root is ViewGroup
        ) {
            for (
                index in 0 until root.childCount
            ) {
                applyProfileButtonTextSizes(
                    root.getChildAt(
                        index
                    )
                )
            }
        }
    }

    private fun createDivider(): TextView {
        return TextView(this).apply {
            setBackgroundColor(Color.rgb(70, 70, 70))
        }
    }

    private fun createHorizontalButtonRow(
        leftText: String,
        rightText: String,
        onLeft: () -> Unit,
        onRight: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL

            addView(
                Button(this@HaloAccessibilityService).apply {
                    text = leftText
                    textSize = 18f
                    setOnClickListener { onLeft() }
                },
                LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                    marginEnd = dp(5)
                }
            )

            addView(
                Button(this@HaloAccessibilityService).apply {
                    text = rightText
                    textSize = 18f
                    setOnClickListener { onRight() }
                },
                LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                    marginStart = dp(5)
                }
            )
        }
    }

    private fun formatMagnifierScale(scale: Float): String {
        return if (scale % 1f == 0f) {
            "${scale.toInt()}배"
        } else {
            "${scale}배"
        }
    }

    // =========================================================
    // 보기 편하게
    // =========================================================

    private fun showReadableOptionsMenu() {
        removeResultPanel()
        removeActionMenu()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = createRoundedBackground(
                Color.rgb(18, 18, 18),
                Color.rgb(75, 155, 255),
                24
            )
        }

        panel.addView(TextView(this).apply {
            text = "보기 편하게"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(100, 190, 255))
            setPadding(0, dp(4), 0, dp(8))
        })

        val magnifierButton = createActionButton(
            "🔎  돋보기로 보기"
        )
        magnifierButton.setOnClickListener {
            removeResultPanel()
            startWindowMagnifier()
        }
        panel.addView(magnifierButton)

        val largeTextButton = createActionButton(
            "📝  큰 글자로 보기"
        )
        largeTextButton.setOnClickListener {
            removeResultPanel()

            val metricStartMs =
                SystemClock.elapsedRealtime()

            refreshCurrentScreenContext(
                source = "large_text_start"
            )

            showMessagePanel(
                "큰 글자로 보기",
                "현재 화면의 글자를 확인하고 있습니다..."
            )

            resolveReadableText(
                onReady = { text, usedOcr ->
                    val metricLatencyMs =
                        SystemClock.elapsedRealtime() -
                            metricStartMs

                    recordReadableMetric(
                        feature = "large_text",
                        usedOcr = usedOcr,
                        latencyMs = metricLatencyMs,
                        textLength = text.length
                    )

                    readableTextSizeSp = profileTextSizeSp

                    showZoomTextPanel(
                        titleText = "큰 글자로 보기",
                        guideText = "",
                        contentText = text,
                        readButtonText = null,
                        onRead = null
                    )
                },
                onError = { message ->
                    val metricLatencyMs =
                        SystemClock.elapsedRealtime() -
                            metricStartMs

                    recordMetricFailure(
                        feature = "large_text",
                        latencyMs = metricLatencyMs,
                        reason = message
                    )

                    showMessagePanel(
                        "큰 글자로 보기",
                        message
                    )
                }
            )
        }
        panel.addView(largeTextButton)

        panel.addView(
            Button(this).apply {
                text = "HALO 메뉴로 돌아가기"
                textSize = 17f
                setOnClickListener {
                    removeResultPanel()
                    showActionMenu()
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply {
                topMargin = dp(8)
            }
        )

        val params = WindowManager.LayoutParams(
            adaptiveOverlayWidth(350),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        try {
            applyProfileButtonTextSizes(panel)
            windowManager.addView(panel, params)
            resultPanel = panel
        } catch (e: Exception) {
            Log.e(TAG, "보기 편하게 선택 화면 표시 실패", e)
        }
    }

    // =========================================================
    // Window Magnifier
    // =========================================================

    private fun initializeMagnificationListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (magnificationChangedListener != null) return

        val listener = object :
            AccessibilityService.MagnificationController.OnMagnificationChangedListener {

            override fun onMagnificationChanged(
                controller: AccessibilityService.MagnificationController,
                region: Region,
                config: MagnificationConfig
            ) {
                if (!isHaloWindowMagnifierActive) return

                if (
                    config.mode != MagnificationConfig.MAGNIFICATION_MODE_WINDOW ||
                    config.scale <= 1.0f
                ) {
                    isHaloWindowMagnifierActive = false
                    isMagnifierOverTrash = false
                    mainHandler.removeCallbacks(magnifierDismissRunnable)
                    hideMagnifierTrashZone()
                    return
                }

                checkMagnifierTrashZone(region)
            }

            @Suppress("DEPRECATION")
            override fun onMagnificationChanged(
                controller: AccessibilityService.MagnificationController,
                region: Region,
                scale: Float,
                centerX: Float,
                centerY: Float
            ) {}
        }

        try {
            magnificationController.addListener(listener, mainHandler)
            magnificationChangedListener = listener
        } catch (e: Exception) {
            Log.e(TAG, "돋보기 리스너 등록 실패", e)
        }
    }

    private fun startWindowMagnifier() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            showMessagePanel(
                "돋보기",
                "이 기능은 Android 13 이상에서 사용할 수 있습니다."
            )
            return
        }

        try {
            val bounds = windowManager.currentWindowMetrics.bounds

            val config = MagnificationConfig.Builder()
                .setMode(MagnificationConfig.MAGNIFICATION_MODE_WINDOW)
                .setScale(profileMagnifierScale)
                .setCenterX(bounds.centerX().toFloat())
                .setCenterY(bounds.centerY().toFloat())
                .build()

            val success = magnificationController.setMagnificationConfig(
                config,
                true
            )

            if (success) {
                isHaloWindowMagnifierActive = true
                isMagnifierOverTrash = false
                showMagnifierTrashZone()
            } else {
                showMessagePanel(
                    "돋보기",
                    "창 돋보기를 시작하지 못했습니다."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "창 돋보기 시작 실패", e)
            showMessagePanel(
                "돋보기",
                "창 돋보기를 시작하지 못했습니다."
            )
        }
    }

    private fun stopWindowMagnifier() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        mainHandler.removeCallbacks(magnifierDismissRunnable)

        isHaloWindowMagnifierActive = false
        isMagnifierOverTrash = false

        try {
            val success = magnificationController.resetCurrentMagnification(true)

            if (success) {
                hideMagnifierTrashZone()
            } else {
                isHaloWindowMagnifierActive = true
            }
        } catch (e: Exception) {
            isHaloWindowMagnifierActive = true
            Log.e(TAG, "창 돋보기 종료 실패", e)
        }
    }

    private fun showMagnifierTrashZone() {
        hideMagnifierTrashZone()

        val trash = TextView(this).apply {
            text = "🗑\n돋보기를 여기로 이동"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = createRoundedBackground(
                Color.rgb(85, 35, 35),
                Color.rgb(210, 90, 90),
                22
            )
        }

        val params = WindowManager.LayoutParams(
            dp(210),
            dp(88),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = dp(20)

        try {
            windowManager.addView(trash, params)
            magnifierTrashView = trash
        } catch (e: Exception) {
            Log.e(TAG, "돋보기 종료 영역 표시 실패", e)
        }
    }

    private fun checkMagnifierTrashZone(region: Region) {
        val magnifierBounds = region.bounds
        if (magnifierBounds.isEmpty) return

        val screenBounds = windowManager.currentWindowMetrics.bounds
        val detectionWidth = dp(300)
        val detectionHeight = dp(180)

        val detectionLeft = screenBounds.centerX() - detectionWidth / 2
        val detectionRight = screenBounds.centerX() + detectionWidth / 2
        val detectionTop = screenBounds.bottom - detectionHeight

        val overTrash =
            magnifierBounds.right >= detectionLeft &&
                magnifierBounds.left <= detectionRight &&
                magnifierBounds.bottom >= detectionTop

        if (overTrash) {
            if (!isMagnifierOverTrash) {
                isMagnifierOverTrash = true

                magnifierTrashView?.apply {
                    text = "🗑\n여기에서 놓으면 종료"
                    background = createRoundedBackground(
                        Color.rgb(185, 35, 35),
                        Color.rgb(255, 150, 150),
                        22
                    )
                }
            }

            mainHandler.removeCallbacks(magnifierDismissRunnable)
            mainHandler.postDelayed(
                magnifierDismissRunnable,
                MAGNIFIER_DISMISS_DELAY_MS
            )
        } else {
            mainHandler.removeCallbacks(magnifierDismissRunnable)

            if (isMagnifierOverTrash) {
                isMagnifierOverTrash = false

                magnifierTrashView?.apply {
                    text = "🗑\n돋보기를 여기로 이동"
                    background = createRoundedBackground(
                        Color.rgb(85, 35, 35),
                        Color.rgb(210, 90, 90),
                        22
                    )
                }
            }
        }
    }

    private fun hideMagnifierTrashZone() {
        mainHandler.removeCallbacks(magnifierDismissRunnable)

        magnifierTrashView?.let {
            try {
                if (::windowManager.isInitialized) {
                    windowManager.removeView(it)
                }
            } catch (_: Exception) {}
        }

        magnifierTrashView = null
    }

    // =========================================================
    // 중요한 내용만
    // =========================================================

    private fun requestImportantScreen(
        imageBase64: String? = null,
        metricStartMs: Long = SystemClock.elapsedRealtime(),
        visionUsed: Boolean = false
    ) {
        val screenText = currentScreenLines.joinToString("\n")

        showMessagePanel(
            "중요한 내용만",
            if (imageBase64 == null) {
                "현재 화면의 중요한 정보를 확인하고 있습니다..."
            } else {
                "화면의 시각 정보까지 분석하고 있습니다..."
            }
        )

        Thread {
            var connection: HttpURLConnection? = null

            try {
                val url = URL("$SERVER_BASE_URL/screen/important")
                connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.connectTimeout = 15000
                connection.readTimeout = 60000
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
                )

                val requestJson = JSONObject().apply {
                    put("screen_text", screenText)

                    if (!imageBase64.isNullOrBlank()) {
                        put("image_base64", imageBase64)
                    }
                }

                connection.outputStream.use { output ->
                    output.write(
                        requestJson.toString().toByteArray(Charsets.UTF_8)
                    )
                    output.flush()
                }

                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val responseText = stream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?: ""

                if (responseCode !in 200..299) {
                    val metricLatencyMs =
                        SystemClock.elapsedRealtime() -
                            metricStartMs

                    mainHandler.post {
                        recordMetricFailure(
                            feature = "important",
                            latencyMs = metricLatencyMs,
                            reason = "HTTP_$responseCode"
                        )

                        showMessagePanel(
                            "중요한 내용만",
                            "서버 오류가 발생했습니다.\n\n응답 코드: $responseCode"
                        )
                    }
                    return@Thread
                }

                val json = JSONObject(responseText)
                val needsVision = json.optBoolean("needs_vision", false)
                val answer = json.optString("answer", "")
                val reason = json.optString("reason", "")

                Log.d(
                    TAG,
                    "중요 정보 / needsVision=$needsVision / reason=$reason"
                )

                if (needsVision && imageBase64 == null) {
                    mainHandler.post {
                        showMessagePanel(
                            "중요한 내용만",
                            "화면의 시각 정보를 추가로 확인하고 있습니다..."
                        )

                        captureScreenshotBase64(
                            onReady = { base64 ->
                                requestImportantScreen(
                                    imageBase64 = base64,
                                    metricStartMs = metricStartMs,
                                    visionUsed = true
                                )
                            },
                            onError = { message ->
                                val metricLatencyMs =
                                    SystemClock.elapsedRealtime() -
                                        metricStartMs

                                recordMetricFailure(
                                    feature = "important",
                                    latencyMs = metricLatencyMs,
                                    reason = message
                                )

                                showMessagePanel(
                                    "중요한 내용만",
                                    message
                                )
                            }
                        )
                    }
                    return@Thread
                }

                val finalAnswer = answer.ifBlank {
                    "현재 화면에서 중요한 내용을 확인하지 못했습니다."
                }

                val metricLatencyMs =
                    SystemClock.elapsedRealtime() -
                        metricStartMs

                mainHandler.post {
                    recordAiMetric(
                        feature = "important",
                        usedVision = visionUsed || imageBase64 != null,
                        latencyMs = metricLatencyMs,
                        answerLength = finalAnswer.length
                    )

                    showImportantResultPanel(finalAnswer)

                    if (isTtsReady && autoReadImportantResult) {
                        speakText(finalAnswer)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "중요 정보 API 연결 실패", e)

                val metricLatencyMs =
                    SystemClock.elapsedRealtime() -
                        metricStartMs

                mainHandler.post {
                    recordMetricFailure(
                        feature = "important",
                        latencyMs = metricLatencyMs,
                        reason = "API_CONNECTION_ERROR"
                    )

                    showMessagePanel(
                        "중요한 내용만",
                        "서버에 연결할 수 없습니다.\n\n인터넷 연결과 HaloVision 서버 상태를 확인해주세요."
                    )
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun showImportantResultPanel(answer: String) {
        readableTextSizeSp = profileTextSizeSp

        showZoomTextPanel(
            titleText = "중요한 내용만",
            guideText = "",
            contentText = answer,
            readButtonText = "🔊 핵심 내용 읽어주기",
            onRead = {
                if (!isTtsReady) {
                    showMessagePanel(
                        "음성 읽기",
                        "음성 읽기 기능을 준비하지 못했습니다."
                    )
                } else {
                    removeResultPanel()
                    showTtsControlPanel(
                        textToRead = answer,
                        statusMessage = "AI가 정리한 핵심 내용을 읽고 있습니다."
                    )
                    speakText(answer)
                }
            },
            extraButtonText = "💬 질문하기",
            onExtraButton = {
                textToSpeech?.stop()
                removeResultPanel()
                startQuestionListening()
            }
        )
    }

    // =========================================================
    // 공통 큰 글자 Panel
    // =========================================================

    private fun showZoomTextPanel(
        titleText: String,
        guideText: String,
        contentText: String,
        readButtonText: String?,
        onRead: (() -> Unit)?,
        extraButtonText: String? = null,
        onExtraButton: (() -> Unit)? = null
    ) {
        removeResultPanel()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = createRoundedBackground(
                Color.rgb(12, 12, 12),
                Color.rgb(75, 155, 255),
                24
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            TextView(this).apply {
                text = titleText
                textSize = 23f
                setTextColor(Color.rgb(100, 190, 255))
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val sizeLabel = TextView(this).apply {
            text = "${readableTextSizeSp.toInt()}sp"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = createRoundedBackground(
                Color.rgb(40, 40, 40),
                Color.rgb(80, 80, 80),
                12
            )
        }
        header.addView(sizeLabel)
        panel.addView(header)

        if (
            guideText.isNotBlank()
        ) {
            panel.addView(
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
            )
        }

        val zoomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val decreaseButton = Button(this).apply {
            text = "A−"
            textSize = 20f
        }

        val increaseButton = Button(this).apply {
            text = "A+"
            textSize = 20f
        }

        zoomRow.addView(
            decreaseButton,
            LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                marginEnd = dp(6)
            }
        )

        zoomRow.addView(
            increaseButton,
            LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                marginStart = dp(6)
            }
        )

        panel.addView(zoomRow)

        val scrollView = ScrollView(this)
        val content = TextView(this).apply {
            text = contentText
            setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                readableTextSizeSp
            )
            setTextColor(Color.WHITE)
            setLineSpacing(dp(10).toFloat(), 1.3f)
            setPadding(dp(2), dp(18), dp(2), dp(20))
        }
        scrollView.addView(content)

        panel.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        fun updateTextSize(newSize: Float) {
            readableTextSizeSp = newSize.coerceIn(
                MIN_TEXT_SIZE_SP,
                MAX_TEXT_SIZE_SP
            )

            content.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                readableTextSizeSp
            )
            sizeLabel.text = "${readableTextSizeSp.toInt()}sp"
        }

        decreaseButton.setOnClickListener {
            updateTextSize(readableTextSizeSp - TEXT_SIZE_STEP_SP)
        }

        increaseButton.setOnClickListener {
            updateTextSize(readableTextSizeSp + TEXT_SIZE_STEP_SP)
        }

        val scaleDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(
                    detector: ScaleGestureDetector
                ): Boolean {
                    updateTextSize(
                        readableTextSizeSp * detector.scaleFactor
                    )
                    return true
                }
            }
        )

        scrollView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            event.pointerCount >= 2 || scaleDetector.isInProgress
        }

        if (readButtonText != null && onRead != null) {
            panel.addView(
                Button(this).apply {
                    text = readButtonText
                    textSize = 18f
                    setOnClickListener { onRead() }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(58)
                ).apply {
                    topMargin = dp(8)
                    bottomMargin = dp(8)
                }
            )
        }

        if (
            extraButtonText != null &&
            onExtraButton != null
        ) {
            panel.addView(
                Button(this).apply {
                    text =
                        extraButtonText

                    setOnClickListener {
                        onExtraButton()
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(58)
                ).apply {
                    bottomMargin =
                        dp(8)
                }
            )
        }

        panel.addView(
            Button(this).apply {
                text = "HALO 메뉴로 돌아가기"
                textSize = 17f
                setOnClickListener {
                    textToSpeech?.stop()
                    removeResultPanel()
                    showActionMenu()
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            )
        )

        val params = WindowManager.LayoutParams(
            adaptiveOverlayWidth(370),
            adaptiveOverlayHeight(650),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        try {
            applyProfileButtonTextSizes(panel)
            windowManager.addView(panel, params)
            resultPanel = panel
        } catch (e: Exception) {
            Log.e(TAG, "확대 패널 표시 실패", e)
        }
    }

    // =========================================================
    // TTS
    // =========================================================

    private fun initializeTextToSpeech() {
        if (textToSpeech != null) return

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.KOREAN)

                isTtsReady =
                    result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED

                if (isTtsReady) {
                    textToSpeech?.setSpeechRate(ttsSpeechRate)
                    textToSpeech?.setPitch(1.0f)
                }
            } else {
                isTtsReady = false
            }
        }
    }

    private fun startReadingCurrentScreen() {
        val metricStartMs =
            SystemClock.elapsedRealtime()

        if (!isTtsReady) {
            recordMetricFailure(
                feature = "read_aloud",
                latencyMs = 0L,
                reason = "TTS_NOT_READY"
            )

            showMessagePanel(
                "화면 읽어줘",
                "음성 읽기 기능을 준비하지 못했습니다.\n\n휴대폰의 TTS 설정을 확인해주세요."
            )
            return
        }

        showMessagePanel(
            "화면 읽어줘",
            "현재 화면에서 읽을 내용을 확인하고 있습니다..."
        )

        resolveReadableText(
            onReady = { text, usedOcr ->
                val metricLatencyMs =
                    SystemClock.elapsedRealtime() -
                        metricStartMs

                recordReadableMetric(
                    feature = "read_aloud",
                    usedOcr = usedOcr,
                    latencyMs = metricLatencyMs,
                    textLength = text.length
                )

                showTtsControlPanel(
                    textToRead = text,
                    statusMessage = if (usedOcr) {
                        "화면 이미지에서 찾은 글자를 읽고 있습니다."
                    } else {
                        "현재 화면의 내용을 읽고 있습니다."
                    }
                )

                speakText(text)
            },
            onError = { message ->
                val metricLatencyMs =
                    SystemClock.elapsedRealtime() -
                        metricStartMs

                recordMetricFailure(
                    feature = "read_aloud",
                    latencyMs = metricLatencyMs,
                    reason = message
                )

                showMessagePanel(
                    "화면 읽어줘",
                    message
                )
            }
        )
    }

    private fun speakText(text: String) {
        if (!isTtsReady) return

        textToSpeech?.stop()

        splitTextForTts(text).forEachIndexed { index, chunk ->
            textToSpeech?.speak(
                chunk,
                if (index == 0) {
                    TextToSpeech.QUEUE_FLUSH
                } else {
                    TextToSpeech.QUEUE_ADD
                },
                null,
                "halo_tts_$index"
            )
        }
    }

    private fun splitTextForTts(text: String): List<String> {
        val maxLength = 3000
        var remaining = text.trim()

        if (remaining.isEmpty()) return emptyList()
        if (remaining.length <= maxLength) return listOf(remaining)

        val chunks = mutableListOf<String>()

        while (remaining.length > maxLength) {
            val candidate = remaining.substring(0, maxLength)

            var cutIndex = maxOf(
                candidate.lastIndexOf('\n'),
                candidate.lastIndexOf(". "),
                candidate.lastIndexOf(' ')
            )

            if (cutIndex < maxLength / 2) cutIndex = maxLength

            val chunk = remaining.substring(0, cutIndex).trim()
            if (chunk.isNotEmpty()) chunks.add(chunk)

            remaining = remaining.substring(cutIndex).trim()
        }

        if (remaining.isNotEmpty()) chunks.add(remaining)

        return chunks
    }

    private fun showTtsControlPanel(
        textToRead: String,
        statusMessage: String
    ) {
        removeResultPanel()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = createRoundedBackground(
                Color.rgb(12, 12, 12),
                Color.rgb(75, 155, 255),
                24
            )
        }

        panel.addView(TextView(this).apply {
            text = "화면 읽어줘"
            textSize = 24f
            setTextColor(Color.rgb(100, 190, 255))
            setPadding(0, 0, 0, dp(18))
        })

        val status = TextView(this).apply {
            text = statusMessage
            textSize = 30f
            setTextColor(Color.WHITE)
            setLineSpacing(dp(8).toFloat(), 1.25f)
        }

        panel.addView(
            status,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        panel.addView(
            Button(this).apply {
                text = "처음부터 다시 읽기"
                textSize = 18f
                setOnClickListener {
                    status.text = "처음부터 다시 읽고 있습니다."
                    speakText(textToRead)
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            ).apply {
                bottomMargin = dp(10)
            }
        )

        panel.addView(
            Button(this).apply {
                text = "읽기 중지"
                textSize = 18f
                setOnClickListener {
                    textToSpeech?.stop()
                    status.text = "읽기를 중지했습니다."
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            ).apply {
                bottomMargin = dp(10)
            }
        )

        panel.addView(
            Button(this).apply {
                text = "HALO 메뉴로 돌아가기"
                textSize = 17f
                setOnClickListener {
                    textToSpeech?.stop()
                    removeResultPanel()
                    showActionMenu()
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            )
        )

        val params = WindowManager.LayoutParams(
            adaptiveOverlayWidth(360),
            adaptiveOverlayHeight(430),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        try {
            applyProfileButtonTextSizes(panel)
            windowManager.addView(panel, params)
            resultPanel = panel
        } catch (e: Exception) {
            Log.e(TAG, "TTS 패널 표시 실패", e)
        }
    }

    // =========================================================
    // SpeechRecognizer
    // =========================================================

    private fun initializeSpeechRecognizer() {
        if (speechRecognizer != null) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "SpeechRecognizer 사용 불가")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    isListeningForQuestion = false

                    mainHandler.post {
                        showMessagePanel(
                            "질문하기",
                            "질문을 인식하지 못했습니다.\n\n다시 질문하기를 눌러주세요."
                        )
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListeningForQuestion = false

                    val question = results
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        ?.firstOrNull()
                        ?.trim()

                    if (question.isNullOrEmpty()) {
                        mainHandler.post {
                            showMessagePanel(
                                "질문하기",
                                "질문을 인식하지 못했습니다."
                            )
                        }
                        return
                    }

                    mainHandler.post {
                        /*
                         * 사용자가 질문을 말하는 동안 카메라/화면이 변했을 수 있으므로
                         * 질문 인식이 끝난 바로 그 시점의 화면을 다시 읽는다.
                         */
                        removeResultPanel()

                        refreshCurrentScreenContext(
                            source = "question_recognized"
                        )

                        showMessagePanel(
                            "질문하기",
                            "질문\n\n$question\n\n답변을 분석하고 있습니다..."
                        )

                        requestScreenQuestion(
                            question
                        )
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            }
        )
    }

    private fun startQuestionListening() {
        textToSpeech?.stop()

        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            showMessagePanel(
                "질문하기",
                "마이크 권한이 필요합니다.\n\nHaloVision 앱 설정에서 마이크 권한을 허용해주세요."
            )
            return
        }

        if (speechRecognizer == null) initializeSpeechRecognizer()

        if (speechRecognizer == null) {
            showMessagePanel(
                "질문하기",
                "이 기기에서 음성 인식을 사용할 수 없습니다."
            )
            return
        }

        if (isListeningForQuestion) {
            speechRecognizer?.cancel()
            isListeningForQuestion = false
        }

        /*
         * 다시 질문하기에서 이전 답변 패널이 남아 있을 수 있으므로
         * 먼저 Halo 패널을 제거한 뒤 현재 앱 화면의 Node Tree를 읽는다.
         */
        removeResultPanel()

        refreshCurrentScreenContext(
            source = "question_start"
        )

        showMessagePanel(
            "질문하기",
            "질문을 말씀해주세요.\n\n예: \"발표가 몇 시야?\""
        )

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        isListeningForQuestion = true
        speechRecognizer?.startListening(intent)
    }

    // =========================================================
    // 질문하기
    // =========================================================

    private fun requestScreenQuestion(
        question: String,
        imageBase64: String? = null,
        metricStartMs: Long = SystemClock.elapsedRealtime(),
        visionUsed: Boolean = false
    ) {
        val screenText = currentScreenLines.joinToString("\n")

        Thread {
            var connection: HttpURLConnection? = null

            try {
                val url = URL("$SERVER_BASE_URL/screen/question")
                connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.connectTimeout = 15000
                connection.readTimeout = 60000
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
                )

                val requestJson = JSONObject().apply {
                    put("screen_text", screenText)
                    put("question", question)

                    if (!imageBase64.isNullOrBlank()) {
                        put("image_base64", imageBase64)
                    }
                }

                connection.outputStream.use { output ->
                    output.write(
                        requestJson.toString().toByteArray(Charsets.UTF_8)
                    )
                    output.flush()
                }

                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val responseText = stream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?: ""

                if (responseCode !in 200..299) {
                    val metricLatencyMs =
                        SystemClock.elapsedRealtime() -
                            metricStartMs

                    mainHandler.post {
                        recordMetricFailure(
                            feature = "question",
                            latencyMs = metricLatencyMs,
                            reason = "HTTP_$responseCode"
                        )

                        showMessagePanel(
                            "질문하기",
                            "서버 오류가 발생했습니다.\n\n응답 코드: $responseCode"
                        )
                    }
                    return@Thread
                }

                val json = JSONObject(responseText)
                val needsVision = json.optBoolean("needs_vision", false)
                val answer = json.optString("answer", "")
                val reason = json.optString("reason", "")

                Log.d(
                    TAG,
                    "질문 / needsVision=$needsVision / reason=$reason"
                )

                if (needsVision && imageBase64 == null) {
                    mainHandler.post {
                        showMessagePanel(
                            "질문하기",
                            "질문의 답을 확인하기 위해 화면의 시각 정보를 추가로 확인하고 있습니다..."
                        )

                        captureScreenshotBase64(
                            onReady = { base64 ->
                                requestScreenQuestion(
                                    question = question,
                                    imageBase64 = base64,
                                    metricStartMs = metricStartMs,
                                    visionUsed = true
                                )
                            },
                            onError = { message ->
                                val metricLatencyMs =
                                    SystemClock.elapsedRealtime() -
                                        metricStartMs

                                recordMetricFailure(
                                    feature = "question",
                                    latencyMs = metricLatencyMs,
                                    reason = message
                                )

                                showMessagePanel(
                                    "질문하기",
                                    message
                                )
                            }
                        )
                    }
                    return@Thread
                }

                val finalAnswer = answer.ifBlank {
                    "현재 화면에서는 해당 정보를 확인할 수 없습니다."
                }

                val metricLatencyMs =
                    SystemClock.elapsedRealtime() -
                        metricStartMs

                mainHandler.post {
                    recordAiMetric(
                        feature = "question",
                        usedVision = visionUsed || imageBase64 != null,
                        latencyMs = metricLatencyMs,
                        answerLength = finalAnswer.length
                    )

                    val shouldFindUiTarget =
                        isUiTargetRequest(
                            question
                        )

                    if (
                        shouldFindUiTarget
                    ) {
                        /*
                         * 위치 찾기 요청일 때만 로컬 UI 탐색.
                         * 현재 화면에서 못 찾으면 최대 3회 아래로 스크롤하면서 탐색한다.
                         */
                        findTargetWithAutoScroll(
                            question = question,
                            answer = finalAnswer,

                            onFound = { targetNode ->
                                showTargetQuestionPanel(
                                    question = question,
                                    answer = finalAnswer,
                                    target = targetNode
                                )

                                if (
                                    isTtsReady &&
                                    autoReadQuestionAnswer
                                ) {
                                    speakText(
                                        finalAnswer
                                    )
                                }
                            },

                            onNotFound = {
                                val notFoundAnswer =
                                    "현재 화면과 아래쪽 화면을 확인했지만 요청한 버튼이나 항목을 찾지 못했습니다."

                                showQuestionAnswerPanel(
                                    question = question,
                                    answer = notFoundAnswer
                                )

                                if (
                                    isTtsReady &&
                                    autoReadQuestionAnswer
                                ) {
                                    speakText(
                                        notFoundAnswer
                                    )
                                }
                            }
                        )
                    } else {
                        Log.d(
                            TAG,
                            "HALO_TARGET | skipped=true | reason=no_explicit_location_intent"
                        )

                        showQuestionAnswerPanel(
                            question,
                            finalAnswer
                        )

                        if (
                            isTtsReady &&
                            autoReadQuestionAnswer
                        ) {
                            speakText(
                                finalAnswer
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "질문 API 연결 실패", e)

                val metricLatencyMs =
                    SystemClock.elapsedRealtime() -
                        metricStartMs

                mainHandler.post {
                    recordMetricFailure(
                        feature = "question",
                        latencyMs = metricLatencyMs,
                        reason = "API_CONNECTION_ERROR"
                    )

                    showMessagePanel(
                        "질문하기",
                        "서버에 연결할 수 없습니다.\n\n인터넷 연결과 HaloVision 서버 상태를 확인해주세요."
                    )
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun showQuestionAnswerPanel(
        question: String,
        answer: String
    ) {
        removeResultPanel()
        readableTextSizeSp = profileTextSizeSp

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = createRoundedBackground(
                Color.rgb(12, 12, 12),
                Color.rgb(75, 155, 255),
                24
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            TextView(this).apply {
                text = "질문 답변"
                textSize = 23f
                setTextColor(Color.rgb(100, 190, 255))
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val sizeLabel = TextView(this).apply {
            text = "${readableTextSizeSp.toInt()}sp"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = createRoundedBackground(
                Color.rgb(40, 40, 40),
                Color.rgb(80, 80, 80),
                12
            )
        }
        header.addView(sizeLabel)
        panel.addView(header)

        panel.addView(TextView(this).apply {
            text = "질문: $question"
            textSize = 17f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(10), 0, dp(10))
        })

        val zoomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val decrease = Button(this).apply {
            text = "A−"
            textSize = 20f
        }
        val increase = Button(this).apply {
            text = "A+"
            textSize = 20f
        }

        zoomRow.addView(
            decrease,
            LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                marginEnd = dp(6)
            }
        )
        zoomRow.addView(
            increase,
            LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                marginStart = dp(6)
            }
        )
        panel.addView(zoomRow)

        val scrollView = ScrollView(this)
        val answerView = TextView(this).apply {
            text = answer
            setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                readableTextSizeSp
            )
            setTextColor(Color.WHITE)
            setLineSpacing(dp(10).toFloat(), 1.3f)
            setPadding(dp(2), dp(18), dp(2), dp(20))
        }
        scrollView.addView(answerView)

        panel.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        fun updateSize(newSize: Float) {
            readableTextSizeSp = newSize.coerceIn(
                MIN_TEXT_SIZE_SP,
                MAX_TEXT_SIZE_SP
            )
            answerView.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                readableTextSizeSp
            )
            sizeLabel.text = "${readableTextSizeSp.toInt()}sp"
        }

        decrease.setOnClickListener {
            updateSize(readableTextSizeSp - TEXT_SIZE_STEP_SP)
        }
        increase.setOnClickListener {
            updateSize(readableTextSizeSp + TEXT_SIZE_STEP_SP)
        }

        val scaleDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    updateSize(readableTextSizeSp * detector.scaleFactor)
                    return true
                }
            }
        )

        scrollView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            event.pointerCount >= 2 || scaleDetector.isInProgress
        }

        panel.addView(
            Button(this).apply {
                text = "🔊 답변 다시 듣기"
                textSize = 18f
                setOnClickListener {
                    if (isTtsReady) {
                        speakText(answer)
                    } else {
                        showMessagePanel(
                            "음성 읽기",
                            "음성 읽기 기능을 사용할 수 없습니다."
                        )
                    }
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
        )

        panel.addView(
            Button(this).apply {
                text = "🎤 다시 질문하기"
                textSize = 18f
                setOnClickListener {
                    textToSpeech?.stop()
                    removeTargetHighlight()
                    removeResultPanel()
                    startQuestionListening()
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            ).apply {
                bottomMargin = dp(8)
            }
        )

        panel.addView(
            Button(this).apply {
                text = "HALO 메뉴로 돌아가기"
                textSize = 17f
                setOnClickListener {
                    textToSpeech?.stop()
                    removeTargetHighlight()
                    removeResultPanel()
                    showActionMenu()
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            )
        )

        val params = WindowManager.LayoutParams(
            adaptiveOverlayWidth(370),
            adaptiveOverlayHeight(650),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        try {
            applyProfileButtonTextSizes(panel)
            windowManager.addView(panel, params)
            resultPanel = panel
        } catch (e: Exception) {
            Log.e(TAG, "질문 답변 패널 표시 실패", e)
        }
    }

    // =========================================================
    // 일반 메시지 Panel
    // =========================================================

    private fun showMessagePanel(
        panelTitle: String,
        message: String
    ) {
        removeResultPanel()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = createRoundedBackground(
                Color.rgb(18, 18, 18),
                Color.rgb(75, 155, 255),
                24
            )
        }

        panel.addView(TextView(this).apply {
            text = panelTitle
            textSize = 23f
            setTextColor(Color.rgb(100, 190, 255))
            setPadding(0, 0, 0, dp(16))
        })

        panel.addView(
            TextView(this).apply {
                text = message
                textSize = 18f
                setTextColor(Color.WHITE)
                setLineSpacing(dp(5).toFloat(), 1.2f)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        panel.addView(
            Button(this).apply {
                text = "HALO 메뉴로 돌아가기"
                textSize = 17f
                setOnClickListener {
                    if (isListeningForQuestion) {
                        speechRecognizer?.cancel()
                        isListeningForQuestion = false
                    }
                    removeResultPanel()
                    showActionMenu()
                }
            }
        )

        val params = WindowManager.LayoutParams(
            adaptiveOverlayWidth(350),
            adaptiveOverlayHeight(360),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        try {
            applyProfileButtonTextSizes(panel)
            windowManager.addView(panel, params)
            resultPanel = panel
        } catch (e: Exception) {
            Log.e(TAG, "메시지 패널 표시 실패", e)
        }
    }

    // =========================================================
    // Helper
    // =========================================================

    private fun removeActionMenu() {
        actionMenu?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        actionMenu = null
    }

    private fun removeResultPanel() {
        resultPanel?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        resultPanel = null
    }

    // =========================================================
    // 기기별 화면 크기에 맞춘 Overlay 크기
    // =========================================================

    private fun adaptiveOverlayWidth(
        preferredDp: Int,
        horizontalMarginDp: Int = 12
    ): Int {
        val screenWidth =
            try {
                windowManager
                    .currentWindowMetrics
                    .bounds
                    .width()
            } catch (_: Exception) {
                dp(preferredDp)
            }

        val available =
            (
                screenWidth -
                    dp(horizontalMarginDp * 2)
                )
                .coerceAtLeast(
                    dp(220)
                )

        return minOf(
            dp(preferredDp),
            available
        )
    }

    private fun adaptiveOverlayHeight(
        preferredDp: Int,
        verticalMarginDp: Int = 12
    ): Int {
        val screenHeight =
            try {
                windowManager
                    .currentWindowMetrics
                    .bounds
                    .height()
            } catch (_: Exception) {
                dp(preferredDp)
            }

        val available =
            (
                screenHeight -
                    dp(verticalMarginDp * 2)
                )
                .coerceAtLeast(
                    dp(280)
                )

        return minOf(
            dp(preferredDp),
            available
        )
    }

    private fun createRoundedBackground(
        backgroundColor: Int,
        borderColor: Int,
        radiusDp: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(backgroundColor)
            setStroke(dp(2), borderColor)
        }
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
            ).toInt()
    }
}
