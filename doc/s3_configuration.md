# S3 Storage Configuration

This document describes how to configure S3 storage integration for the DSP True Connector's data transfer
functionality.

## Overview

The DSP True Connector supports S3-compatible object storage for artifact management and data transfer. You can choose **one** of the following storage backends:

1. **MinIO** - Open-source, self-hosted S3-compatible storage
2. **AWS S3** - Amazon's native cloud object storage service

**Important**: Configure only **one** storage backend at a time. The connector uses the same configuration properties for both MinIO and AWS S3, but the values differ based on your chosen backend.

## Configuration Properties

Choose **ONE** of the following configuration approaches based on your storage backend:

### Option 1: MinIO Configuration

Use this configuration when connecting to **MinIO** self-hosted object storage.

**Key Characteristics:**
- ✅ Self-hosted, open-source solution
- ✅ Full control over infrastructure
- ✅ Requires explicit endpoint URLs
- ✅ Must configure external presigned endpoint for URL generation
- ✅ Best for on-premise deployments and development environments

For MinIO, add the following properties:

```properties
# MinIO Connection Settings
s3.endpoint=<S3_ENDPOINT_URL>                      # REQUIRED: e.g., http://localhost:9000 or https://minio.example.com
s3.accessKey=<YOUR_ACCESS_KEY>
s3.secretKey=<YOUR_SECRET_KEY>
s3.region=<S3_REGION>                              # Can be any value (e.g., us-east-1)
s3.bucketName=<BUCKET_NAME>
s3.externalPresignedEndpoint=<EXTERNAL_ENDPOINT>   # REQUIRED: External endpoint for presigned URLs
```

**Complete MinIO Example:**
```properties
s3.endpoint=http://localhost:9000
s3.accessKey=minioadmin
s3.secretKey=minioadmin
s3.region=us-east-1
s3.bucketName=dsp-artifacts
s3.externalPresignedEndpoint=http://192.168.1.100:9000
```

**MinIO Setup Steps:**
1. Deploy MinIO server (Docker, Kubernetes, or standalone)
2. Access MinIO console and create access credentials
3. Create a bucket for the connector
4. Set `s3.endpoint` to your MinIO server's internal URL
5. Set `s3.externalPresignedEndpoint` to the externally accessible URL (important for presigned URLs)

---

### Option 2: AWS S3 Configuration

Use this configuration when connecting to **Amazon AWS S3** cloud storage.

**Key Characteristics:**
- ✅ Fully managed cloud service by Amazon
- ✅ No endpoint URL required (uses AWS regional endpoints automatically)
- ✅ Presigned URLs handled automatically by AWS
- ✅ Requires AWS account and IAM credentials
- ✅ Best for production cloud deployments

For AWS S3 buckets, add the following properties to your `application.properties`:

```properties
# AWS S3 Connection Settings
s3.endpoint=                           # MUST be empty for AWS S3 (uses default AWS endpoints)
s3.accessKey=<YOUR_AWS_ACCESS_KEY_ID>
s3.secretKey=<YOUR_AWS_SECRET_ACCESS_KEY>
s3.region=<AWS_REGION>                 # e.g., us-east-1, eu-west-1, ap-southeast-1
s3.bucketName=<YOUR_BUCKET_NAME>
s3.externalPresignedEndpoint=          # MUST be empty for AWS S3 (uses AWS regional endpoints)
```

**Complete AWS S3 Example:**
```properties
s3.endpoint=
s3.accessKey=AKIAIOSFODNN7EXAMPLE
s3.secretKey=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
s3.region=us-east-1
s3.bucketName=my-dsp-connector-bucket
s3.externalPresignedEndpoint=
```

**AWS S3 Setup Steps:**
1. Create an S3 bucket in your desired AWS region
2. Create an IAM user with programmatic access
3. Attach appropriate S3 permissions (see Security Considerations)
4. Copy the Access Key ID and Secret Access Key
5. Leave `s3.endpoint` and `s3.externalPresignedEndpoint` empty

---

## Configuration Property Details

The following properties are used regardless of which storage backend you choose:

| Property | MinIO | AWS S3 | Description |
|----------|-------|--------|-------------|
| `s3.endpoint` | **Required** (e.g., `http://localhost:9000`) | **Empty** | The S3 service endpoint URL |
| `s3.accessKey` | MinIO Access Key | AWS Access Key ID | Your S3 access key ID |
| `s3.secretKey` | MinIO Secret Key | AWS Secret Access Key | Your S3 secret access key |
| `s3.region` | Any value (e.g., `us-east-1`) | AWS Region (e.g., `us-east-1`) | S3 region identifier |
| `s3.bucketName` | MinIO Bucket Name | AWS Bucket Name | Name of the S3 bucket to store data |
| `s3.externalPresignedEndpoint` | **Required** (externally accessible URL) | **Empty** | External endpoint URL for presigned URL generation |

### Detailed Property Descriptions

### Detailed Property Descriptions

- **`s3.endpoint`**: The S3 service endpoint URL
  - **MinIO**: Your MinIO server endpoint URL (e.g., `http://localhost:9000` or `https://minio.example.com`)
  - **AWS S3**: Leave empty (connector uses default AWS regional endpoints automatically)
  
- **`s3.accessKey`**: Your S3 access key ID
  - **MinIO**: MinIO access key
  - **AWS S3**: AWS Access Key ID (format: AKIA...)
  
- **`s3.secretKey`**: Your S3 secret access key
  - **MinIO**: MinIO secret key
  - **AWS S3**: AWS Secret Access Key
  
- **`s3.region`**: S3 region identifier
  - **MinIO**: Can be any value (often set to `us-east-1` by convention)
  - **AWS S3**: Must match actual AWS region (e.g., `us-east-1`, `eu-west-1`, `ap-southeast-1`)
  
- **`s3.bucketName`**: Name of the S3 bucket to store data
  
- **`s3.externalPresignedEndpoint`**: External endpoint URL used for presigned URL generation
  - **MinIO**: **Critical** - Must be set to the externally accessible URL for presigned URLs to work correctly
  - **AWS S3**: Leave empty(AWS handles this automatically using regional endpoints)

## MinIO vs AWS S3: Quick Comparison

| Feature | MinIO | AWS S3 |
|---------|-------|--------|
| **Hosting** | Self-hosted on your infrastructure | Cloud-managed by Amazon |
| **Cost Model** | Free software, you pay for infrastructure | Pay-per-use (storage + requests) |
| **Endpoint Configuration** | ✅ Required (your server URL) | ❌ Leave empty (automatic) |
| **Presigned URL Endpoint** | ✅ Required (external URL) | ❌ Leave empty (automatic) |
| **Region** | Can be any value | Must match actual AWS region |
| **Setup Complexity** | Medium (deploy + configure server) | Low (AWS account + IAM) |
| **Best For** | On-premise, development, air-gapped environments | Production cloud deployments |
| **Security** | You manage all security aspects | AWS IAM, policies, encryption |
| **Scalability** | Limited by your infrastructure | Unlimited (AWS managed) |

**Key Takeaway**: Choose **MinIO** for on-premise control and self-hosted environments, or **AWS S3** for cloud-native deployments with minimal infrastructure management.

## Usage in Data Transfer

The S3 storage integration is used for:

- Storing artifacts
- Generating presigned URLs for secure artifact access
- Managing data transfer between connectors

## Testing Configuration

To verify your S3 configuration:

1. Ensure your S3 credentials are correctly set
2. Verify the bucket exists and is accessible
3. Test connectivity using the provided test endpoints
4. Confirm presigned URL generation works as expected

## Security Considerations

### AWS S3 Security Best Practices

- **Use IAM Roles**: When running on AWS infrastructure (EC2, ECS, Lambda), use IAM roles instead of access keys
- **IAM User Permissions**: Create dedicated IAM users with minimal required permissions
- **Enable MFA Delete**: Protect against accidental or malicious deletion of objects
- **Enable Versioning**: Maintain object version history for data recovery
- **Server-Side Encryption**: Enable default encryption for the bucket (SSE-S3, SSE-KMS, or SSE-C)
- **Block Public Access**: Ensure all public access block settings are enabled unless explicitly required
- **Access Logging**: Enable S3 server access logging to track all requests
- **CloudTrail**: Enable AWS CloudTrail for API-level audit logging
- **Bucket Policies**: Implement strict bucket policies with least-privilege access
- **VPC Endpoints**: Use VPC endpoints for S3 to keep traffic within AWS network

### General S3 Security Best Practices

- Use IAM roles with strict privilege access when possible
- Regularly rotate access keys
- Enable bucket encryption (at rest and in transit)
- Configure appropriate bucket policies
- Monitor S3 access logs
- Implement lifecycle policies for data retention
- Use HTTPS endpoints for encrypted data transfer

## Troubleshooting

### Common Issues

#### 1. Connection failures
- Verify endpoint URL is correct (empty for AWS S3)
- Check network connectivity
- Validate credentials
- **AWS Specific**: Verify the region is correct and the service is available in that region
- **AWS Specific**: Check if using VPC endpoints and ensure route tables are configured correctly

#### 2. Access denied errors
- Verify IAM permissions
- Check bucket policies
- Ensure correct access key/secret key
- **AWS Specific**: Verify IAM user/role has the necessary S3 permissions (s3:GetObject, s3:PutObject, etc.)
- **AWS Specific**: Check if bucket has Block Public Access settings that may interfere
- **AWS Specific**: Ensure there are no conflicting SCPs (Service Control Policies) in AWS Organizations

#### 3. Presigned URL issues
- Verify external endpoint configuration
- Check URL expiration settings
- **AWS S3**: Leave `s3.externalPresignedEndpoint` empty (AWS handles this automatically)
- **MinIO**: Ensure the `s3.externalPresignedEndpoint` is set to the MinIO server URL:
    - Running locally (from an IDE): use http://localhost:9000
    - Running in a container (e.g., Docker): set it to the local IP address of the machine (Ethernet adapter - IPv4
      Address; http://192.168.x.x:9000), or the public URL if running in a production environment
- **AWS Specific**: Verify the IAM user/role has s3:GetObject permissions
- **AWS Specific**: Check if bucket CORS configuration is needed for browser-based access

#### 4. Region-specific issues
- **AWS Specific**: Ensure the region in configuration matches the bucket's region
- **AWS Specific**: Some AWS regions require specific endpoint formats (e.g., China regions)
- **AWS Specific**: Check if the region requires opt-in and is enabled for your account
