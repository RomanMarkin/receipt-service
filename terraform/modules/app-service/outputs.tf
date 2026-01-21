output "alb_dns_name" {
  description = "The DNS name of the load balancer (Used to call deployed API)"
  value       = aws_lb.main.dns_name
}

output "alb_arn" {
  description = "The ARN of the Load Balancer"
  value       = aws_lb.main.arn
}

output "alb_zone_id" {
  description = "The Canonical Hosted Zone ID of the load balancer (Needed for Route53 Alias)"
  value       = aws_lb.main.zone_id
}

output "service_name" {
  description = "The name of the ECS Service created"
  value       = aws_ecs_service.main.name
}

output "target_group_arn" {
  value = aws_lb_target_group.app.arn
}