# 1. Create the Project for a cluster
resource "mongodbatlas_project" "this" {
  name   = "${var.project_name}-${var.env}"
  org_id = var.org_id
}

# 2. Create the Cluster
resource "mongodbatlas_advanced_cluster" "this" {
  project_id   = mongodbatlas_project.this.id
  name         = "${var.env}-cluster"
  cluster_type = "REPLICASET"
  replication_specs = [{
    region_configs = [{
      electable_specs = {
        instance_size = var.instance_size_name
      }
      provider_name         = var.provider_name
      backing_provider_name = var.provider_name == "TENANT" ? "AWS" : null
      region_name           = var.region_name
      priority              = 7
    }]
  }]
}

# 3. Create a Database User (for your App)
# Generate a random password or pass one in. Let Terraform manage a random password for simplicity.
resource "random_password" "db_pass" {
  length  = 24
  special = false
}

resource "mongodbatlas_database_user" "app_user" {
  username           = "app-user"
  password           = random_password.db_pass.result
  project_id         = mongodbatlas_project.this.id
  auth_database_name = "admin"

  roles {
    role_name     = "readWrite"
    database_name = var.db_name
  }
}

# 4. IP Whitelist (allow AWS NAT Gateway to talk to Atlas)
resource "mongodbatlas_project_ip_access_list" "nat_ips" {
  project_id = mongodbatlas_project.this.id
  count      = length(var.nat_gateway_ips)
  ip_address = var.nat_gateway_ips[count.index]
  comment    = "AWS NAT Gateway - ${var.env}"
}