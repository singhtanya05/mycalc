#!/bin/bash

# Configuration Variables
IP="15.135.213.23"
KEY_PATH="$HOME/Downloads/mycalc-key.pem"
REMOTE_USER="ubuntu"
JAR_NAME="calc-backend-0.0.1-SNAPSHOT.jar"

echo "🚀 Starting deployment of mycalc to EC2 ($IP)..."

# Ensure the SSH key exists
if [ ! -f eval echo $KEY_PATH ]; then
    # Resolve tilde if needed
    KEY_PATH_EXPANDED=$(eval echo $KEY_PATH)
    if [ ! -f "$KEY_PATH_EXPANDED" ]; then
        echo "❌ SSH Key not found at $KEY_PATH_EXPANDED."
        echo "Please ensure your key is in your Downloads folder or edit this script to update KEY_PATH."
        exit 1
    fi
    KEY_PATH="$KEY_PATH_EXPANDED"
fi

# Set proper permissions for SSH key
chmod 400 "$KEY_PATH"

echo "📦 1. Building React Frontend..."
cd frontend
npm run build
cd ..

echo "📦 2. Building Spring Boot Backend..."
cd backend
mvn clean package -DskipTests
cd ..

echo "🌐 3. Preparing directories on EC2 server..."
ssh -i "$KEY_PATH" -o StrictHostKeyChecking=no "$REMOTE_USER@$IP" "sudo mkdir -p /var/www/mycalc && sudo chown -R ubuntu:ubuntu /var/www/mycalc"

echo "📤 4. Uploading Frontend assets to EC2..."
scp -r -i "$KEY_PATH" frontend/dist/* "$REMOTE_USER@$IP:/var/www/mycalc/"

echo "📤 5. Uploading Backend JAR file..."
scp -i "$KEY_PATH" backend/target/$JAR_NAME "$REMOTE_USER@$IP:/home/ubuntu/mycalc-backend.jar"

echo "⚙️ 6. Setting up Systemd Service and Nginx Proxy..."
# Create the deployment commands script to run on remote host
cat << 'EOF' > remote_setup.sh
#!/bin/bash
set -e

# Update and install system requirements
sudo apt update
sudo apt install -y openjdk-21-jre-headless nginx

# Configure systemd service for Spring Boot Backend
sudo tee /etc/systemd/system/mycalc.service > /dev/null << 'SERVICE_EOF'
[Unit]
Description=mycalc Spring Boot Math Solver Backend
After=syslog.target

[Service]
User=ubuntu
ExecStart=/usr/bin/java -jar /home/ubuntu/mycalc-backend.jar
SuccessExitStatus=143
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
SERVICE_EOF

# Restart and enable mycalc backend service
sudo systemctl daemon-reload
sudo systemctl restart mycalc
sudo systemctl enable mycalc

# Configure Nginx for Frontend serving and API reverse proxy
sudo tee /etc/nginx/sites-available/mycalc > /dev/null << 'NGINX_EOF'
server {
    listen 80;
    server_name _;

    # Serve React Frontend static files
    location / {
        root /var/www/mycalc;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # Proxy API requests to Spring Boot
    location /api/ {
        proxy_pass http://127.0.0.1:8081;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }
}
NGINX_EOF

# Enable the Nginx site and disable default site
if [ -f /etc/nginx/sites-enabled/default ]; then
    sudo rm /etc/nginx/sites-enabled/default
fi
if [ ! -f /etc/nginx/sites-enabled/mycalc ]; then
    sudo ln -s /etc/nginx/sites-available/mycalc /etc/nginx/sites-enabled/
fi

# Verify Nginx and restart
sudo nginx -t
sudo systemctl restart nginx

echo "✅ Remote setup complete!"
EOF

# Upload and execute remote setup script
scp -i "$KEY_PATH" remote_setup.sh "$REMOTE_USER@$IP:/home/ubuntu/remote_setup.sh"
ssh -i "$KEY_PATH" "$REMOTE_USER@$IP" "chmod +x /home/ubuntu/remote_setup.sh && /home/ubuntu/remote_setup.sh && rm /home/ubuntu/remote_setup.sh"
rm remote_setup.sh

echo "🎉 Deployment successful! Visit http://$IP to see your mycalc app running live!"
