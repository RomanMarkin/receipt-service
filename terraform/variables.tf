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

variable "app_secrets" {
  description = "Map of sensitive application secrets (DB, API tokens, keys, etc.)"
  type        = map(string)
  sensitive   = true
  default     = {}
}

variable "mongodb_atlas_org_id" {
  description = "The Organization ID from MongoDB Atlas Settings"
  type        = string
  sensitive   = true
}

variable "mongodb_atlas_public_key" {
  type      = string
  sensitive = true
}

variable "mongodb_atlas_private_key" {
  type      = string
  sensitive = true
}

variable "mongodb_atlas_instance_size_name" {
  description = "Cluster size (M0, M2, M10, M20...)"
  type        = string
  default     = "M0"
}

variable "mongodb_atlas_provider_name" {
  description = "The provider name. 'TENANT' for M0/M2/M5, 'AWS' for M10+"
  type        = string
  default     = "TENANT"
}

variable "app_db_name" {
  description = "The name of the MongoDB database to create and connect to"
  type        = string
  default     = "receipt_db"
}

variable "domain_name" {
  description = "The hosted zone name managed in AWS"
  type        = string
  default     = "receipt-api.betgol.com"
}