locals {
  project_name = "betgol-receipt-leadgen"
  common_tags  = {
    Project    = local.project_name
    ManagedBy  = "Terraform"
  }
}

# 1. ECR Repository
resource "aws_ecr_repository" "this" {
  name                 = local.project_name
  image_tag_mutability = "MUTABLE"

  # Prevents Terraform from destroying the repo if it contains images.
  force_delete = false

  image_scanning_configuration {
    scan_on_push = true
  }
}

# 2. Lifecycle Policy (Clean up old images)
resource "aws_ecr_lifecycle_policy" "this" {
  repository = aws_ecr_repository.this.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 30 images to manage costs. Expire old images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 30
      }
      action = {
        type = "expire"
      }
    }]
  })
}