output "log_group_name" {
  description = "The CloudWatch Log Group for this worker. Check here for application logs."
  value       = aws_cloudwatch_log_group.worker_logs.name
}

output "service_name" {
  description = "The name of the ECS Service running this worker."
  value       = aws_ecs_service.worker.name
}

output "task_definition_arn" {
  description = "The ARN of the Task Definition currently running."
  value       = aws_ecs_task_definition.worker.arn
}