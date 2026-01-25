# 1. Security Group (Egress Only)
# Job workers initiate connections (to Mongo/API), but nobody connects TO them.
resource "aws_security_group" "worker_sg" {
  name   = "${var.project_name}-${var.env}-${var.app_name}-sg"
  vpc_id = var.vpc_id

  egress {
    protocol    = "-1"
    from_port   = 0
    to_port     = 0
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# 2. CloudWatch Logs
resource "aws_cloudwatch_log_group" "worker_logs" {
  name              = "/ecs/${var.project_name}-${var.env}-${var.app_name}"
  retention_in_days = 7
}

# 3. Task Definition
resource "aws_ecs_task_definition" "worker" {
  family                   = "${var.project_name}-${var.env}-${var.app_name}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = var.execution_role_arn

  container_definitions = jsonencode([
    {
      name      = "worker"
      image     = var.docker_image
      essential = true
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.worker_logs.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "worker"
        }
      }
      environment = [
        { name = "ENV", value = var.env },
        { name = "APP_ROLE", value = var.app_role },
        { name = "MONGODB_CONNECTION_STRING", value = var.app_secrets["mongodb_connection_string"] },
        { name = "BONUS_APP_CODE",  value = var.app_secrets["bonus_app_code"] },
        { name = "API_PERU_TOKEN",  value = var.app_secrets["api_peru_token"] },
        { name = "FACTILIZA_TOKEN", value = var.app_secrets["factiliza_token"] },
        { name = "JSON_PE_TOKEN",   value = var.app_secrets["json_pe_token"] }
      ]
    }
  ])
}

# 4. ECS Service (Without Load Balancer)
resource "aws_ecs_service" "worker" {
  name            = "${var.project_name}-${var.env}-${var.app_name}"
  cluster         = var.cluster_id
  task_definition = aws_ecs_task_definition.worker.arn
  desired_count   = var.desired_count

  # Enable Spot Capacity to save 70% on background workers
  capacity_provider_strategy {
    capacity_provider = "FARGATE_SPOT"
    weight            = 100
  }

  network_configuration {
    security_groups  = [aws_security_group.worker_sg.id]
    subnets          = var.private_subnets
    assign_public_ip = false
  }
}