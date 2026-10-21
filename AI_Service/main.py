"""启动入口：python main.py 等价于 python server.py。

PyCharm 可直接运行本文件；生产部署用：
    uvicorn server:app --host 0.0.0.0 --port 8000
"""
import uvicorn

from config import HOST, PORT

if __name__ == "__main__":
    uvicorn.run("server:app", host=HOST, port=PORT, reload=False)
