# Used to fetch availability zones dynamically
data "aws_availability_zones" "available" {
  state = "available"
}

module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 6.0"
  name = "${var.project_name}-${var.env}-vpc"
  cidr = var.cidr_block
  # Use the first 2 available zones in the region
  azs = [data.aws_availability_zones.available.names[0], data.aws_availability_zones.available.names[1]]
  # Dynamic Subnet Calculation
  # e.g., if VPC is 10.0.0.0/16:
  # Public:  10.0.1.0/24, 10.0.2.0/24
  # Private: 10.0.101.0/24, 10.0.102.0/24
  public_subnets  = [cidrsubnet(var.cidr_block, 8, 1), cidrsubnet(var.cidr_block, 8, 2)]
  private_subnets = [cidrsubnet(var.cidr_block, 8, 101), cidrsubnet(var.cidr_block, 8, 102)]
  # NAT Gateway Configuration
  enable_nat_gateway     = true
  single_nat_gateway     = var.single_nat_gateway
  one_nat_gateway_per_az = !var.single_nat_gateway
  # DNS Support (Needed for Mongo Atlas peering)
  enable_dns_hostnames = true
  enable_dns_support   = true
  # Declare module specific tags
  tags = {
    Module = "terraform-aws-modules/vpc/aws"
  }
}