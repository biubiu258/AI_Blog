FROM python:3.12-slim
ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /AI_Service

COPY AI_Service/requirements.txt ./requirements.txt
RUN pip install -r requirements.txt

COPY AI_Service/app ./app
COPY 鲁迅散文风格配置.json ./鲁迅散文风格配置.json

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
