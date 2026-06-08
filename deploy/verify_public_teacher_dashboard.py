import json
import urllib.request

BASE = "http://127.0.0.1"


def post_json(path, payload):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        return resp.status, json.loads(resp.read().decode("utf-8"))


def get_json(path, token):
    req = urllib.request.Request(
        BASE + path,
        headers={"Authorization": "Bearer " + token},
        method="GET",
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        return resp.status, json.loads(resp.read().decode("utf-8"))


login_status, login_body = post_json(
    "/api/auth/login",
    {"username": "teacher1", "password": "123456"},
)
token = (login_body.get("data") or {}).get("token")
print("teacher_login_http", login_status)
print("teacher_login_code", login_body.get("code"))
print("teacher_token_present", bool(token))

dashboard_status, dashboard_body = get_json("/api/essays/teacher-dashboard", token)
data = dashboard_body.get("data") or {}
print("teacher_dashboard_http", dashboard_status)
print("teacher_dashboard_code", dashboard_body.get("code"))
print("students", data.get("studentCount"))
print("assignments", data.get("assignmentCount"))
print("submitted", data.get("submittedCount"))
print("weak_points", len(data.get("weakKnowledgePoints") or []))
print("recommendations", len(data.get("lessonRecommendations") or []))
