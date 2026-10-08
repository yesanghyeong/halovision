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

client = OpenAI(
    api_key=OPENAI_API_KEY
)


# ============================================================
# FastAPI
# ============================================================

app = FastAPI(
    title="HaloVision V3 Backend",
    version="0.2.0"
)


# ============================================================
# Request Model
# ============================================================

class ScreenImportantRequest(BaseModel):
    screen_text: str


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
        "model": OPENAI_MODEL
    }


# ============================================================
# 현재 화면 → 중요한 내용만
# ============================================================

@app.post("/screen/important")
def screen_important(
    request: ScreenImportantRequest
):

    try:

        # ----------------------------------------------------
        # 입력 정리
        # ----------------------------------------------------

        screen_text = request.screen_text.strip()


        if not screen_text:

            raise HTTPException(
                status_code=400,
                detail="현재 화면 텍스트가 비어 있습니다."
            )


        # 너무 긴 입력 방지
        max_chars = 12000

        if len(screen_text) > max_chars:
            screen_text = screen_text[:max_chars]


        print()
        print("========================================")
        print("HALO SCREEN IMPORTANT REQUEST")
        print("========================================")
        print(screen_text)
        print("========================================")


        # ----------------------------------------------------
        # OpenAI 요청
        # ----------------------------------------------------

        response = client.responses.create(

            model=OPENAI_MODEL,

            instructions="""
너는 HaloVision의 저시력 사용자를 돕는
스마트폰 화면 접근성 AI다.

사용자는 현재 화면의 글자가 작거나
정보가 너무 많아서 필요한 내용을 찾기 어렵다.

반드시 현재 화면에 실제로 존재하는 정보만 사용한다.
화면에 없는 내용은 추측하거나 만들어내지 않는다.

답변은 저시력 사용자가 큰 글자 또는 음성으로
빠르게 이해할 수 있도록 짧고 명확한 한국어로 작성한다.
""",

            input=f"""
아래 내용은 Android AccessibilityService가
현재 스마트폰 화면의 Accessibility Node Tree에서
가져온 텍스트다.

[현재 화면 시작]

{screen_text}

[현재 화면 끝]


위 화면에서 사용자가 지금 알아야 할
중요한 정보만 골라서 알려줘.

다음 항목이 있으면 우선적으로 찾아라.

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

1. 중요하지 않은 메뉴명, 탭 이름, 버튼 이름,
   내비게이션 문구는 가능하면 제외한다.

2. 중요한 내용은 최대 5개까지만 제공한다.

3. 각 내용은 짧은 문장으로 작성한다.

4. 불필요한 서론이나 결론을 쓰지 않는다.

5. Markdown의 *, # 같은 기호는 사용하지 않는다.

6. 화면에서 중요한 정보를 명확히 찾을 수 없다면
   "현재 화면에서 특별히 중요한 정보를 찾지 못했습니다."
   라고 답한다.
""",

            max_output_tokens=500
        )


        # Responses API에서는 output_text로
        # 최종 텍스트를 편리하게 가져올 수 있음
        answer = response.output_text.strip()


        if not answer:

            answer = (
                "현재 화면에서 특별히 중요한 정보를 "
                "찾지 못했습니다."
            )


        print()
        print("========== HALO AI ANSWER ==========")
        print(answer)
        print("====================================")


        # 사용량도 개발 중 확인
        if response.usage:

            print(
                f"Input tokens: "
                f"{response.usage.input_tokens}"
            )

            print(
                f"Output tokens: "
                f"{response.usage.output_tokens}"
            )

            print(
                f"Total tokens: "
                f"{response.usage.total_tokens}"
            )


        return {
            "success": True,
            "model": OPENAI_MODEL,
            "answer": answer
        }


    # --------------------------------------------------------
    # FastAPI 오류
    # --------------------------------------------------------

    except HTTPException:
        raise


    # --------------------------------------------------------
    # 기타 오류
    # --------------------------------------------------------

    except Exception as e:

        print()
        print("========== HALO AI ERROR ==========")

        traceback.print_exc()

        print("===================================")


        raise HTTPException(
            status_code=500,
            detail=str(e)
        )