variable "env" {
  description = "The environment name (staging, prod)"
  type        = string
}

variable "aws_region" {
  default = "us-east-1"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
}

variable "image_tag" {
  description = "The Docker image tag to deploy (e.g. v1.0.5)"
  type        = string
}

variable "app_count" {
  description = "Number of docker containers to run"
  type        = number
}

variable "app_on_demand_base" {
  description = "How many containers MUST run on On-Demand? (We will configure 0 for Staging, 1+ for Prod)"
  type        = number
  default     = 0
}

variable "app_cpu" {
  description = "Fargate CPU units (256 = 0.25 vCPU)"
  type        = number
}

variable "app_memory" {
  description = "Fargate Memory (512 = 0.5 GB)"
  type        = number
}

variable "single_nat_gateway" {
  description = "Should we run only 1 NAT Gateway for each of two availability zones to save ~$35/m?"
  type        = bool
  default     = false
}

variable "mongodb_connection_string" {
  description = "The MongoDB connection string (e.g. mongodb+srv://...)"
  type        = string
  sensitive   = true # Hides it from Terraform CLI output for security
}