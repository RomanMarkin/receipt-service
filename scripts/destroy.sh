#!/bin/bash
set -e

# 1. Switch to Project Root
cd "$(dirname "$0")/.."

# 2. Argument Parsing
POSITIONAL_ARGS=()
while [[ "$#" -gt 0 ]]; do
  case $1 in
    -p|--profile) export AWS_PROFILE="$2"; shift; shift ;;
    *) POSITIONAL_ARGS+=("$1"); shift ;;
  esac
done
set -- "${POSITIONAL_ARGS[@]}"
ENV=$1

if [[ -z "$ENV" ]]; then
  echo "Usage: ./scripts/destroy.sh <staging|prod> [--profile name]"
  exit 1
fi

echo "⚠️  WARNING: Destroying environment: $ENV"
if [[ -n "$AWS_PROFILE" ]]; then echo "👤 Profile: $AWS_PROFILE"; fi

# 3. Construct Terraform Command
CMD="terraform -chdir=terraform destroy \
  -var-file="../terraform/config/${ENV}.tfvars" \
  -var="image_tag=destroying" \
  -var="env=$ENV""

# 4. Check for Local Secrets
SECRET_FILE="terraform/secret.tfvars"
if [[ -f "$SECRET_FILE" ]]; then
  echo "🔓 Including local secrets from $SECRET_FILE"
  CMD="$CMD -var-file="../$SECRET_FILE""
fi

# 5. Confirmation & Execution
read -p "Are you sure? (Type 'yes' to confirm): " CONFIRM
if [[ "$CONFIRM" == "yes" ]]; then
  terraform -chdir=terraform workspace select $ENV
  eval $CMD
else
  echo "❌ Cancelled."
fi