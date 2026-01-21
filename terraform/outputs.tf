output "ecr_repository_url" {
  description = "The ECR Repository URL"
  value       = module.ecr.repository_url
}

output "api_endpoint" {
  description = "The public URL of the ZIO API Server"
  value       = "http://${module.api_server.alb_dns_name}"
}

output "receipt_worker_logs" {
  description = "Location of logs for the Receipt Verifier worker"
  value       = module.receipt_worker.log_group_name
}

output "bonus_worker_logs" {
  description = "Location of logs for the Bonus Retry worker"
  value       = module.bonus_worker.log_group_name
}