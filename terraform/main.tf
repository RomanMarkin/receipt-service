locals {
  project_name = "betgol-receipt-leadgen"
  common_tags  = {
    Project    = local.project_name
    ManagedBy  = "Terraform"
  }
}

# 1. Use existing global ECR Repository
data "aws_ecr_repository" "app_repo" {
  name = "betgol-receipt-leadgen"
}

# 2. Network
module "vpc" {
  source             = "./modules/networking"
  env                = var.env
  project_name       = local.project_name
  cidr_block         = var.vpc_cidr
  single_nat_gateway = var.single_nat_gateway
}

# 3. ECS Cluster
module "ecs_cluster" {
  source       = "./modules/ecs-cluster"
  project_name = local.project_name
  env          = var.env
}

# 4. MongoDB Atlas
module "db" {
  source = "./modules/db"
  project_name = local.project_name
  env          = var.env
  org_id            = var.mongodb_atlas_org_id
  nat_gateway_ips   = module.vpc.nat_public_ips
  instance_size_name = var.mongodb_atlas_instance_size_name
  provider_name      = var.mongodb_atlas_provider_name
  region_name        = replace(upper(var.aws_region), "-", "_")
  db_name            = var.app_db_name
}

# 5. ZIO App Server (REST API)
module "api_server" {
  source                  = "./modules/app-service"
  project_name            = local.project_name
  env                     = var.env
  aws_region              = var.aws_region
  app_name                = "receipt-server"
  app_role                = "server"
  docker_image            = "${data.aws_ecr_repository.app_repo.repository_url}:${var.image_tag}"
  app_secrets             = merge(var.app_secrets, { "mongodb_connection_string" = module.db.connection_string_full })
  app_db_name             = var.app_db_name

  desired_count           = var.app_count
  cpu                     = var.app_cpu
  memory                  = var.app_memory
  on_demand_base_capacity = var.app_on_demand_base

  # Infrastructure
  cluster_id              = module.ecs_cluster.id
  vpc_id                  = module.vpc.vpc_id
  public_subnets          = module.vpc.public_subnets
  private_subnets         = module.vpc.private_subnets
  execution_role_arn      = module.ecs_cluster.execution_role_arn
}


# 6. Worker: receipt_verification_retry_job
module "receipt_worker" {
  source             = "./modules/worker-service"
  project_name       = local.project_name
  env                = var.env
  aws_region         = var.aws_region
  app_name           = "receipt-verification-retry-worker"
  app_role           = "receipt_verification_retry_job"
  docker_image       = "${data.aws_ecr_repository.app_repo.repository_url}:${var.image_tag}"
  app_secrets        = merge(var.app_secrets, { "mongodb_connection_string" = module.db.connection_string_full })
  app_db_name        = var.app_db_name

  # Infrastructure
  cluster_id         = module.ecs_cluster.id
  vpc_id             = module.vpc.vpc_id
  private_subnets    = module.vpc.private_subnets
  execution_role_arn = module.ecs_cluster.execution_role_arn
}

# 7. Worker: bonus_assignment_retry_job
module "bonus_worker" {
  source             = "./modules/worker-service"
  project_name       = local.project_name
  env                = var.env
  aws_region         = var.aws_region
  app_name           = "bonus-assignment-retry-worker"
  app_role           = "bonus_assignment_retry_job"
  docker_image       = "${data.aws_ecr_repository.app_repo.repository_url}:${var.image_tag}"
  app_secrets        = merge(var.app_secrets, { "mongodb_connection_string" = module.db.connection_string_full })
  app_db_name        = var.app_db_name

  # Infrastructure
  cluster_id         = module.ecs_cluster.id
  vpc_id             = module.vpc.vpc_id
  private_subnets    = module.vpc.private_subnets
  execution_role_arn = module.ecs_cluster.execution_role_arn
}