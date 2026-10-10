import json
import os
import traceback
from typing import Optional

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from openai import OpenAI
from pydantic import BaseModel


# ============================================================
# 환경 변수
# ============================================================

load_dotenv()

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")

if not OPENAI_API_KEY:
    raise RuntimeError(
        "OPENAI_API_KEY가 .env 파일에 없습니다."
    )


MODEL_NAME = os.getenv(
    "OPENAI_MODEL",
    "gpt-6-luna",
)


client = OpenAI(
    api_key=OPENAI_API_KEY
)


# ============================================================
# FastAPI
# ============================================================

app = FastAPI(
    title="HaloVision V3 Backend",
    version="3.1.0",
)


# ============================================================
# Request Models
# ============================================================

class ScreenImportantRequest(BaseModel):
    screen_text: str = ""
    image_base64: Optional[str] = None


class ScreenQuestionRequest(BaseModel):
    screen_text: str = ""
    question: str
    image_base64: Optional[str] = None


# ============================================================
# Structured Output Schema
# ============================================================

SCREEN_DECISION_FORMAT = {
    "type": "json_schema",
    "name": "halo_screen_decision",
    "strict": True,
    "schema": {
        "type": "object",
        "properties": {
            "needs_vision": {
                "type": "boolean"
            },
            "answer": {
                "type": "string"
            },
            "reason": {
                "type": "string"
            },
        },
        "required": [
            "needs_vision",
            "answer",
            "reason",
        ],
        "additionalProperties": False,
    },
}


# ============================================================
# 공통 Helper
# ============================================================

def make_image_data_url(
    image_base64: str
) -> str:

    image_base64 = image_base64.strip()

    if image_base64.startswith(
        "data:image/"
    ):
        return image_base64

    return (
        "data:image/jpeg;base64,"
        + image_base64
    )


def parse_decision(
    response
) -> dict:

    raw = (
        response.output_text
        or ""
    ).strip()

    if not raw:
        raise RuntimeError(
            "GPT 응답이 비어 있습니다."
        )

    data = json.loads(raw)

    return {
        "needs_vision": bool(
            data["needs_vision"]
        ),
        "answer": str(
            data["answer"]
        ).strip(),
        "reason": str(
            data["reason"]
        ).strip(),
    }


# ============================================================
# Health
# ============================================================

@app.get("/health")
def health():

    return {
        "success": True,
        "status": "ok",
        "model": MODEL_NAME,
        "version": "3.1.0",
    }


# ============================================================
# 중요한 내용만
# ============================================================

@app.post("/screen/important")
def analyze_important_screen(
    request: ScreenImportantRequest,
):

    screen_text = (
        request.screen_text
        or ""
    ).strip()

    image_base64 = (
        request.image_base64
        or ""
    ).strip()


    # ========================================================
    # Node Tree조차 없고 이미지도 아직 없는 경우
    #
    # GPT 호출도 할 필요 없이 바로 Vision 요청
    # ========================================================

    if (
        not screen_text
        and not image_base64
    ):

        return {
            "success": True,
            "model": MODEL_NAME,
            "needs_vision": True,
            "answer": "",
            "reason": (
                "Accessibility Node Tree에서 "
                "화면 정보를 얻지 못했습니다."
            ),
        }


    try:

        # ====================================================
        # 1차: Node Tree만 있음
        # ====================================================

        if not image_base64:

            response = client.responses.create(

                model=MODEL_NAME,

                store=False,

                text={
                    "format":
                        SCREEN_DECISION_FORMAT
                },

                input=[
                    {
                        "role": "system",
                        "content": [
                            {
                                "type": "input_text",
                                "text": (
                                    "너는 저시력 사용자를 위한 "
                                    "HaloVision 화면 분석 AI다. "

                                    "현재 제공된 정보는 Android "
                                    "Accessibility Node Tree에서 "
                                    "가져온 텍스트다. "

                                    "이 정보만으로 사용자가 실제로 "
                                    "보고 있는 화면의 핵심 내용을 "
                                    "정확히 설명할 수 있는지 먼저 판단한다. "

                                    "사진, 카메라 미리보기, 스캔 문서, "
                                    "그래프, 차트, Canvas, 지도 등 "
                                    "시각적 내용이 핵심일 가능성이 있고 "
                                    "Node Tree에는 메뉴나 버튼 정보만 "
                                    "있는 경우 needs_vision을 true로 한다. "

                                    "단순히 글자가 적다는 이유만으로 "
                                    "true로 하지 않는다. "
                                    "Node Tree에 실제 핵심 내용이 "
                                    "충분히 들어 있으면 false로 한다. "

                                    "needs_vision이 false라면 "
                                    "중요한 내용만 짧고 명확한 "
                                    "한국어로 answer에 작성한다. "

                                    "needs_vision이 true라면 "
                                    "answer는 빈 문자열로 두고 "
                                    "reason에 이미지가 필요한 이유를 "
                                    "짧게 작성한다. "

                                    "화면에 없는 내용은 추측하지 않는다."
                                ),
                            }
                        ],
                    },

                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "input_text",
                                "text": (
                                    "현재 화면의 Node Tree 텍스트:\n\n"
                                    f"{screen_text}\n\n"
                                    "이 정보만으로 현재 화면의 "
                                    "중요한 내용을 정확하게 설명할 수 있는지 "
                                    "판단하고 결과를 반환해."
                                ),
                            }
                        ],
                    },
                ],
            )


        # ====================================================
        # 2차: Screenshot까지 있음
        # ====================================================

        else:

            image_data_url = (
                make_image_data_url(
                    image_base64
                )
            )


            response = client.responses.create(

                model=MODEL_NAME,

                store=False,

                text={
                    "format":
                        SCREEN_DECISION_FORMAT
                },

                input=[
                    {
                        "role": "system",
                        "content": [
                            {
                                "type": "input_text",
                                "text": (
                                    "너는 저시력 사용자를 위한 "
                                    "HaloVision 화면 분석 AI다. "

                                    "Android Node Tree와 현재 화면 "
                                    "스크린샷을 함께 분석한다. "

                                    "스크린샷이 이미 제공되었으므로 "
                                    "needs_vision은 반드시 false로 한다. "

                                    "사진이나 카메라 화면 안의 글자, "
                                    "스캔 문서, 표, 그래프, 도식 등 "
                                    "Node Tree에서 빠진 시각 정보도 "
                                    "직접 확인한다. "

                                    "사용자가 실제로 알아야 할 "
                                    "핵심 내용을 answer에 짧고 "
                                    "명확한 한국어로 작성한다. "

                                    "단순 앱 메뉴보다 실제 콘텐츠를 "
                                    "우선한다. "

                                    "화면에서 확인되지 않는 내용은 "
                                    "추측하지 않는다. "

                                    "reason에는 '이미지 분석 완료'라고 쓴다."
                                ),
                            }
                        ],
                    },

                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "input_text",
                                "text": (
                                    "Node Tree 텍스트:\n\n"
                                    f"{screen_text}\n\n"
                                    "현재 화면 이미지까지 함께 보고 "
                                    "사용자가 꼭 알아야 하는 "
                                    "중요한 내용만 정리해."
                                ),
                            },

                            {
                                "type": "input_image",
                                "image_url":
                                    image_data_url,
                                "detail":
                                    "high",
                            },
                        ],
                    },
                ],
            )


        result = parse_decision(
            response
        )


        print(
            "===== SCREEN IMPORTANT ====="
        )

        print(
            f"vision supplied = {bool(image_base64)}"
        )

        print(
            f"needs vision = {result['needs_vision']}"
        )

        print(
            f"reason = {result['reason']}"
        )

        print(
            "============================"
        )


        return {
            "success": True,
            "model": MODEL_NAME,
            **result,
        }


    except Exception as e:

        print(
            "===== /screen/important ERROR ====="
        )

        traceback.print_exc()

        print(
            "==================================="
        )


        raise HTTPException(
            status_code=500,
            detail=str(e),
        )


# ============================================================
# 질문하기
# ============================================================

@app.post("/screen/question")
def ask_screen_question(
    request: ScreenQuestionRequest,
):

    screen_text = (
        request.screen_text
        or ""
    ).strip()


    question = (
        request.question
        or ""
    ).strip()


    image_base64 = (
        request.image_base64
        or ""
    ).strip()


    if not question:

        raise HTTPException(
            status_code=400,
            detail="question이 비어 있습니다.",
        )


    # ========================================================
    # Node Tree 자체가 없음
    # ========================================================

    if (
        not screen_text
        and not image_base64
    ):

        return {
            "success": True,
            "model": MODEL_NAME,
            "question": question,
            "needs_vision": True,
            "answer": "",
            "reason": (
                "Accessibility Node Tree에서 "
                "질문에 사용할 화면 정보를 얻지 못했습니다."
            ),
        }


    try:

        # ====================================================
        # 1차: Node Tree로 질문 해결 시도
        # ====================================================

        if not image_base64:

            response = client.responses.create(

                model=MODEL_NAME,

                store=False,

                text={
                    "format":
                        SCREEN_DECISION_FORMAT
                },

                input=[
                    {
                        "role": "system",
                        "content": [
                            {
                                "type": "input_text",
                                "text": (
                                    "너는 저시력 사용자를 위한 "
                                    "HaloVision 화면 질의응답 AI다. "

                                    "현재 제공된 화면 정보는 "
                                    "Android Accessibility Node Tree의 "
                                    "텍스트다. "

                                    "먼저 이 정보만으로 사용자의 질문에 "
                                    "정확히 답할 수 있는지 판단한다. "

                                    "질문의 답이 사진, 카메라 미리보기, "
                                    "스캔 문서, 그래프, 지도, 이미지 속 글자 "
                                    "등 시각 정보에 의존하고 "
                                    "Node Tree에는 그 내용이 없다면 "
                                    "needs_vision을 true로 한다. "

                                    "Node Tree에 질문의 답이 명확히 있다면 "
                                    "needs_vision을 false로 하고 "
                                    "answer에 한국어 1~3문장으로 답한다. "

                                    "needs_vision이 true인 경우 "
                                    "answer는 빈 문자열로 둔다. "

                                    "화면에 없는 내용은 추측하지 않는다."
                                ),
                            }
                        ],
                    },

                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "input_text",
                                "text": (
                                    "현재 화면 Node Tree:\n\n"
                                    f"{screen_text}\n\n"
                                    "사용자 질문:\n"
                                    f"{question}\n\n"
                                    "이 Node Tree만으로 정확히 "
                                    "답할 수 있는지 판단해."
                                ),
                            }
                        ],
                    },
                ],
            )


        # ====================================================
        # 2차: Screenshot 포함 질문
        # ====================================================

        else:

            image_data_url = (
                make_image_data_url(
                    image_base64
                )
            )


            response = client.responses.create(

                model=MODEL_NAME,

                store=False,

                text={
                    "format":
                        SCREEN_DECISION_FORMAT
                },

                input=[
                    {
                        "role": "system",
                        "content": [
                            {
                                "type": "input_text",
                                "text": (
                                    "너는 저시력 사용자를 위한 "
                                    "HaloVision 화면 질의응답 AI다. "

                                    "Android Node Tree와 현재 화면 "
                                    "스크린샷이 함께 제공된다. "

                                    "스크린샷이 제공되었으므로 "
                                    "needs_vision은 반드시 false로 한다. "

                                    "Node Tree뿐 아니라 사진 안의 글자, "
                                    "카메라가 비추는 내용, 스캔 문서, "
                                    "표, 그래프, 지도 등 실제 화면의 "
                                    "시각 정보도 확인해서 질문에 답한다. "

                                    "답변은 한국어 1~3문장으로 "
                                    "짧고 명확하게 한다. "

                                    "화면에서 확인할 수 없는 내용은 "
                                    "'현재 화면에서는 해당 정보를 "
                                    "확인할 수 없습니다.'라고 답한다. "

                                    "추측하지 않는다. "

                                    "reason에는 '이미지 분석 완료'라고 쓴다."
                                ),
                            }
                        ],
                    },

                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "input_text",
                                "text": (
                                    "현재 화면 Node Tree:\n\n"
                                    f"{screen_text}\n\n"
                                    "사용자 질문:\n"
                                    f"{question}"
                                ),
                            },

                            {
                                "type": "input_image",
                                "image_url":
                                    image_data_url,
                                "detail":
                                    "high",
                            },
                        ],
                    },
                ],
            )


        result = parse_decision(
            response
        )


        print(
            "===== SCREEN QUESTION ====="
        )

        print(
            f"question = {question}"
        )

        print(
            f"vision supplied = {bool(image_base64)}"
        )

        print(
            f"needs vision = {result['needs_vision']}"
        )

        print(
            f"reason = {result['reason']}"
        )

        print(
            "==========================="
        )


        return {
            "success": True,
            "model": MODEL_NAME,
            "question": question,
            **result,
        }


    except Exception as e:

        print(
            "===== /screen/question ERROR ====="
        )

        traceback.print_exc()

        print(
            "=================================="
        )


        raise HTTPException(
            status_code=500,
            detail=str(e),
        )