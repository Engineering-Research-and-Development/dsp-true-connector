# S3 Storage Configuration

This document describes how to configure S3 storage integration for the DSP True Connector's data transfer
functionality.

## Configuration Properties

Add the following properties to your `application.properties`:

```properties
# S3 Connection Settings
s3.endpoint=<S3_ENDPOINT_URL>
s3.accessKey=<YOUR_ACCESS_KEY>
s3.secretKey=<YOUR_SECRET_KEY>
s3.region=<S3_REGION>
s3.bucketName=<BUCKET_NAME>
s3.externalPresignedEndpoint=<EXTERNAL_ENDPOINT>
```

### Property Descriptions

- `s3.endpoint`: The S3 service endpoint URL (your custom endpoint for MinIO/other S3-compatible services)
- `s3.accessKey`: Your S3 access key ID
- `s3.secretKey`: Your S3 secret access key
- `s3.region`: S3 region identifier (e.g., us-east-1)
- `s3.bucketName`: Name of the S3 bucket to store data
- `s3.externalPresignedEndpoint`: External endpoint URL used for presigned URLs, which are used to download artifacts
  from connectors and
  to view artifacts that are already stored in S3

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

- Use IAM roles with strict privilege access
- Regularly rotate access keys
- Enable bucket encryption
- Configure appropriate bucket policies
- Monitor S3 access logs

## Troubleshooting

Common issues:

1. Connection failures
    - Verify endpoint URL is correct
    - Check network connectivity
    - Validate credentials

2. Access denied errors
    - Verify IAM permissions
    - Check bucket policies
    - Ensure correct access key/secret key

3. Presigned URL issues
    - Verify external endpoint configuration
    - Check URL expiration settings
    - If using MinIO, ensure the `s3.externalPresignedEndpoint` is set to the MinIO server URL:
        - running locally (from an IDE) the default URL will be http://localhost:9000
        - running in a container (e.g., Docker), set it to the local IP address of the machine (Ethernet adapter - IPv4
          Address; http://192.168.x.x:9000),
          or the public URL if running in a production environment.
