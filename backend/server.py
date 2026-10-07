from fastapi import FastAPI

app = FastAPI(
    title="HaloVision V3 Backend",
    version="0.1.0"
)


@app.get("/")
def root():
    return {
        "message": "HaloVision V3 backend running"
    }


@app.get("/health")
def health():
    return {
        "status": "ok",
        "service": "halovision-v3"
    }