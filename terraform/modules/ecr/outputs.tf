output "repository_url" {
  description = "The URL of the created repository"
  value       = aws_ecr_repository.this.repository_url
}

output "repository_arn" {
  value = aws_ecr_repository.this.arn
}