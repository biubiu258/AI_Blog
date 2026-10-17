import uvicorn
from config import Internal_Token
from fastapi import FastAPI, requests, HTTPException
from functools import wraps

app = FastAPI()



@wraps
def auth(func):
    @wraps(func)
    def wrapper(*args, **kwargs):
        token = requests.headers.get('X-Internal-Token')
        if token and token == Internal_Token:
            return func(*args, **kwargs)
        raise HTTPException(status_code=401)
    return wrapper

@app.get("/")
@auth
async def root():
    return {"message": "Hello World"}

if __name__ == "__main__":
    uvicorn.run("server:app", host="0.0.0.0", port=8000, reload=True)