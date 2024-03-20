# Users in Connector

There are 2 types of users that can access Connector API:

 * connector (performing contract negotiation and requesting data) 
 * human (api) user (performing actions for configuring connector and updating catalogs)
 
## Connector "user"

This user does not represent human in real life, but it is used to identify and authorize other connectors performing actions like requesting catalog, doing contract negotiation or requesting data. Authorization is performed via Bearer token - in form of JWT (Json Web Token). Token should be send with each request and connector will evaluate token and if all checks are successful, action will be allowed. Otherwise 403 Http status response will be returned.

## API (human) user

Api user represents real human user, responsible for configuring connector, making configuration modifications and updating catalogs. This user is identified with username, password and role. From API perspective, authorization is done using Basic authorization (header key - 'Authorization', header value 'Basic encoded(username:password)'.

Information about users and their roles are stored in database.