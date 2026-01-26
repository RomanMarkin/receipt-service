# 1. Security Group for the Load Balancer (Public)
resource "aws_security_group" "lb_sg" {
  name   = "${var.project_name}-${var.env}-${var.app_name}-lb-sg"
  vpc_id = var.vpc_id

  ingress {
    protocol    = "tcp"
    from_port   = 80
    to_port     = 80
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    protocol    = "tcp"
    from_port   = 443
    to_port     = 443
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    protocol    = "-1"
    from_port   = 0
    to_port     = 0
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# 2. Security Group for the Fargate Task (Private)
resource "aws_security_group" "app_sg" {
  name   = "${var.project_name}-${var.env}-${var.app_name}-app-sg"
  vpc_id = var.vpc_id

  ingress {
    protocol        = "tcp"
    from_port       = var.container_port
    to_port         = var.container_port
    security_groups = [aws_security_group.lb_sg.id]
  }

  egress {
    protocol    = "-1"
    from_port   = 0
    to_port     = 0
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# 3. Application Load Balancer
resource "aws_lb" "main" {
  name               = "${var.env}-${var.app_name}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.lb_sg.id]
  subnets            = var.public_subnets
}

# 4. Target Group
resource "aws_lb_target_group" "app" {
  name        = "${var.env}-${var.app_name}-tg"
  port        = var.container_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    path                = "/health"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 2
  }
}

# 5. Listener (HTTP)
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = "80"
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

# 6. Task Definition
resource "aws_ecs_task_definition" "app" {
  family                   = "${var.project_name}-${var.env}-${var.app_name}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = var.execution_role_arn

  container_definitions = jsonencode([
    {
      name      = "app"
      image     = var.docker_image
      essential = true
      portMappings = [
        {
          containerPort = var.container_port
          hostPort      = var.container_port
        }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = "/ecs/${var.project_name}-${var.env}-${var.app_name}"
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }
      environment = [
        { name = "ENV", value = var.env },
        { name = "APP_ROLE", value = var.app_role },
        { name = "MONGODB_CONNECTION_STRING", value = var.app_secrets["mongodb_connection_string"] },
        { name = "APP_DB_NAME", value = var.app_db_name },
        { name = "BONUS_APP_CODE",  value = var.app_secrets["bonus_app_code"] },
        { name = "API_PERU_TOKEN",  value = var.app_secrets["api_peru_token"] },
        { name = "FACTILIZA_TOKEN", value = var.app_secrets["factiliza_token"] },
        { name = "JSON_PE_TOKEN",   value = var.app_secrets["json_pe_token"] }
      ]
    }
  ])
}

# 7. ECS Service
resource "aws_ecs_service" "main" {
  name            = "${var.project_name}-${var.env}-${var.app_name}-service"
  cluster         = var.cluster_id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.desired_count

  # Provider 1: Fargate On-demand
  # It takes 'base' number of tasks.
  capacity_provider_strategy {
    capacity_provider = "FARGATE"
    base              = var.on_demand_base_capacity
    weight            = 0
  }

  # Provider 2: Fargate Spot
  # (it takes ALL tasks exceeding the base, because weight is 100 vs 0).
  capacity_provider_strategy {
    capacity_provider = "FARGATE_SPOT"
    base              = 0
    weight            = 100
  }

  network_configuration {
    security_groups  = [aws_security_group.app_sg.id]
    subnets          = var.private_subnets
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "app"
    container_port   = var.container_port
  }

  depends_on = [aws_lb_listener.http]
}

# 8. Log Group
resource "aws_cloudwatch_log_group" "app_logs" {
  name              = "/ecs/${var.project_name}-${var.env}-${var.app_name}"
  retention_in_days = 14
}