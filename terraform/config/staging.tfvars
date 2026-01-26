# Resources
app_cpu     = 256 # 0.25 vCPU
app_memory  = 512 # 0.5 GB RAM
app_count   = 2
app_on_demand_base = 0

# Network
vpc_cidr    = "10.0.0.0/16"
single_nat_gateway = true # for staging it's ok to have only 1 NAT for 2 different private networks

# MongoDB Atlas
mongodb_atlas_instance_size_name = "M0"     # Free of charge
mongodb_atlas_provider_name      = "TENANT" # Mandatory for M0/M2/M5