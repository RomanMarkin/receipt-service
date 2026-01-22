# Resources
app_cpu     = 512  # 0.5 vCPU
app_memory  = 1024 # 1 GB RAM
app_count   = 2
app_on_demand_base = 1

# Network
vpc_cidr    = "10.0.0.0/16"
single_nat_gateway = true # Change to false if we are ready to pay extra $35/mo for redundancy (each AZ will have it's own NAT gateway)