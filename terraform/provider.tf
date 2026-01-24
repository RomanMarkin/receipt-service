terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  backend "s3" {
    bucket         = "betgol-receipt-leadgen-terraform-state" # Was created in /terraform/global/backend.tf
    key            = "receipt-service.tfstate"                # Path to file in bucket
    region         = "us-east-1"
    dynamodb_table = "betgol-receipt-leadgen-terraform-locks" # Was created in /terraform/global/backend.tf
    encrypt        = true
    profile        = "receipt-service-deploy" # Optional: forces usage of specific profile
  }
}

provider "aws" {
  # Take aws_profile from AWS_PROFILE environment variable
  region  = var.aws_region

  # Apply these tags to ALL resources automatically
  default_tags {
    tags = local.common_tags
  }
}