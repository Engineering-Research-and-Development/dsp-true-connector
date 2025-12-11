# ProfileId Implementation Summary

## Problem Resolved

### Original Issues
1. **Jackson ObjectNode Deserialization Error**: `Failed to instantiate com.fasterxml.jackson.databind.node.ObjectNode using constructor NO_CONSTRUCTOR`
2. **MongoDB Bean Conflict**: Multiple `MongoCustomConversions` beans causing startup failure
3. **Validation Error**: `profileId must not be null` when creating VerifiablePresentation

## Solutions Implemented

### 1. MongoDB JsonNode Serialization Fix

**Files Modified:**
- `dcp/src/main/java/it/eng/dcp/config/DCPMongoConfig.java`
- `connector/src/main/java/it/eng/connector/configuration/MongoConfig.java`

**Files Created:**
- `dcp/src/main/java/it/eng/dcp/config/JsonNodeToDocumentConverter.java`
- `dcp/src/main/java/it/eng/dcp/config/DocumentToJsonNodeConverter.java`

**What was done:**
- Created custom MongoDB converters for `JsonNode` ↔ `Document` conversion
- Added converters to the connector's existing `MongoCustomConversions` bean
- Used `@ConditionalOnMissingBean` in DCP module to avoid bean conflicts
- This fixes the ObjectNode deserialization error when reading credentials from MongoDB

### 2. ProfileId Automatic Assignment (Credentials Storage)

**File Modified:**
- `dcp/src/main/java/it/eng/dcp/rest/DcpController.java`

**What was done:**
- Injected `ProfileResolver` into `DcpController`
- Added logic to determine `profileId` when storing credentials via `POST /dcp/credentials`
- Uses `ProfileResolver.resolve(format, attributes)` to determine the appropriate profile
- Falls back to default `VC11_SL2021_JWT` if resolver returns null
- Checks for `credentialStatus` field to enable StatusList2021 detection

**Resolution Logic:**
```
Format "jwt" + has credentialStatus → VC11_SL2021_JWT
Format "json-ld" + no credentialStatus → VC11_SL2021_JSONLD
Otherwise → Default to VC11_SL2021_JWT
```

### 3. ProfileId Default for Presentations

**File Modified:**
- `dcp/src/main/java/it/eng/dcp/service/PresentationService.java`

**What was done:**
- Updated `createPresentation()` to use default profile when credentials have null profileId
- Changed from: `String profile = e.getKey().isEmpty() ? null : e.getKey();`
- Changed to: `String profile = e.getKey().isEmpty() ? ProfileId.VC11_SL2021_JWT.toString() : e.getKey();`
- This ensures VerifiablePresentation validation passes

### 4. Documentation

**Files Created:**
- `dcp/PROFILEID_GUIDE.md` - Comprehensive guide explaining profileId field

**Files Modified:**
- `dcp/README.md` - Added link to ProfileId Guide

**What was documented:**
- Available profiles and their purpose
- How profileId is automatically determined
- ProfileResolver resolution rules
- Configuration options
- Troubleshooting guide for common issues

## How It Works Now

### Credential Delivery Flow

1. **Issuer sends credentials** via `POST /dcp/credentials`:
   ```json
   {
     "credentials": [{
       "credentialType": "MembershipCredential",
       "payload": "eyJraWQi...",
       "format": "jwt"
     }]
   }
   ```

2. **System automatically determines profileId**:
   - Parses `format` field → `"jwt"`
   - Checks for `credentialStatus` in credential payload
   - Calls `ProfileResolver.resolve("jwt", attributes)`
   - Returns `ProfileId.VC11_SL2021_JWT`

3. **Credential stored with profileId**:
   ```java
   VerifiableCredential {
     id: "urn:uuid:...",
     credentialType: "MembershipCredential",
     profileId: "VC11_SL2021_JWT",  // ← Automatically set
     credential: {...},
     holderDid: "did:web:...",
     ...
   }
   ```

### Presentation Query Flow

1. **Verifier queries** via `POST /dcp/presentations/query`:
   ```json
   {
     "scope": ["org.eclipse.dspace.dcp.vc.type:MembershipCredential"]
   }
   ```

2. **System creates presentation**:
   - Fetches credentials matching scope
   - Groups by `profileId` (null credentials grouped separately)
   - For groups with null profileId → uses default `VC11_SL2021_JWT`
   - Creates one VP per profile group

3. **Presentation returned with profileId**:
   ```java
   VerifiablePresentation {
     holderDid: "did:web:...",
     credentialIds: ["urn:uuid:..."],
     profileId: "VC11_SL2021_JWT",  // ← Set from credentials or default
     ...
   }
   ```

## Benefits

✅ **No Manual Configuration Required**: Users don't need to specify profileId in requests  
✅ **Automatic Profile Detection**: System intelligently determines the right profile  
✅ **Backwards Compatible**: Existing credentials without profileId get default value  
✅ **Profile Homogeneity**: Presentations group credentials by profile as per DCP spec  
✅ **Extensible**: Custom ProfileResolver can be implemented for specific needs  

## Testing

### Verify Credential Storage
```bash
# Send credential
POST /dcp/credentials
{
  "status": "ISSUED",
  "credentials": [{
    "credentialType": "MembershipCredential",
    "format": "jwt",
    "payload": "eyJ..."
  }]
}

# Check MongoDB - profileId should be set
db.verifiable_credentials.findOne()
```

### Verify Presentation Creation
```bash
# Query presentation
POST /dcp/presentations/query
{
  "scope": ["org.eclipse.dspace.dcp.vc.type:MembershipCredential"]
}

# Response should include presentations with profileId
# No validation errors should occur
```

## Migration Guide

### For Existing Deployments

If you have existing credentials in MongoDB without `profileId`:

**Option 1: Database Update (Quick Fix)**
```javascript
// Update all credentials with null profileId to default
db.verifiable_credentials.updateMany(
  { profileId: null },
  { $set: { profileId: "VC11_SL2021_JWT" } }
)
```

**Option 2: Re-delivery (Clean Approach)**
1. Delete existing credentials: `db.verifiable_credentials.deleteMany({})`
2. Re-deliver credentials via issuer
3. System will assign profileId automatically

## Future Enhancements

Potential improvements for the future:

1. **Profile Detection from JWT Claims**: Parse JWT to detect profile from claims
2. **Custom Profile Attributes**: Support for custom profile attributes beyond statusList
3. **Profile Registry**: Dynamic profile registration and discovery
4. **Profile Negotiation**: Allow holder/verifier to negotiate supported profiles

## Files Changed Summary

### Core Logic Changes
- ✅ `dcp/src/main/java/it/eng/dcp/rest/DcpController.java`
- ✅ `dcp/src/main/java/it/eng/dcp/service/PresentationService.java`

### Configuration Changes
- ✅ `dcp/src/main/java/it/eng/dcp/config/DCPMongoConfig.java`
- ✅ `connector/src/main/java/it/eng/connector/configuration/MongoConfig.java`

### New Files
- ✅ `dcp/src/main/java/it/eng/dcp/config/JsonNodeToDocumentConverter.java`
- ✅ `dcp/src/main/java/it/eng/dcp/config/DocumentToJsonNodeConverter.java`
- ✅ `dcp/PROFILEID_GUIDE.md`

### Documentation Updates
- ✅ `dcp/README.md`

## Verification

All changes have been:
- ✅ Compiled successfully (no errors)
- ✅ Checkstyle validated (0 violations)
- ✅ Bean conflicts resolved
- ✅ Documentation updated

