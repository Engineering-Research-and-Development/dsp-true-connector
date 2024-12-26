# Users in Connector

There are 2 types of users that can access Connector API:

 * connector (performing contract negotiation and requesting data) 
 * human (api) user (performing actions for configuring connector and updating catalogs)
 
## Connector "user"

This user does not represent human in real life, but it is used to identify and authorize other connectors performing actions like requesting catalog, doing contract negotiation or requesting data. Authorization is performed via Bearer token - in form of JWT (Json Web Token). Token should be send with each request and connector will evaluate token and if all checks are successful, action will be allowed. Otherwise 403 Http status response will be returned.

## API (human) user

Api user represents real human user, responsible for configuring connector, making configuration modifications and updating catalogs. This user is identified with username, password and role. From API perspective, authorization is done using Basic authorization (header key - 'Authorization', header value 'Basic encoded(username:password)'.

Information about users and their roles are stored in database.

## User API endpoints (/api/v1/users)

Connector has implemented simple user management, including following:

 - list user by email
 - list all uers
 - create user
 - update user
 - update password
 
All endpoints requires Content-Type: application/json, and Authorization header with username:password for existing user with role ADMIN.
 
### Create user 

POST request 

When creating new user request should be like following:

```
{
  "firstName" : "GHA Test user",
  "lastName" : "DSP-TRUEConnector",
  "email" : "user_gha@mail.com",
  "password" : "GhaPassword123!",
  "role" : "ROLE_ADMIN"
}
```

### Update user (can be only for self)

*/api/v1/users/{{userId}}/update*

PUT request

```
{
  "firstName" : "UPDATE_NAME",
  "lastName" : "UPDATE_LAST_NAME"
}

```

It will check if any of fields are passed and if is, it will update firstName and/or lastname.

If updating other user (than logged in), connector will return error message.

### Update password (for self)

*/api/v1/users/{{userId}}/password*
 
 PUT request
 
```
 {
  "newPassword" : "ValidaPasswordUpdate123!",
  "password" : "ValidPassword123!"
}
```

 - It will check if password matches with existing password 
 - Password validity enforcement for new password will be applied (min/max length, must contains digits, lower/upper case, special characters...)
 - If both checks are ok, old password will be replaced with new value