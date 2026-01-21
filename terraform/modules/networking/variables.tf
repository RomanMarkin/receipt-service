# 1. Context & Global Identity
variable "project_name" {}
variable "env" {}

# 2. Network Configuration
variable "cidr_block" {}
variable "single_nat_gateway" {
  description = "Should we share one NAT Gateway across all AZs? True = Staging (Cheap), False = Prod (High Availability)"
  type        = bool
  default     = false
}