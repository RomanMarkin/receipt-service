# Infrastructure Provisioning in AWS

This directory contains the Terraform code to provision the infrastructure for the **Receipt Service** (VPC, ECS Cluster, ALB, and Fargate Services).

## 📋 Prerequisites

### 1. Install Tools
Ensure you have the following installed:
* [Terraform](https://developer.hashicorp.com/terraform/downloads) (v1.0+)
* [AWS CLI](https://aws.amazon.com/cli/)

### 2. Configure AWS Profile
Create an AWS profile named `receipt-service-deploy`. This profile is referenced in the scripts to authenticate the deployment.

    aws configure --profile receipt-service-deploy

### 3. Add permissions to the AWS deployment user
IAM -> Policies -> Create Policy -> JSON. Name it `ReceiptServiceDeployerPolicy` and save.
Use `policies/receipt-service-deployer.json` file.

IAM -> Users -> (select user) -> Permissions tab -> Add permissions -> Attach policies directly.
Search for and select `ReceiptServiceDeployerPolicy`.

---

## 🌍 Step 1: Global Infrastructure (One-Time Setup)

**⚠️ Critical First Step:** You must run this **before** initializing the main Terraform project.

This script provisions the shared resources used by all environments:
1.  **ECR Repository:** Stores Docker images (`betgol-receipt-leadgen`).
2.  **Terraform Backend:** Creates the S3 Bucket and DynamoDB Table used to store and lock the remote state.

**Run the Global Deploy Script:**

    ./scripts/deploy-global.sh --profile receipt-service-deploy

* **When to run:** Only once when setting up the project from scratch.

---

## ⚙️ Step 2: Initialize Application Terraform

Once the Global Infrastructure (S3 Bucket) exists, you must initialize the application Terraform to connect to it.

    cd terraform
    terraform init
    cd ..

* *Note: If you have a local state file, Terraform will ask if you want to migrate your state to S3. Answer **yes**.*

---

## 🛠 Managing Environments (Workspaces)

We use **Terraform Workspaces** combined with the **S3 Remote Backend** to separate data for `staging` and `prod`.

**Check current workspace:**

    cd terraform
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

## 🚀 Application Deployment

You can deploy using the helper script (recommended) or manual commands.

### Option A: Using the Helper Script (Recommended)
We have a script in the `scripts/` folder that handles authentication, secrets, and workspace selection for you.

**Syntax:**

    # From the project root
    ./scripts/deploy.sh <environment> <image_tag> --profile <aws_profile_name>

**Example (Staging):**

    ./scripts/deploy.sh staging v1.0.0 --profile receipt-service-deploy

### Option B: Manual Deployment
If you need to debug or run specific flags, use the raw Terraform commands.

**1. Select Workspace:**

    cd terraform
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

**⚠️ Warning:** This will permanently delete all application resources (Load Balancers, Networking, Clusters). It will **not** delete the Global Infrastructure (ECR/S3) which is protected by deletion locks.

### Option 1: Destroy using the script (Recommended)

    ./scripts/destroy.sh <prod|staging> --profile <aws_profile_name>

#### Example (staging):

    ./scripts/destroy.sh staging --profile receipt-service-deploy

### Option 2: Destroy manually
1.  **Select the correct workspace:**

        cd terraform
        terraform workspace select staging

2. **Run Destroy:**
   You must provide the variables (even `image_tag`, though it won't be used) to satisfy Terraform's validation.

        AWS_PROFILE=receipt-service-deploy terraform destroy \
        -var-file="config/staging.tfvars" \
        -var-file="secret.tfvars" \
        -var="image_tag=v0.0.1" \
        -var="env=staging"