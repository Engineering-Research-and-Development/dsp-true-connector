mock_provider "kind" {}
mock_provider "kubernetes" {}
mock_provider "local" {}

variables {
  auth_provider                  = "INTERNAL"
  keycloak_mode                  = "NONE"
  keycloak_base_url              = ""
  keycloak_admin_username        = ""
  keycloak_admin_password        = ""
  keycloak_db_username           = ""
  keycloak_db_password           = ""
  keycloak_backend_client_secret = ""
  connector_a_ui_origin          = "http://localhost:4200"
  connector_b_ui_origin          = "http://localhost:4300"
}

run "internal_auth_has_no_keycloak" {
  command = plan

  assert {
    condition = (
      length(kubernetes_deployment.keycloak) == 0 &&
      length(kubernetes_deployment.keycloak_db) == 0
    )
    error_message = "Internal authentication must not deploy Keycloak or PostgreSQL."
  }
}

run "disabled_auth_uses_supported_ui_mode" {
  command = plan

  variables {
    auth_provider = "DISABLED"
  }

  assert {
    condition = (
      local.ui_auth_provider == "INTERNAL" &&
      length(kubernetes_deployment.keycloak) == 0 &&
      length(kubernetes_deployment.keycloak_db) == 0
    )
    error_message = "Disabled authentication must keep the UI in INTERNAL mode without Keycloak or PostgreSQL."
  }
}

run "managed_keycloak_creates_resources" {
  command = plan

  variables {
    auth_provider           = "KEYCLOAK"
    keycloak_mode           = "MANAGED"
    keycloak_base_url       = "http://keycloak.example.test:8180"
    keycloak_admin_username = "test-admin"
    keycloak_admin_password = "test-only-password"
    keycloak_db_username    = "test-db-user"
    keycloak_db_password    = "test-only-db-password"
  }

  assert {
    condition = (
      length(kubernetes_deployment.keycloak) == 1 &&
      length(kubernetes_deployment.keycloak_db) == 1 &&
      length(kubernetes_secret.keycloak_realm_import) == 1
    )
    error_message = "Managed mode must deploy Keycloak, PostgreSQL, and realm imports."
  }
}

run "external_keycloak_has_no_managed_resources" {
  command = plan

  variables {
    auth_provider                  = "KEYCLOAK"
    keycloak_mode                  = "EXTERNAL"
    keycloak_base_url              = "https://identity.example.test"
    keycloak_backend_client_secret = "test-only-client-secret"
  }

  assert {
    condition = (
      length(kubernetes_deployment.keycloak) == 0 &&
      length(kubernetes_deployment.keycloak_db) == 0 &&
      length(kubernetes_secret.keycloak_realm_import) == 0
    )
    error_message = "External mode must not provision Keycloak infrastructure."
  }
}

run "reject_incompatible_authentication_modes" {
  command = plan

  variables {
    auth_provider = "INTERNAL"
    keycloak_mode = "MANAGED"
  }

  expect_failures = [
    local_file.connector_a_properties,
    local_file.connector_b_properties,
  ]
}

run "reject_ui_origin_containing_a_path" {
  command = plan

  variables {
    connector_a_ui_origin = "https://ui.example.test/some/path"
  }

  expect_failures = [var.connector_a_ui_origin]
}
