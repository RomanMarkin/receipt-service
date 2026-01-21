# Identity
aws_profile = "receipt-service-deploy"

# App Settings
mongodb_connection_string = "mongodb+srv://myuser:mypassword@cluster0.abcde.mongodb.net" # TODO replace with correct one

# Resources
app_cpu     = 256 # 0.25 vCPU
app_memory  = 512 # 0.5 GB RAM
app_count   = 2
app_on_demand_base = 0

# Network
vpc_cidr    = "10.0.0.0/16"
single_nat_gateway = true # for staging it's ok to have only 1 NAT for 2 different private networks