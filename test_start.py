import requests

login_resp = requests.post("http://localhost:8080/api/auth/login", json={"email": "admin@bpflow.com", "password": "Admin1234"})
print(f"Login Status: {login_resp.status_code}")
print(f"Login Text: {login_resp.text}")

token = login_resp.json().get("accessToken") if login_resp.status_code == 200 else None

if token:
    wf_resp = requests.get("http://localhost:8080/api/workflows", headers={"Authorization": f"Bearer {token}"})
    print(f"Workflow Status: {wf_resp.status_code}")
    print(f"Workflow Text: {wf_resp.text}")
