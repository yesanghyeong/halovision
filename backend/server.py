import os
import traceback

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from openai import OpenAI
from pydantic import BaseModel


# ============================================================
# 환경 변수
# ============================================================

load_dotenv()

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-6-luna")

if not OPENAI_API_KEY:
    raise RuntimeError(
        "OPENAI_API_KEY가 없습니다. backend/.env 파일을 확인하세요."
    )


# ============================================================
# OpenAI Client
# ============================================================

client = OpenAI(api_key=OPENAI_API_KEY)


# ============================================================
# FastAPI
# ============================================================

app = FastAPI(
    title="HaloVision V3 Backend",
    version="0.3.0",
)


# ============================================================
# Request Models
# ============================================================

class ScreenImportantRequest(BaseModel):
    screen_text: str


class ScreenQuestionRequest(BaseModel):
    screen_text: str
    question: str


# ============================================================
# 기본 확인
# ============================================================

@app.get("/")
def root():
    return {
        "message": "HaloVision V3 backend running"
    }


@app.get("/health")
def health():
    return {
        "status": "ok",
        "service": "halovision-v3",
        "model": OPENAI_MODEL,
        "version": "0.3.0",
    }


# ============================================================
# 중요한 내용만
# ============================================================

@app.post("/screen/important")
def screen_important(request: ScreenImportantRequest):

    try:
        screen_text = request.screen_text.strip()

        if not screen_text:
            raise HTTPException(
                status_code=400,
                detail="현재 화면 텍스트가 비어 있습니다.",
            )

        # 너무 긴 화면 입력 제한
        max_chars = 12000

        if len(screen_text) > max_chars:
            screen_text = screen_text[:max_chars]

        print()
        print("========================================")
        print("HALO SCREEN IMPORTANT REQUEST")
        print("========================================")
        print(screen_text)
        print("========================================")

        response = client.responses.create(
            model=OPENAI_MODEL,

            instructions="""
너는 HaloVision의 저시력 사용자를 돕는 스마트폰 화면 접근성 AI다.

사용자는 현재 화면의 글자가 작거나 정보가 많아서
필요한 내용을 찾기 어렵다.

반드시 현재 화면에 실제로 존재하는 정보만 사용한다.
화면에 없는 내용은 추측하거나 만들어내지 않는다.

답변은 저시력 사용자가 큰 글자 또는 음성으로
빠르게 이해할 수 있도록 짧고 명확한 한국어로 작성한다.
""",

            input=f"""
아래 내용은 Android AccessibilityService가
현재 스마트폰 화면에서 가져온 텍스트다.

[현재 화면 시작]

{screen_text}

[현재 화면 끝]

현재 화면에서 사용자가 알아야 할 중요한 정보만 골라서 알려줘.

다음 정보가 있으면 우선적으로 찾는다.

- 날짜
- 시간
- 장소
- 금액
- 일정
- 마감일
- 해야 할 일
- 상태
- 중요 공지
- 경고
- 준비물
- 연락 내용
- 주문 또는 배송 상태

규칙:

1. 중요하지 않은 메뉴명, 탭 이름, 버튼 이름과
   내비게이션 문구는 가능하면 제외한다.

2. 중요한 내용은 최대 5개까지만 제공한다.

3. 짧고 명확한 문장으로 작성한다.

4. 불필요한 서론과 결론은 쓰지 않는다.

5. Markdown의 *, # 같은 기호는 사용하지 않는다.

6. 중요한 정보를 찾을 수 없다면
   "현재 화면에서 특별히 중요한 정보를 찾지 못했습니다."
   라고 답한다.
""",

            max_output_tokens=500,
            store=False,
        )

        answer = response.output_text.strip()

        if not answer:
            answer = "현재 화면에서 특별히 중요한 정보를 찾지 못했습니다."

        print()
        print("========== HALO IMPORTANT ANSWER ==========")
        print(answer)
        print("===========================================")

        return {
            "success": True,
            "model": OPENAI_MODEL,
            "answer": answer,
        }

    except HTTPException:
        raise

    except Exception as e:
        print()
        print("========== HALO IMPORTANT ERROR ==========")
        traceback.print_exc()
        print("==========================================")

        raise HTTPException(
            status_code=500,
            detail=str(e),
        )


# ============================================================
# 질문하기
#
# 현재 화면 Context + 사용자 질문
# → GPT
# → 현재 화면 기반 답변
# ============================================================

@app.post("/screen/question")
def screen_question(request: ScreenQuestionRequest):

    try:
        screen_text = request.screen_text.strip()
        question = request.question.strip()

        # ----------------------------------------------------
        # 입력 검사
        # ----------------------------------------------------

        if not screen_text:
            raise HTTPException(
                status_code=400,
                detail="현재 화면 텍스트가 비어 있습니다.",
            )

        if not question:
            raise HTTPException(
                status_code=400,
                detail="질문이 비어 있습니다.",
            )

        # ----------------------------------------------------
        # 길이 제한
        # ----------------------------------------------------

        max_screen_chars = 12000
        max_question_chars = 1000

        if len(screen_text) > max_screen_chars:
            screen_text = screen_text[:max_screen_chars]

        if len(question) > max_question_chars:
            question = question[:max_question_chars]

        # ----------------------------------------------------
        # 개발 확인 로그
        # ----------------------------------------------------

        print()
        print("========================================")
        print("HALO SCREEN QUESTION")
        print("========================================")
        print("현재 화면:")
        print(screen_text)
        print()
        print("사용자 질문:")
        print(question)
        print("========================================")

        # ----------------------------------------------------
        # GPT 질문
        # ----------------------------------------------------

        response = client.responses.create(
            model=OPENAI_MODEL,

            instructions="""
너는 HaloVision의 저시력 사용자를 돕는
현재 화면 질의응답 AI다.

사용자는 스마트폰의 현재 화면을 보고 있으며
화면에서 필요한 정보를 찾기 어려워 질문하고 있다.

현재 화면에 제공된 내용을 최우선 근거로 사용한다.

화면에 없는 사실을 추측하거나 만들어내지 않는다.

답을 현재 화면만으로 확인할 수 없다면
그 사실을 명확하게 알려준다.

저시력 사용자가 큰 글자나 음성으로
답변을 확인하므로 짧고 명확하게 답한다.
""",

            input=f"""
[현재 스마트폰 화면]

{screen_text}


[사용자 질문]

{question}


위 현재 화면의 내용을 바탕으로
사용자의 질문에 답해줘.

규칙:

1. 질문에 대한 답부터 바로 말한다.

2. 현재 화면에서 확인할 수 있는 정보만 사용한다.

3. 불필요하게 현재 화면 전체를 요약하지 않는다.

4. 답변은 가능하면 1~3개의 짧은 문장으로 작성한다.

5. 날짜, 시간, 장소, 금액 등은 정확하게 유지한다.

6. Markdown의 *, # 같은 기호는 사용하지 않는다.

7. 화면에서 답을 확인할 수 없다면
   "현재 화면에서는 해당 정보를 확인할 수 없습니다."
   라고 명확하게 답한다.
""",

            max_output_tokens=500,
            store=False,
        )

        answer = response.output_text.strip()

        if not answer:
            answer = "현재 화면에서는 해당 정보를 확인할 수 없습니다."

        print()
        print("========== HALO QUESTION ANSWER ==========")
        print(answer)
        print("==========================================")

        return {
            "success": True,
            "model": OPENAI_MODEL,
            "question": question,
            "answer": answer,
        }

    except HTTPException:
        raise

    except Exception as e:
        print()
        print("========== HALO QUESTION ERROR ==========")
        traceback.print_exc()
        print("=========================================")

        raise HTTPException(
            status_code=500,
            detail=str(e),
        )