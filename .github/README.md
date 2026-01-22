# Required GitHub Configuration
Set up "Environments" in your GitHub Repository Settings.

## Create Environments:
1. Go to Settings -> Environments.
2. Create `staging`.
3. Create `production`.

## Add Secrets (to each environment):
    AWS_ACCESS_KEY_ID: Actual user deployer user key.
    AWS_SECRET_ACCESS_KEY: Actual user deployer secret.
    MONGODB_CONNECTION_STRING: Actual MongoDB connection string (e.g., mongodb+srv://myuser:mypassword@cluster0.abcde.mongodb.net)

## Add Variables (shared for both environments):
    AWS_REGION: us-east-1.
    ECR_REPOSITORY_URL: The output from terraform output ecr_repository_url (e.g., 123456789.dkr.ecr.eu-central-1.amazonaws.com/betgol-receipt-leadgen).