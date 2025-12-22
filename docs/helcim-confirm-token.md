# Helcim confirm-token validation

The `/helcim/confirm-token` endpoint now expects the Helcim validation payload documented at
<https://devdocs.helcim.com/docs/validate-helcimpayjs>. The request body must include:

- `checkoutToken`: The checkout token that was returned from the `/helcim/initialize` call. The service stores the corresponding `secretToken` for one hour.
- `data`: The transaction payload sent from Helcim (for example, `transactionId`, `status`, `amount`, `cardToken`, etc.). This object is hashed exactly as received using JSON with no added whitespace.
- `hash`: The Helcim-provided hash calculated from the compact `data` JSON plus the shared `secretToken`.

The service recomputes the hash with the stored `secretToken` and validates that a `cardToken` exists before clearing the stored secret.

## Example cURL request

```bash
curl -X POST http://localhost:8080/helcim/confirm-token \
  -H "Content-Type: application/json" \
  -d '{
    "checkoutToken": "CHK12345",
    "data": {
      "transactionId": "20163175",
      "dateCreated": "2023-07-17 10:34:35",
      "cardBatchId": "2915466",
      "status": "APPROVED",
      "type": "purchase",
      "amount": "15.45",
      "currency": "CAD",
      "avsResponse": "X",
      "cvvResponse": "",
      "approvalCode": "T3E5ST",
      "cardToken": "27128ae9440a0b47e2a068",
      "cardNumber": "4000000028",
      "cardHolderName": "Test",
      "customerCode": "CST1049",
      "invoiceNumber": "INV001045",
      "warning": ""
    },
    "hash": "dbcb570cca52c38d597941adbed03f01be78c43cba89048722925b2f168226a9"
  }'
```
