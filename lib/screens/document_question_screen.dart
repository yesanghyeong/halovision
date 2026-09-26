import 'package:flutter/material.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:speech_to_text/speech_to_text.dart' as stt;

class DocumentQuestionScreen extends StatefulWidget {
  const DocumentQuestionScreen({
    super.key,
  });

  @override
  State<DocumentQuestionScreen> createState() =>
      _DocumentQuestionScreenState();
}

class _DocumentQuestionScreenState
    extends State<DocumentQuestionScreen> {
  final TextEditingController _controller =
      TextEditingController();

  final stt.SpeechToText _speech =
      stt.SpeechToText();

  final FlutterTts _tts =
      FlutterTts();

  final List<_QuestionMessage> _messages = [];

  bool _isAnswering = false;
  bool _isListening = false;
  bool _speechAvailable = false;
  bool _isSpeaking = false;

  @override
  void initState() {
    super.initState();

    _initializeSpeech();
    _initializeTts();
  }

  Future<void> _initializeSpeech() async {
    final available =
        await _speech.initialize(
      onStatus: (status) {
        if (!mounted) {
          return;
        }

        if (status == 'done' ||
            status == 'notListening') {
          setState(() {
            _isListening = false;
          });
        }
      },
      onError: (_) {
        if (!mounted) {
          return;
        }

        setState(() {
          _isListening = false;
        });
      },
    );

    if (!mounted) {
      return;
    }

    setState(() {
      _speechAvailable = available;
    });
  }

  Future<void> _initializeTts() async {
    await _tts.setLanguage(
      'ko-KR',
    );

    await _tts.setSpeechRate(
      0.45,
    );

    await _tts.setVolume(
      1.0,
    );

    await _tts.setPitch(
      1.0,
    );

    _tts.setStartHandler(() {
      if (!mounted) {
        return;
      }

      setState(() {
        _isSpeaking = true;
      });
    });

    _tts.setCompletionHandler(() {
      if (!mounted) {
        return;
      }

      setState(() {
        _isSpeaking = false;
      });
    });

    _tts.setCancelHandler(() {
      if (!mounted) {
        return;
      }

      setState(() {
        _isSpeaking = false;
      });
    });
  }

  Future<void> _toggleListening() async {
    if (!_speechAvailable) {
      if (!mounted) {
        return;
      }

      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            '음성 인식을 사용할 수 없습니다.',
          ),
        ),
      );

      return;
    }

    if (_isListening) {
      await _speech.stop();

      if (!mounted) {
        return;
      }

      setState(() {
        _isListening = false;
      });

      return;
    }

    setState(() {
      _isListening = true;
    });

    await _speech.listen(
      onResult: (result) {
        if (!mounted) {
          return;
        }

        setState(() {
          _controller.text =
              result.recognizedWords;

          _controller.selection =
              TextSelection.fromPosition(
            TextPosition(
              offset: _controller.text.length,
            ),
          );
        });
      },
      listenOptions: stt.SpeechListenOptions(
        localeId: 'ko_KR',
        listenMode:
            stt.ListenMode.confirmation,
        partialResults: true,
      ),
    );
  }

  Future<void> _speak(
    String text,
  ) async {
    if (_isSpeaking) {
      await _tts.stop();

      if (!mounted) {
        return;
      }

      setState(() {
        _isSpeaking = false;
      });

      return;
    }

    await _tts.speak(
      text,
    );
  }

  Future<void> _sendQuestion() async {
    final question =
        _controller.text.trim();

    if (question.isEmpty ||
        _isAnswering) {
      return;
    }

    if (_isListening) {
      await _speech.stop();

      if (!mounted) {
        return;
      }

      setState(() {
        _isListening = false;
      });
    }

    setState(() {
      _messages.add(
        _QuestionMessage(
          text: question,
          isUser: true,
        ),
      );

      _controller.clear();
      _isAnswering = true;
    });

    await Future.delayed(
      const Duration(
        milliseconds: 800,
      ),
    );

    if (!mounted) {
      return;
    }

    const answer =
        '현재는 백엔드 연결 전이라 임시 답변을 보여주고 있습니다. '
        '실제 버전에서는 분석된 문서 내용을 바탕으로 질문에 답변합니다.';

    setState(() {
      _messages.add(
        const _QuestionMessage(
          text: answer,
          isUser: false,
        ),
      );

      _isAnswering = false;
    });
  }

  void _clearConversation() {
    setState(() {
      _messages.clear();
    });
  }

  @override
  void dispose() {
    _speech.stop();
    _tts.stop();
    _controller.dispose();

    super.dispose();
  }

  @override
  Widget build(
    BuildContext context,
  ) {
    const yellow =
        Color(0xFFF4D35E);

    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(),

            Expanded(
              child: _messages.isEmpty
                  ? _buildEmptyState()
                  : _buildConversation(),
            ),

            if (_isAnswering)
              Padding(
                padding:
                    const EdgeInsets.fromLTRB(
                  24,
                  8,
                  24,
                  4,
                ),
                child: Row(
                  children: [
                    const SizedBox(
                      width: 18,
                      height: 18,
                      child:
                          CircularProgressIndicator(
                        strokeWidth: 2,
                        color: yellow,
                      ),
                    ),
                    const SizedBox(
                      width: 10,
                    ),
                    Text(
                      '답변을 작성하고 있어요...',
                      style: TextStyle(
                        color: Colors.white
                            .withValues(
                          alpha: 0.65,
                        ),
                        fontSize: 13,
                      ),
                    ),
                  ],
                ),
              ),

            _buildInputArea(),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    const yellow =
        Color(0xFFF4D35E);

    return Padding(
      padding:
          const EdgeInsets.fromLTRB(
        10,
        10,
        14,
        8,
      ),
      child: Row(
        children: [
          IconButton(
            onPressed: () {
              Navigator.pop(context);
            },
            icon: const Icon(
              Icons.arrow_back_rounded,
              color: Colors.white,
              size: 28,
            ),
          ),

          const SizedBox(width: 4),

          const Expanded(
            child: Column(
              crossAxisAlignment:
                  CrossAxisAlignment.start,
              children: [
                Text(
                  '문서 질문하기',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 20,
                    fontWeight:
                        FontWeight.w800,
                  ),
                ),
                SizedBox(height: 2),
                Text(
                  '텍스트 또는 음성으로 질문할 수 있어요.',
                  style: TextStyle(
                    color: Colors.white54,
                    fontSize: 11,
                  ),
                ),
              ],
            ),
          ),

          if (_messages.isNotEmpty)
            IconButton(
              onPressed:
                  _clearConversation,
              tooltip: '대화 초기화',
              icon: const Icon(
                Icons.delete_outline_rounded,
                color: Colors.white54,
              ),
            ),

          const Icon(
            Icons.record_voice_over_rounded,
            color: yellow,
            size: 25,
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyState() {
    const yellow =
        Color(0xFFF4D35E);

    return Center(
      child: SingleChildScrollView(
        padding:
            const EdgeInsets.all(
          28,
        ),
        child: Column(
          mainAxisAlignment:
              MainAxisAlignment.center,
          children: [
            Container(
              width: 90,
              height: 90,
              decoration:
                  BoxDecoration(
                color: yellow
                    .withValues(
                  alpha: 0.12,
                ),
                borderRadius:
                    BorderRadius.circular(
                  27,
                ),
              ),
              child: const Icon(
                Icons
                    .question_answer_outlined,
                color: yellow,
                size: 46,
              ),
            ),

            const SizedBox(
              height: 24,
            ),

            const Text(
              '궁금한 내용을\n질문해보세요.',
              textAlign:
                  TextAlign.center,
              style: TextStyle(
                color: Colors.white,
                fontSize: 26,
                fontWeight:
                    FontWeight.w900,
                height: 1.25,
              ),
            ),

            const SizedBox(
              height: 12,
            ),

            Text(
              '문서 내용 요약, 표의 의미, 그림 설명 등을 질문할 수 있습니다.',
              textAlign:
                  TextAlign.center,
              style: TextStyle(
                color: Colors.white
                    .withValues(
                  alpha: 0.62,
                ),
                fontSize: 14,
                height: 1.5,
              ),
            ),

            const SizedBox(
              height: 26,
            ),

            _SuggestionButton(
              text:
                  '이 문서 내용을 요약해줘',
              onTap: () {
                setState(() {
                  _controller.text =
                      '이 문서 내용을 요약해줘';
                });
              },
            ),

            const SizedBox(
              height: 10,
            ),

            _SuggestionButton(
              text:
                  '표에서 중요한 내용이 뭐야?',
              onTap: () {
                setState(() {
                  _controller.text =
                      '표에서 중요한 내용이 뭐야?';
                });
              },
            ),

            const SizedBox(
              height: 10,
            ),

            _SuggestionButton(
              text:
                  '그림이 무엇을 설명하는지 알려줘',
              onTap: () {
                setState(() {
                  _controller.text =
                      '그림이 무엇을 설명하는지 알려줘';
                });
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildConversation() {
    return ListView.builder(
      padding:
          const EdgeInsets.fromLTRB(
        18,
        18,
        18,
        24,
      ),
      itemCount:
          _messages.length,
      itemBuilder: (
        context,
        index,
      ) {
        final message =
            _messages[index];

        return _MessageBubble(
          message: message,
          onSpeak: message.isUser
              ? null
              : () {
                  _speak(
                    message.text,
                  );
                },
          isSpeaking:
              _isSpeaking,
        );
      },
    );
  }

  Widget _buildInputArea() {
    const yellow =
        Color(0xFFF4D35E);

    return Container(
      padding:
          const EdgeInsets.fromLTRB(
        14,
        12,
        14,
        18,
      ),
      decoration:
          BoxDecoration(
        color: Colors.black,
        border: Border(
          top: BorderSide(
            color: Colors.white
                .withValues(
              alpha: 0.08,
            ),
          ),
        ),
      ),
      child: Column(
        children: [
          if (_isListening)
            Container(
              width: double.infinity,
              margin:
                  const EdgeInsets.only(
                bottom: 10,
              ),
              padding:
                  const EdgeInsets.symmetric(
                horizontal: 14,
                vertical: 10,
              ),
              decoration:
                  BoxDecoration(
                color: yellow
                    .withValues(
                  alpha: 0.10,
                ),
                borderRadius:
                    BorderRadius.circular(
                  14,
                ),
                border: Border.all(
                  color: yellow
                      .withValues(
                    alpha: 0.30,
                  ),
                ),
              ),
              child: const Row(
                children: [
                  Icon(
                    Icons.mic_rounded,
                    color: yellow,
                    size: 19,
                  ),
                  SizedBox(width: 9),
                  Text(
                    '듣고 있어요. 말씀해주세요.',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 13,
                      fontWeight:
                          FontWeight.w700,
                    ),
                  ),
                ],
              ),
            ),

          Row(
            crossAxisAlignment:
                CrossAxisAlignment.end,
            children: [
              SizedBox(
                width: 52,
                height: 52,
                child:
                    OutlinedButton(
                  onPressed:
                      _toggleListening,
                  style:
                      OutlinedButton
                          .styleFrom(
                    foregroundColor:
                        _isListening
                            ? Colors.black
                            : yellow,
                    backgroundColor:
                        _isListening
                            ? yellow
                            : Colors.transparent,
                    side: const BorderSide(
                      color: yellow,
                      width: 1.5,
                    ),
                    padding:
                        EdgeInsets.zero,
                    shape:
                        RoundedRectangleBorder(
                      borderRadius:
                          BorderRadius.circular(
                        17,
                      ),
                    ),
                  ),
                  child: Icon(
                    _isListening
                        ? Icons.stop_rounded
                        : Icons.mic_rounded,
                    size: 25,
                  ),
                ),
              ),

              const SizedBox(
                width: 10,
              ),

              Expanded(
                child: TextField(
                  controller:
                      _controller,
                  minLines: 1,
                  maxLines: 4,
                  style:
                      const TextStyle(
                    color: Colors.white,
                    fontSize: 15,
                  ),
                  decoration:
                      InputDecoration(
                    hintText:
                        _isListening
                            ? '음성을 인식하고 있어요...'
                            : '문서에 대해 질문하세요.',
                    hintStyle:
                        TextStyle(
                      color: Colors.white
                          .withValues(
                        alpha: 0.36,
                      ),
                    ),
                    filled: true,
                    fillColor:
                        const Color(
                      0xFF151515,
                    ),
                    contentPadding:
                        const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 13,
                    ),
                    border:
                        OutlineInputBorder(
                      borderRadius:
                          BorderRadius.circular(
                        18,
                      ),
                      borderSide:
                          BorderSide.none,
                    ),
                  ),
                ),
              ),

              const SizedBox(
                width: 10,
              ),

              SizedBox(
                width: 52,
                height: 52,
                child:
                    FilledButton(
                  onPressed:
                      _isAnswering
                          ? null
                          : _sendQuestion,
                  style:
                      FilledButton.styleFrom(
                    backgroundColor:
                        yellow,
                    foregroundColor:
                        Colors.black,
                    padding:
                        EdgeInsets.zero,
                    shape:
                        RoundedRectangleBorder(
                      borderRadius:
                          BorderRadius.circular(
                        17,
                      ),
                    ),
                  ),
                  child: const Icon(
                    Icons
                        .arrow_upward_rounded,
                    size: 25,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _QuestionMessage {
  final String text;
  final bool isUser;

  const _QuestionMessage({
    required this.text,
    required this.isUser,
  });
}

class _MessageBubble
    extends StatelessWidget {
  final _QuestionMessage message;
  final VoidCallback? onSpeak;
  final bool isSpeaking;

  const _MessageBubble({
    required this.message,
    required this.onSpeak,
    required this.isSpeaking,
  });

  @override
  Widget build(
    BuildContext context,
  ) {
    const yellow =
        Color(0xFFF4D35E);

    return Align(
      alignment: message.isUser
          ? Alignment.centerRight
          : Alignment.centerLeft,
      child: Container(
        constraints:
            const BoxConstraints(
          maxWidth: 320,
        ),
        margin:
            const EdgeInsets.only(
          bottom: 14,
        ),
        padding:
            const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 13,
        ),
        decoration:
            BoxDecoration(
          color: message.isUser
              ? yellow
              : const Color(
                  0xFF171717,
                ),
          borderRadius:
              BorderRadius.circular(
            18,
          ),
          border: message.isUser
              ? null
              : Border.all(
                  color: Colors.white
                      .withValues(
                    alpha: 0.08,
                  ),
                ),
        ),
        child: Column(
          crossAxisAlignment:
              CrossAxisAlignment.start,
          children: [
            Text(
              message.text,
              style: TextStyle(
                color:
                    message.isUser
                        ? Colors.black
                        : Colors.white,
                fontSize: 15,
                height: 1.5,
                fontWeight:
                    message.isUser
                        ? FontWeight.w700
                        : FontWeight.w500,
              ),
            ),

            if (!message.isUser &&
                onSpeak != null) ...[
              const SizedBox(
                height: 8,
              ),
              TextButton.icon(
                onPressed: onSpeak,
                style:
                    TextButton.styleFrom(
                  foregroundColor:
                      yellow,
                  padding:
                      EdgeInsets.zero,
                  minimumSize:
                      const Size(
                    0,
                    34,
                  ),
                ),
                icon: Icon(
                  isSpeaking
                      ? Icons.stop_rounded
                      : Icons.volume_up_outlined,
                  size: 19,
                ),
                label: Text(
                  isSpeaking
                      ? '읽기 중지'
                      : '답변 다시 듣기',
                  style:
                      const TextStyle(
                    fontSize: 12,
                    fontWeight:
                        FontWeight.w700,
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _SuggestionButton
    extends StatelessWidget {
  final String text;
  final VoidCallback onTap;

  const _SuggestionButton({
    required this.text,
    required this.onTap,
  });

  @override
  Widget build(
    BuildContext context,
  ) {
    return SizedBox(
      width: double.infinity,
      child:
          OutlinedButton(
        onPressed: onTap,
        style:
            OutlinedButton.styleFrom(
          foregroundColor:
              Colors.white,
          side: BorderSide(
            color: Colors.white
                .withValues(
              alpha: 0.14,
            ),
          ),
          padding:
              const EdgeInsets.symmetric(
            horizontal: 16,
            vertical: 14,
          ),
          shape:
              RoundedRectangleBorder(
            borderRadius:
                BorderRadius.circular(
              15,
            ),
          ),
        ),
        child: Align(
          alignment:
              Alignment.centerLeft,
          child: Text(
            text,
            style:
                const TextStyle(
              fontSize: 13,
              fontWeight:
                  FontWeight.w700,
            ),
          ),
        ),
      ),
    );
  }
}