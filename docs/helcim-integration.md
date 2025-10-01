# Helcim Integration Checklist

The Rating Slave backend does not create any Helcim resources on your behalf. All
Helcim objects must be created through Helcim’s own tools (dashboard, REST API,
or Helcim.js) and then their identifiers are passed to this service. The tables
below outline what the backend expects and where you obtain the values inside
Helcim.

## Required Helcim artefacts

| Backend field | Helcim resource | How to obtain it |
| --- | --- | --- |
| `billing.helcim.customerId` | Customer | Create the customer through the Helcim Dashboard (**Commerce** → **Customers**) or via [`POST /v2/customers`](https://docs.helcim.com/api/#customers-create). Copy the `id` field from Helcim’s response. |
| `billing.helcim.defaultCardToken` | Tokenized card (optional) | Use Helcim.js or the [Payments API](https://docs.helcim.com/api/#payments-create) to tokenize a card, then persist the token string returned in the `token` field. |
| `billing.helcim.defaultBankToken` | Tokenized bank account (optional) | Tokenize the ACH account through Helcim.js or [`POST /v2/bank-accounts`](https://docs.helcim.com/api/#bank-accounts-create) and store the returned `token`. |

At minimum you must supply the `customerId`; tokens are optional but let you
charge the customer without re-entering payment details.

## Recurring plans (optional but recommended for Pro tiers)

If you sell recurring subscriptions, create them in Helcim first and then attach
their identifiers in the optional `billing.subscription` block. Rating Slave
never infers these IDs; it simply stores what you provide.

| Backend field | Helcim resource | How to obtain it |
| --- | --- | --- |
| `billing.subscription.planId` | Payment Plan | Create plans via the Helcim Dashboard (**Commerce** → **Recurring / Payment Plans**) or with [`POST /v2/payment-plans`](https://docs.helcim.com/api/#payment-plans-create). Copy the `id` of your "Pro Monthly" or "Pro Annual" plan. |
| `billing.subscription.subscriptionId` | Subscription | Once you enrol a customer in a plan (dashboard or [`POST /v2/subscriptions`](https://docs.helcim.com/api/#subscriptions-create)), store the returned subscription `id`. |
| `billing.subscription.status` | Subscription status | Store Helcim’s `status` string (e.g., `active`, `paused`). |
| `billing.subscription.nextBillAt` | Next billing timestamp | Optional ISO timestamp from Helcim’s response (e.g., `nextBilling`). |
| `billing.subscription.amountCents` | Billing amount | Convert Helcim’s plan amount to cents before persisting. |
| `billing.subscription.currency` | Currency code | Copy the three-letter code (e.g., `USD`) from the plan/subscription. |

## Where to plug the values in

When calling the `/auth/signup` endpoint for a paid plan, include a `billing`
payload that embeds the identifiers captured above. An example request body is
shown below:

```json
{
  "email": "customer@example.com",
  "password": "hunter2",
  "plan": "pro_monthly",
  "billing": {
    "helcim": {
      "customerId": "CUST-12345",
      "defaultCardToken": "CARD-abc123"
    },
    "address": {
      "line1": "123 Example St",
      "city": "Calgary",
      "state": "AB",
      "postalCode": "T2P 1J9",
      "country": "CA"
    },
    "subscription": {
      "subscriptionId": "SUB-98765",
      "planId": "PLAN-54321",
      "status": "active",
      "amountCents": 1900,
      "currency": "CAD"
    }
  }
}
```

This service simply persists those identifiers, allowing you to reconcile the
user’s account with Helcim later.

## Additional Helcim resources

Helcim’s full API reference is published at <https://docs.helcim.com/api/>. You
can also use their SDK snippets and the Helcim.js browser library documented at
<https://docs.helcim.com/helcimjs/> to tokenize payment details securely.
