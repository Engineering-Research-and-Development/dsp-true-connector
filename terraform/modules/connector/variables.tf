variable "name" { type = string }
variable "image" { type = string }
variable "container_port" { type = number }
variable "service_port" { type = number }
variable "node_port" { type = number }
variable "env_config_map" { type = string }
variable "config_map" { type = string }
variable "initial_data_config_map" { type = string }
variable "certs_config_map" { type = string }
variable "employee_data_config_map" {
  type    = string
  default = null
}

