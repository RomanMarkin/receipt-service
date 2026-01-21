output "id" {
  value = aws_ecs_cluster.main.id
}

output "name" {
  value = aws_ecs_cluster.main.name
}

output "execution_role_arn" {
  value = aws_iam_role.execution_role.arn
}

output "log_group_name" {
  value = aws_cloudwatch_log_group.main.name
}