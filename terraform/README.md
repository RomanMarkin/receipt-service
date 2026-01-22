# Infrastructure Provisioning in AWS

This directory contains the Terraform code to provision the infrastructure for the **Receipt Service** (VPC, ECS Cluster, ALB, and Fargate Services).

## 📋 Prerequisites

### 1. Install Tools
Ensure you have the following installed:
* [Terraform](https://developer.hashicorp.com/terraform/downloads) (v1.0+)
* [AWS CLI](https://aws.amazon.com/cli/)

### 2. Configure AWS Profile
Create an AWS profile named `receipt-service-deploy`. This profile is referenced in the `provider.tf` or `*.tfvars` files to authenticate the deployment.

    aws configure --profile receipt-service-deploy

### 3. Add permissions to the AWS deployment user
IAM -> Policies -> Create Policy -> JSON. Name it 'ReceiptServiceDeployerPolicy' and save.
Use `policies/receipt-service-deployer.json` file.

IAM -> Users -> (select user) -> Permissions tab -> Add permissions -> Attach policies directly.
Search for and select 'ReceiptServiceDeployerPolicy'.


### 4. Initialize Terraform
Before running any commands, initialize the project to download providers and modules.

    terraform init

---

## 🛠 Managing Environments (Workspaces)

We use **Terraform Workspaces** to separate the state files for `staging` and `prod`. **You must switch to the correct workspace before deploying.**

**Check current workspace:**

    terraform workspace show

**Create/Select Staging:**
# Create if it doesn't exist 
    terraform workspace new staging

# Switch to it
    terraform workspace select staging

**Create/Select Production:**

    terraform workspace new prod
    terraform workspace select prod

---

## 🚀 Deployment

You can deploy using the helper script (recommended) or manual commands.

### Option A: Using the Helper Script (Recommended)
We have a script in the root `scripts/` folder that handles the arguments for you.

**Syntax:**
# From the project root
./scripts/deploy.sh <environment> <image_tag> --profile <aws_profile_name>

**Example (Staging):**
 
    ./scripts/deploy.sh staging v1.0.0 --profile receipt-service-deploy

### Option B: Manual Deployment
If you need to debug or run specific flags, use the raw Terraform commands.

**1. Select Workspace:**

    terraform workspace select staging

**2. Plan (Dry Run):**

    terraform plan \
    -var-file="config/staging.tfvars" \
    -var="image_tag=v1.0.0"

**3. Apply (Deploy):**

    terraform apply \
    -var-file="config/staging.tfvars" \
    -var="image_tag=v1.0.0"

---

## 🧨 Destroying Infrastructure

**⚠️ Warning:** This will permanently delete all resources (Load Balancers, Networking, Clusters).

To destroy an environment:

1.  **Select the correct workspace:**
     
        terraform workspace select staging

2. **Run Destroy:**
    You must provide the variables (even `app_image`, though it won't be used) to satisfy Terraform's validation.
  
        AWS_PROFILE=receipt-service-deploy terraform destroy \
        -var-file="config/staging.tfvars" \
        -var="image_tag=ignore_me" \
        -var="env=staging"