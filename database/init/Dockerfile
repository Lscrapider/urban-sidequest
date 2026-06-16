FROM python:3.12-alpine

RUN apk add --no-cache postgresql16-client

WORKDIR /app

COPY database/init_database.py /app/database/init_database.py
COPY database/migrations /app/database/migrations

CMD ["python3", "/app/database/init_database.py"]
