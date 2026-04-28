import requests
import json

BASE = "http://localhost:8080"

# 1. Login
login = requests.post(f"{BASE}/api/auth/login", json={"email": "admin@bpflow.com", "password": "Admin1234"})
token = login.json().get("accessToken")
headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
print(f"✅ Login OK - Token: {token[:30]}...")

# 2. Create workflow
wf = requests.post(f"{BASE}/api/workflows", headers=headers, json={
    "name": "Flujo Simple API",
    "description": "Prueba de flujo: Inicio → Revision → Aprobado",
    "category": "Testing",
    "defaultSlaHours": 24
})
wf_id = wf.json()["id"]
print(f"✅ Workflow creado: {wf_id}")

# 3. Add START node
n1 = requests.post(f"{BASE}/api/workflows/{wf_id}/nodes", headers=headers, json={
    "label": "Inicio", "type": "START", "x": 200, "y": 300,
    "startNode": True, "endNode": False, "slaHours": 0
})
n1_id = n1.json()["nodes"][-1]["id"]
print(f"✅ Nodo START añadido: {n1_id}")

# 4. Add TASK node
n2 = requests.post(f"{BASE}/api/workflows/{wf_id}/nodes", headers=headers, json={
    "label": "Revision", "type": "TASK", "x": 500, "y": 300,
    "startNode": False, "endNode": False, "slaHours": 24,
    "assignedRole": "MANAGER",
    "description": "El gerente debe revisar y aprobar la solicitud"
})
n2_id = n2.json()["nodes"][-1]["id"]
print(f"✅ Nodo TASK añadido: {n2_id}")

# 5. Add END node
n3 = requests.post(f"{BASE}/api/workflows/{wf_id}/nodes", headers=headers, json={
    "label": "Aprobado", "type": "END", "x": 800, "y": 300,
    "startNode": False, "endNode": True, "slaHours": 0
})
n3_id = n3.json()["nodes"][-1]["id"]
print(f"✅ Nodo END añadido: {n3_id}")

# 6. Connect nodes
e1 = requests.post(f"{BASE}/api/workflows/{wf_id}/edges", headers=headers, json={
    "sourceNodeId": n1_id, "targetNodeId": n2_id, "type": "SEQUENCE"
})
print(f"✅ Conexión Inicio→Revision: {e1.status_code}")

e2 = requests.post(f"{BASE}/api/workflows/{wf_id}/edges", headers=headers, json={
    "sourceNodeId": n2_id, "targetNodeId": n3_id, "type": "SEQUENCE"
})
print(f"✅ Conexión Revision→Aprobado: {e2.status_code}")

# 7. Publish
pub = requests.post(f"{BASE}/api/workflows/{wf_id}/publish", headers=headers)
print(f"✅ Publicar: {pub.status_code} - Status: {pub.json().get('status')}")

# 8. Start instance
inst = requests.post(f"{BASE}/api/instances/start", headers=headers, json={
    "workflowId": wf_id,
    "referenceNumber": "TEST-001",
    "priority": "NORMAL",
    "variables": {"tipo": "prueba"}
})
print(f"\n🚀 Instancia iniciada: {inst.status_code}")
inst_data = inst.json()
print(json.dumps(inst_data, indent=2, default=str))
inst_id = inst_data.get("id")

# 9. Check tasks created
if inst_id:
    tasks = requests.get(f"{BASE}/api/tasks", headers=headers)
    all_tasks = tasks.json()
    my_tasks = [t for t in all_tasks if t.get("workflowInstanceId") == inst_id]
    print(f"\n📋 Tareas de esta instancia: {len(my_tasks)}")
    for t in my_tasks:
        print(f"  - {t.get('title')} | Estado: {t.get('status')} | Asignado: {t.get('assignedRole')}")
    
    # 10. Complete first task (approve)
    if my_tasks:
        task_id = my_tasks[0]["id"]
        complete = requests.post(f"{BASE}/api/tasks/{task_id}/complete", headers=headers, 
                                  json={"action": "APPROVE", "comment": "Aprobado en prueba"})
        print(f"\n✅ Completar tarea '{my_tasks[0]['title']}': {complete.status_code}")
        print(complete.text)
        
        # 11. Check instance state after completing task
        inst_check = requests.get(f"{BASE}/api/instances/{inst_id}", headers=headers)
        print(f"\n🔍 Estado final instancia: {inst_check.json().get('status')}")
