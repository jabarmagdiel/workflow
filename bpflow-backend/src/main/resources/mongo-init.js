db = db.getSiblingDB('admin');
db.auth('bpflow', 'bpflow_secret_2024');

db = db.getSiblingDB('bpflow_db');

db.createUser({
    user: 'bpflow',
    pwd: 'bpflow_secret_2024',
    roles: [
        { role: 'readWrite', db: 'bpflow_db' },
        { role: 'dbAdmin', db: 'bpflow_db' }
    ]
});

// Crear colecciones iniciales
db.createCollection('users');
db.createCollection('workflows');
db.createCollection('workflow_instances');
db.createCollection('tasks');
db.createCollection('fraud_alerts');
db.createCollection('audit_logs');

print('✅ Base de datos BPFlow inicializada correctamente');
