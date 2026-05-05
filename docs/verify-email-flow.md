# Verifying Email Flow via /addMsg Endpoint

## Overview

The `/addMsg` endpoint processes email requests and publishes messages to the messaging queue. This guide explains how to verify the email flow is working correctly by monitoring logs and understanding the expected behavior.

## Endpoint Usage

The `/addMsg` endpoint accepts email requests with the following parameters:

- `firstName`: Recipient's first name
- `lastName`: Recipient's last name
- `email`: Email address
- `message`: Email message content

## Verification Steps

### 1. Send a Request

```bash
curl -X POST http://localhost:8080/addMsg \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "message": "Test message content"
  }'
```

### 2. Monitor Logs for Email Request

Look for an `EmailContactRoutes` log entry showing the incoming request:

```
service=release date=2026-05-05T04:10:48.154 - thread=io-compute-62 - level=INFO - logger=com.iscs.ratingbunny.routes.EmailContactRoutes - msg="request" John Doe john.doe@example.com test message content
```

This confirms the endpoint received the request.

### 3. Verify Email Processing

After the request is logged, watch for `EmailContactImpl` logs showing email delivery:

```
service=release date=2026-05-05T04:10:48.981 - thread=io-compute-blocker-62 - level=INFO - logger=com.iscs.ratingbunny.domains.EmailContactImpl - msg=Email john.doe@example.com - Message ID: <771978358.3.17779542...>
```

This indicates:
- Email was successfully sent
- A unique Message ID was generated and sent to the mail server

### 4. Check Processing Duration

Each email includes a total processing time log:

```
service=release date=2026-05-05T04:10:48.981 - thread=io-compute-blocker-62 - level=INFO - logger=com.iscs.ratingbunny.domains.EmailContactImpl - msg=total email time 827 ms
```

**Expected performance:**
- Typical email processing: 600–1000 ms
- This includes message assembly, SMTP communication, and queue publishing

## Example Log Sequence

Here's a complete example of a successful email flow:

```
# Request received
service=release date=2026-05-05T04:10:48.154 - thread=io-compute-62 - level=INFO - logger=com.iscs.ratingbunny.routes.EmailContactRoutes - msg="request" Davos2 Smith freemarket2020@gmx.com test message davos2 publish some publish message updated davos2 more data new

# Email processed and sent
service=release date=2026-05-05T04:10:48.981 - thread=io-compute-blocker-62 - level=INFO - logger=com.iscs.ratingbunny.domains.EmailContactImpl - msg=Email freemarket2020@gmx.com - Message ID: <771978358.3.17779542...>

# Performance metric
service=release date=2026-05-05T04:10:48.981 - thread=io-compute-blocker-62 - level=INFO - logger=com.iscs.ratingbunny.domains.EmailContactImpl - msg=total email time 827 ms
```

## Troubleshooting

### Missing EmailContactImpl logs

If you see the request log but no email processing log, check:
- SMTP configuration and connectivity
- Message queue availability
- Application error logs for exceptions

### Slow Processing Times

If total email time exceeds 2000 ms:
- Check SMTP server latency
- Verify network connectivity
- Review message queue backlog

### Missing Message IDs

If emails are sent but Message ID is not logged:
- Verify mail server connection
- Check SMTP response handling
- Review message assembly logic
