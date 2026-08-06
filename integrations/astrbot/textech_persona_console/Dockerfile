FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
ARG PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple
RUN pip install --no-cache-dir --index-url "$PIP_INDEX_URL" -r requirements.txt

COPY app ./app
COPY static ./static

ENV ASTRBOT_DATA=/astrbot-data
ENV CONSOLE_DB=/data/console.db

EXPOSE 6186

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "6186"]
