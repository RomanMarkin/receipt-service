output "api_endpoint" {
  description = "The public URL of the ZIO API Server"
  value       = "http://${module.api_server.alb_dns_name}"
}

output "db_connection_string_full" {
  description = "The full connection string for local testing"
  value       = module.db.connection_string_full
  sensitive   = true
}

output "receipt_worker_logs" {
  description = "Location of logs for the Receipt Verifier worker"
  value       = module.receipt_worker.log_group_name
}

output "bonus_worker_logs" {
  description = "Location of logs for the Bonus Retry worker"
  value       = module.bonus_worker.log_group_name
}