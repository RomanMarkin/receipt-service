# 1. Context & Global Identity
variable "project_name" {}
variable "env" {}
variable "aws_region" {}

# 2. Application Configuration
variable "app_name" {}
variable "app_role" {}
variable "docker_image" {}
variable "mongodb_host" {}

# 3. Infrastructure Dependencies
variable "vpc_id" {}
variable "private_subnets" {}
variable "cluster_id" {}
variable "execution_role_arn" {}

# 4. Sizing & Scaling (Defaults)
variable "cpu" { default = 256 }
variable "memory" { default = 512 }
variable "desired_count" { default = 1 }