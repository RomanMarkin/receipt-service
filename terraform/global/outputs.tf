output "repository_url" {
  description = "The URL of the created repository"
  value       = aws_ecr_repository.this.repository_url
}

output "repository_arn" {
  value = aws_ecr_repository.this.arn
}

output "hosted_zone_id" {
  value = aws_route53_zone.main.zone_id
}

output "nameservers" {
  value = aws_route53_zone.main.name_servers
}