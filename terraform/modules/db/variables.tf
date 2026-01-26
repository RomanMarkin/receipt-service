variable "project_name" {}
variable "env" {}
variable "org_id" {}
variable "provider_name" {}
variable "region_name" {}
variable "instance_size_name" {}
variable "nat_gateway_ips" {
  type = list(string)
}
variable "db_name" {
  description = "Name of the database to grant access to"
  type        = string
}