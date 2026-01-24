#!/bin/bash
set -e # Exit immediately if a command exits with a non-zero status

# 1. Automatically switch to the Project Root directory
cd "$(dirname "$0")/.."

# Argument Parsing: handles flags (like --profile) mixed with positional args
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

# Restore positional arguments (ENV and TAG)
set -- "${POSITIONAL_ARGS[@]}"

# Assign Variables from positional arguments
ENV=$1
TAG=$2
# ------------------------------------

# Validate required arguments
if [[ -z "$ENV" || -z "$TAG" ]]; then
  echo "❌ Error: Missing arguments."
  echo "Usage: ./scripts/deploy.sh <staging|prod> <image_tag> [options]"
  echo "Example: ./scripts/deploy.sh staging v1.0.5 --profile receipt-service-deploy"
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

# Check if AWS_PROFILE is set (either by flag or pre-existing env var)
if [[ -n "$AWS_PROFILE" ]]; then
    echo "👤 Using AWS Profile: $AWS_PROFILE"
else
    echo "👤 Using default AWS credentials (no specific profile set)"
fi
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
# The || operator ensures that if select fails, new is run, without triggering 'set -e'
terraform -chdir=terraform workspace select $ENV || terraform -chdir=terraform workspace new $ENV

# 6. Run Terraform Apply
echo "---------------------------------------------------"
# Terraform automatically picks up the exported AWS_PROFILE environment variable.
CMD="terraform -chdir=terraform apply \
  -auto-approve \
  -var-file="../$VAR_FILE" \
  -var="image_tag=$TAG" \
  -var="env=$ENV""

# Check for local secret file (in case we run script manually from local machine)
SECRET_FILE="terraform/secret.tfvars"
if [[ -f "$SECRET_FILE" ]]; then
  echo "🔓 Found local secrets file ($SECRET_FILE). Including it..."
  CMD="$CMD -var-file="../$SECRET_FILE""
else
  echo "🔒 No local secrets file found. Relying on TF_VAR_ env vars (CI/CD mode)."
fi

# Execute command
eval $CMD
echo "---------------------------------------------------"
echo "✅ Deployment Complete!"