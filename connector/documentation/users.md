# Connector authorization

There are 2 types of authorizations that can access Connector endpoints:

 * connector  
 * human user
 
## Connector

This authorization does not represent human in real life, but it is used to identify and authorize other connectors performing when interacting with connector. Authorization will be used to access endpoints defined by [Dataspace Protocol.](https://docs.internationaldataspaces.org/ids-knowledgebase/dataspace-protocol)

 - [requesting catalog](https://docs.internationaldataspaces.org/ids-knowledgebase/dataspace-protocol/catalog/catalog.binding.https), 
 - [contract negotiation](https://docs.internationaldataspaces.org/ids-knowledgebase/dataspace-protocol/contract-negotiation/contract.negotiation.binding.https) 
 - [transfer process](https://docs.internationaldataspaces.org/ids-knowledgebase/dataspace-protocol/transfer-process/transfer.process.binding.https)
 
If connector protocol endpoints are secured with Basic authorization, then user with role CONNECTOR should be created. And when other connectors are interacting with it, they should send Authorization header with Basic auth.

In case when authorization is performed via Bearer token - in form of JWT (Json Web Token), then connector authorization should not exist. 
JWToken should be send with each request and connector will evaluate token and if all checks are successful, action will be allowed.

## Human user

Human user represents real human user, responsible for interacting with connector, via API endpoints:
 - making configuration modifications 
 - updating catalog
 - interacting with contract negotiation (start, approve, verify...)
 - performing actual data transfer
 
This user is identified with email, password and role (ADMIN, USER). From API perspective, authorization is done using JWT Bearer tokens (header key - 'Authorization', header value 'Bearer <jwt_token>').

The system supports:
- **User Registration**: New users can register via `/api/v1/auth/register` endpoint
- **User Login**: Existing users authenticate via `/api/v1/auth/login` endpoint  
- **JWT Token Management**: Access and refresh tokens for secure API access
- **Auto-approval**: Configurable registration approval (auto-enabled or admin-approval required)

Information about users and their roles are stored in database.

## Authentication API endpoints (/api/v1/auth)

The connector provides JWT-based authentication endpoints for user management:

### User Registration

**POST** `/api/v1/auth/register`

Register a new user account. The system supports both auto-approval and manual approval modes.

**Request Body:**
```json
{
  "firstName": "John",
  "lastName": "Doe", 
  "email": "john.doe@example.com",
  "password": "SecurePassword123!"
}
```

**Response:**
- If auto-approval is enabled: Returns JWT tokens and user info (user is immediately logged in)
- If manual approval is required: Returns user info with message indicating pending admin approval

### User Login

**POST** `/api/v1/auth/login`

Authenticate an existing user and receive JWT tokens.

**Request Body:**
```json
{
  "email": "john.doe@example.com",
  "password": "SecurePassword123!"
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "userId": "urn:uuid:123e4567-e89b-12d3-a456-426614174000",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "role": "ROLE_USER"
}
```

### Token Refresh

**POST** `/api/v1/auth/refresh`

Refresh an expired access token using a valid refresh token.

**Request Body:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response:** Same format as login response with new tokens.

### User Logout

**POST** `/api/v1/auth/logout`

Logout user by revoking tokens. Requires valid Bearer token in Authorization header.

**Headers:**
```
Authorization: Bearer <access_token>
```

### Get Current User

**GET** `/api/v1/auth/me`

Get current authenticated user information. Requires valid Bearer token.

**Headers:**
```
Authorization: Bearer <access_token>
```

**Response:**
```json
{
  "userId": "urn:uuid:123e4567-e89b-12d3-a456-426614174000",
  "firstName": "John",
  "lastName": "Doe", 
  "email": "john.doe@example.com",
  "role": "ROLE_USER",
  "enabled": true,
  "lastLoginDate": "2024-01-15T10:30:00"
}
```

## User Management API endpoints (/api/v1/users)

Connector has implemented comprehensive user management, including following:

 - list user by email
 - list all users
 - create user
 - update user
 - update password
 - reset user password (admin only)
 - delete/disable user (admin only)
 - get user profile
 
All endpoints require:
- **Content-Type**: `application/json`
- **Authorization**: `Bearer <jwt_token>` header with valid JWT token
- **Role-based access**: Most operations require ADMIN role, some allow self-management
 
### Create user (Admin only)

**POST** `/api/v1/users`

Create a new user account. Requires ADMIN role.

**Headers:**
```
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "firstName": "GHA Test user",
  "lastName": "DSP-TRUEConnector", 
  "email": "user_gha@mail.com",
  "password": "GhaPassword123!",
  "role": "ROLE_ADMIN"
}
```

**Response:**
```json
{
  "userId": "urn:uuid:123e4567-e89b-12d3-a456-426614174000",
  "firstName": "GHA Test user",
  "lastName": "DSP-TRUEConnector",
  "email": "user_gha@mail.com", 
  "role": "ROLE_ADMIN",
  "enabled": true,
  "createdDate": "2024-01-15T10:30:00"
}
```

### Update user profile (self or admin)

**PUT** `/api/v1/users/{userId}`

Update user profile information. Users can update their own profile, admins can update any user.

**Headers:**
```
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "firstName": "UPDATE_NAME",
  "lastName": "UPDATE_LAST_NAME"
}
```

**Response:** Updated user profile information.

### Update password (self)

**PUT** `/api/v1/users/{userId}/password`

Update user password. Users can only update their own password.

**Headers:**
```
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "newPassword": "ValidPasswordUpdate123!",
  "password": "ValidPassword123!"
}
```

**Validation:**
- Verifies current password matches
- Applies password complexity rules (min/max length, digits, upper/lower case, special characters)
- Updates password if validation passes

### Reset user password (Admin only)

**PUT** `/api/v1/users/{userId}/reset-password`

Reset a user's password. Requires ADMIN role.

**Headers:**
```
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "newPassword": "NewSecurePassword123!"
}
```

### Delete/Disable user (Admin only)

**DELETE** `/api/v1/users/{userId}`

Disable a user account (soft delete). Requires ADMIN role.

**Headers:**
```
Authorization: Bearer <jwt_token>
```

**Response:** Confirmation message that user account has been disabled.

### List users (Admin only)

**GET** `/api/v1/users`

Get list of all users. Requires ADMIN role.

**GET** `/api/v1/users?email={email}`

Get user by email address. Requires ADMIN role.

**Headers:**
```
Authorization: Bearer <jwt_token>
```

### Get user profile

**GET** `/api/v1/users/{userId}`

Get user profile information. Users can view their own profile, admins can view any profile.

**Headers:**
```
Authorization: Bearer <jwt_token>
```

## Configuration Settings

The authentication system supports several configuration options:

### Registration Settings

- **Auto-approval**: `app.registration.auto=true/false`
  - `true`: New users are automatically enabled and logged in upon registration
  - `false`: New users require admin approval before they can log in

- **Default role**: `app.registration.default-role=ROLE_USER`
  - Sets the default role for newly registered users
  - Options: `ROLE_USER`, `ROLE_ADMIN`

### JWT Token Settings

- **Access token expiration**: `app.jwt.access-token-expiration=3600000` (1 hour in milliseconds)
- **Refresh token expiration**: `app.jwt.refresh-token-expiration=604800000` (7 days in milliseconds)
- **JWT secret**: `app.jwt.secret` (configured in application properties)

### Password Policy

The system enforces password complexity requirements:
- Minimum length: 8 characters
- Maximum length: 128 characters
- Must contain at least one digit
- Must contain at least one lowercase letter
- Must contain at least one uppercase letter
- Must contain at least one special character

## Security Features

- **JWT Token Blacklisting**: Revoked tokens are blacklisted and cannot be reused
- **Refresh Token Rotation**: New refresh tokens are issued on each refresh
- **Account Status Tracking**: Tracks enabled/disabled, locked, and expired account states
- **Last Login Tracking**: Records user login timestamps
- **Role-based Access Control**: Enforces permissions based on user roles (ADMIN, USER)