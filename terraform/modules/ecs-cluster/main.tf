# 1. AWS Fargate Cluster
resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-${var.env}-cluster"
  # Enable Container Insights (metrics for CPU/Mem usage)
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

# 2. CloudWatch Log Group
# (to collect logs from ZIO apps)
resource "aws_cloudwatch_log_group" "main" {
  name              = "/ecs/${var.project_name}-${var.env}"
  retention_in_days = 30
  tags = {
    Name = "${var.project_name}-${var.env}-logs"
  }
}

# 3. IAM Execution Role
# (Execution role for ECS agent to pull docker images, write logs)
resource "aws_iam_role" "execution_role" {
  name = "${var.project_name}-${var.env}-execution-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{ Action = "sts:AssumeRole", Effect = "Allow", Principal = { Service = "ecs-tasks.amazonaws.com" } }]
  })
}

# Attach the standard AWS policy for Fargate execution
resource "aws_iam_role_policy_attachment" "execution_role_policy" {
  role       = aws_iam_role.execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}