# Resources
app_cpu     = 512  # 0.5 vCPU
app_memory  = 1024 # 1 GB RAM
app_count   = 2
app_on_demand_base = 1

# Network
vpc_cidr    = "10.0.0.0/16"
single_nat_gateway = true # Change to false if we are ready to pay extra $35/mo for redundancy (each AZ will have it's own NAT gateway)

# MongoDB Atlas
mongodb_atlas_instance_size_name  = "M0"     # Free of charge
#mongodb_atlas_instance_size_name = "M10"    # Starts at ~$57/mo
mongodb_atlas_provider_name       = "TENANT" # Mandatory for M0/M2/M5
#mongodb_atlas_provider_name      = "AWS"    # Mandatory for M10+