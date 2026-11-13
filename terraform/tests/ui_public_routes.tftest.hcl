mock_provider "kind" {}
mock_provider "kubernetes" {}
mock_provider "local" {}

run "tenant_discovery_routes" {
  command = plan

  assert {
    condition = can(regex(
      "(?s)listen 80;.*?location /connector-a/public/\\s*\\{\\s*proxy_pass http://connector-a:8080/public/;",
      split("listen 443 ssl;", kubernetes_config_map.ui_a_nginx_conf.data["nginx.conf"])[0]
    ))
    error_message = "Connector A's HTTP UI must proxy public tenant discovery to the connector."
  }

  assert {
    condition = can(regex(
      "(?s)location /connector-a/public/\\s*\\{\\s*proxy_pass https://connector-a:8080/public/;",
      split("listen 443 ssl;", kubernetes_config_map.ui_a_nginx_conf.data["nginx.conf"])[1]
    ))
    error_message = "Connector A's HTTPS UI must proxy public tenant discovery to the connector."
  }

  assert {
    condition = can(regex(
      "(?s)listen 80;.*?location /connector-b/public/\\s*\\{\\s*proxy_pass http://connector-b:8090/public/;",
      split("listen 443 ssl;", kubernetes_config_map.ui_b_nginx_conf.data["nginx.conf"])[0]
    ))
    error_message = "Connector B's HTTP UI must proxy public tenant discovery to the connector."
  }

  assert {
    condition = can(regex(
      "(?s)location /connector-b/public/\\s*\\{\\s*proxy_pass https://connector-b:8090/public/;",
      split("listen 443 ssl;", kubernetes_config_map.ui_b_nginx_conf.data["nginx.conf"])[1]
    ))
    error_message = "Connector B's HTTPS UI must proxy public tenant discovery to the connector."
  }
}
