terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
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