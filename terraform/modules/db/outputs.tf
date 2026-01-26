output "connection_string_standard" {
  value = mongodbatlas_advanced_cluster.this.connection_strings.standard_srv
}

output "connection_string_full" {
  description = "Full connection string with credentials and database name"
  sensitive   = true
  value = "${replace(
    mongodbatlas_advanced_cluster.this.connection_strings.standard_srv,
    "mongodb+srv://",
    "mongodb+srv://${mongodbatlas_database_user.app_user.username}:${mongodbatlas_database_user.app_user.password}@"
  )}/${var.db_name}?retryWrites=true&w=majority"
}

output "db_user" {
  value = mongodbatlas_database_user.app_user.username
}

output "db_pass" {
  value = mongodbatlas_database_user.app_user.password
  sensitive = true
}