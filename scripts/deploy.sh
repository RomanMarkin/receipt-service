#!/bin/bash
set -e # Exit immediately if a command exits with a non-zero status

# 1. Automatically switch to the Project Root directory (allows to run the script from anywhere (e.g. ./scripts/deploy.sh or cd scripts; ./deploy.sh)
cd "$(dirname "$0")/.."

# Validate script arguments
ENV=$1
TAG=$2

if [[ -z "$ENV" || -z "$TAG" ]]; then
  echo "❌ Error: Missing arguments."
  echo "Usage: ./scripts/deploy.sh <staging|prod> <image_tag>"
  echo "Example: ./scripts/deploy.sh staging v1.0.5"
  exit 1
fi

# 3. Validate Environment Config Exists
VAR_FILE="terraform/config/${ENV}.tfvars"

if [[ ! -f "$VAR_FILE" ]]; then
  echo "❌ Error: Configuration file '$VAR_FILE' not found."
  exit 1
fi

echo "🚀 Deploying to environment: $ENV"
echo "📦 Using Docker Image Tag: $TAG"
echo "---------------------------------------------------"

# 4. Check if Terraform is initialized
if [[ ! -d "terraform/.terraform" ]]; then
  echo "⚙️  Terraform not initialized. Running 'terraform init'..."
  terraform -chdir=terraform init
else
  echo "✅ Terraform already initialized. Skipping init."
fi

# 5. Switch to the correct Workspace
echo "---------------------------------------------------"
echo "🔄 Switching to Terraform Workspace: $ENV"
terraform -chdir=terraform workspace select $ENV || terraform -chdir=terraform workspace new $ENV

# 6. Run Terraform Apply
echo "---------------------------------------------------"
terraform -chdir=terraform apply \
  -var-file="../$VAR_FILE" \
  -var="image_tag=$TAG" \
  -var="env=$ENV"

echo "---------------------------------------------------"
echo "✅ Deployment Complete!"