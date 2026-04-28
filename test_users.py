import requests

login = requests.post("http://localhost:8080/api/auth/login", json={"email": "admin@bpflow.com", "password": "Admin1234"})
token = login.json().get("accessToken")
if token:
    users = requests.get("http://localhost:8080/api/users", headers={"Authorization": f"Bearer {token}"})
    print(f"Status: {users.status_code}")
    print(users.text)
else:
    print("Login failed")
