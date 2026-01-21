# 1. Context & Global Identity
variable "project_name" {}
variable "env" {}
variable "aws_region" {}

# 2. Application Configuration
variable "app_name" {}
variable "app_role" {}
variable "docker_image" {}
variable "mongodb_host" {}
variable "container_port" {
  description = "The port exposed by the container"
  type        = number
  default     = 8080
}

# 3. Infrastructure Dependencies
variable "vpc_id" {}
variable "cluster_id" {}
variable "execution_role_arn" {}

# Networking
variable "public_subnets" {
  description = "List of public subnets (for Load Balancer)"
  type        = list(string)
  default     = [] # Optional if this module is used for a worker without LB
}

variable "private_subnets" {
  description = "List of private subnets (for App tasks)"
  type        = list(string)
}

# 4. Sizing & Scaling strategy
variable "cpu" { default = 256 }
variable "memory" { default = 512 }
variable "desired_count" { default = 1 }
variable "on_demand_base_capacity" {
  description = "Number of tasks guaranteed to run on On-Demand (Standard) Fargate. The rest go to Spot."
  type        = number
  default     = 0
}