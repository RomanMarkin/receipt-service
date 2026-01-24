# 1. S3 Bucket to keep Terraform state files
resource "aws_s3_bucket" "terraform_state" {
  bucket = "betgol-receipt-leadgen-terraform-state" # Must be globally unique

  # Prevent accidental bucket deletion
  lifecycle {
    prevent_destroy = true
  }
}

# Enable Versioning (crucial for recovery if state gets corrupted)
resource "aws_s3_bucket_versioning" "enabled" {
  bucket = aws_s3_bucket.terraform_state.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Enable Server-Side Encryption
resource "aws_s3_bucket_server_side_encryption_configuration" "default" {
  bucket = aws_s3_bucket.terraform_state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Block Public Access
resource "aws_s3_bucket_public_access_block" "public_access" {
  bucket                  = aws_s3_bucket.terraform_state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}