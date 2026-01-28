variable "domain_name" {
  description = "The full domain name (e.g. dev.receipt-api.betgol.com)"
  type        = string
}

variable "zone_id" {
  description = "The Hosted Zone ID where the record should be created"
  type        = string
}

variable "alb_dns_name" {
  description = "The DNS name of the ALB"
  type        = string
}

variable "alb_zone_id" {
  description = "The Zone ID of the ALB"
  type        = string
}