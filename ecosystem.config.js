module.exports = {
  apps: [
    {
      name: "bpflow-backend",
      script: "java",
      args: "-jar bpflow-backend/target/bpflow-backend-1.0.0-SNAPSHOT.jar",
      cwd: "./",
      env: {
        SPRING_DATA_MONGODB_URI: "mongodb://bpflow:bpflow_secret_2024@localhost:27017/bpflow_db?authSource=admin",
        SPRING_DATA_MONGODB_DATABASE: "bpflow_db",
        JWT_SECRET: "BPFlow_JWT_Super_Secret_Key_2024_Change_In_Prod_256bits",
        JWT_EXPIRATION_MS: "86400000",
        JWT_REFRESH_EXPIRATION_MS: "604800000",
        AI_SERVICE_URL: "http://localhost:8000",
        CORS_ALLOWED_ORIGINS: "http://localhost,http://localhost:4200"
      }
    },
    {
      name: "bpflow-ai",
      script: "uvicorn",
      args: "app.main:app --host 0.0.0.0 --port 8000",
      cwd: "./bpflow-ai",
      interpreter: "python3",
      env: {
        MONGODB_URI: "mongodb://bpflow:bpflow_secret_2024@localhost:27017/bpflow_db?authSource=admin",
        ENV: "production"
      }
    },
    {
      name: "bpflow-frontend",
      script: "npx",
      args: "serve -s dist/bpflow-frontend -l 4200",
      cwd: "./bpflow-frontend",
      env: {
        NODE_ENV: "production"
      }
    }
  ]
};
