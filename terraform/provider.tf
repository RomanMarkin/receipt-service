terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    mongodbatlas = {
      source  = "mongodb/mongodbatlas"
      version = "~> 2.0.1"
    }
  }

  backend "s3" {
    bucket         = "betgol-receipt-leadgen-terraform-state" # Was created in /terraform/global/backend.tf
    key            = "receipt-service.tfstate"                # Path to file in bucket
    region         = "us-east-1"
    use_lockfile   = true
    encrypt        = true
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

provider "mongodbatlas" {
  public_key  = var.mongodb_atlas_public_key
  private_key = var.mongodb_atlas_private_key
}