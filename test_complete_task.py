import requests, json

login = requests.post('http://localhost:8080/api/auth/login', json={'email': 'admin@bpflow.com', 'password': 'Admin1234'}).json()
token = login['accessToken']
h = {'Authorization': f'Bearer {token}'}
inst_id = '69f006ad28e5ef06e0bca5d2'

# Check instance detail
inst = requests.get(f'http://localhost:8080/api/instances/{inst_id}', headers=h).json()
print('Estado:', inst.get('status'))
print('Nodo activo:', inst.get('currentNodeId'))

# Check all tasks and filter manually
all_t = requests.get('http://localhost:8080/api/tasks', headers=h).json()
instance_tasks = [t for t in all_t if t.get('workflowInstanceId') == inst_id]
print(f'Tareas encontradas: {len(instance_tasks)}')
for t in instance_tasks:
    tid = t.get('id')
    ttitle = t.get('title')
    tstatus = t.get('status')
    trole = t.get('assignedRole')
    print(f'  Tarea: {ttitle} | Status: {tstatus} | Rol: {trole} | ID: {tid}')
    
    # Complete the task if pending
    if tstatus == 'PENDING':
        complete = requests.post(
            f'http://localhost:8080/api/tasks/{tid}/complete', 
            headers=h, 
            json={'action': 'APPROVE', 'comment': 'Aprobado en prueba E2E'}
        )
        print(f'    -> Completar: {complete.status_code} | {complete.text[:200]}')

# Final state check
inst2 = requests.get(f'http://localhost:8080/api/instances/{inst_id}', headers=h).json()
print(f'\nEstado final instancia: {inst2.get("status")}')
print(f'Historial de pasos: {len(inst2.get("history", []))}')
for h_entry in inst2.get('history', []):
    print(f'  - {h_entry.get("nodeName")} | {h_entry.get("action")}')
