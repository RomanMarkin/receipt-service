#!/bin/bash
set -e # Exit immediately if any command fails

# 1. Automatically switch to the Global Terraform directory
cd "$(dirname "$0")/../terraform/global"

# 2. Argument Parsing (for AWS Profile)
POSITIONAL_ARGS=()

while [[ "$#" -gt 0 ]]; do
  case $1 in
    -p|--profile)
      echo "🔑 Setting AWS Profile to: $2"
      export AWS_PROFILE="$2"
      shift # past argument
      shift # past value
      ;;
    *)
      POSITIONAL_ARGS+=("$1") # save positional arg
      shift # past argument
      ;;
  esac
done

echo "---------------------------------------------------"
echo "🌍 Deploying GLOBAL Infrastructure (ECR Repo)"
echo "📂 Working Directory: $(pwd)"
if [[ -n "$AWS_PROFILE" ]]; then
    echo "👤 Using AWS Profile: $AWS_PROFILE"
else
    echo "👤 Using default AWS credentials"
fi
echo "---------------------------------------------------"

# 3. Check for Provider Config
if [[ ! -f "provider.tf" ]]; then
  echo "❌ Error: provider.tf not found in terraform/global!"
  echo "   Please create it before running this script."
  exit 1
fi

# 4. Terraform Init
echo "⚙️  Initializing Terraform..."
terraform init

# 5. Terraform Apply
echo "---------------------------------------------------"
echo "🚀 Applying Global Infrastructure..."
echo "   (This will create the ECR repository if it doesn't exist)"
terraform apply

echo "---------------------------------------------------"
echo "✅ Global Deployment Complete!"