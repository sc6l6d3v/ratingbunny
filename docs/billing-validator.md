# Billing collection validator with Helcim and Stripe support

The schema below allows a document in the `billing_info` collection to describe
Helcim-backed or Stripe-backed accounts. It keeps the shared fields (`userId`,
`gateway`, `address`, etc.) at the top level, and then uses a `oneOf` branch to
require the correct gateway-specific sub-document:

* When `gateway` is `helcim`, the document must include a `helcim` object with a
  `customerId` and optional default token fields.
* When `gateway` is `stripe`, the document must include a `stripe` object with a
  `customerId` and optional payment method identifiers. The `helcim` field can be
  omitted in that case.

```json
{
  "$jsonSchema": {
    "bsonType": "object",
    "required": [
      "_id",
      "userId",
      "gateway",
      "address",
      "updatedAt"
    ],
    "properties": {
      "userId": {
        "bsonType": "string",
        "minLength": 3,
        "maxLength": 64
      },
      "gateway": {
        "enum": [
          "helcim",
          "stripe"
        ]
      },
      "helcim": {
        "bsonType": "object",
        "required": [
          "customerId"
        ],
        "properties": {
          "customerId": {
            "bsonType": "string",
            "minLength": 3
          },
          "defaultCardToken": {
            "bsonType": [
              "string",
              "null"
            ]
          },
          "defaultBankToken": {
            "bsonType": [
              "string",
              "null"
            ]
          }
        }
      },
      "stripe": {
        "bsonType": "object",
        "required": [
          "customerId"
        ],
        "properties": {
          "customerId": {
            "bsonType": "string",
            "minLength": 3
          },
          "defaultPaymentMethod": {
            "bsonType": [
              "string",
              "null"
            ]
          },
          "subscriptionId": {
            "bsonType": [
              "string",
              "null"
            ]
          }
        }
      },
      "address": {
        "bsonType": "object",
        "required": [
          "line1",
          "city",
          "state",
          "postalCode",
          "country"
        ],
        "properties": {
          "line1": {
            "bsonType": "string"
          },
          "line2": {
            "bsonType": [
              "string",
              "null"
            ]
          },
          "city": {
            "bsonType": "string"
          },
          "state": {
            "bsonType": "string"
          },
          "postalCode": {
            "bsonType": "string"
          },
          "country": {
            "bsonType": "string",
            "minLength": 2,
            "maxLength": 2
          }
        }
      },
      "subscription": {
        "bsonType": [
          "object",
          "null"
        ],
        "required": [
          "subscriptionId",
          "planId",
          "status",
          "amountCents",
          "currency"
        ],
        "properties": {
          "subscriptionId": {
            "bsonType": "string"
          },
          "planId": {
            "bsonType": "string"
          },
          "status": {
            "bsonType": "string"
          },
          "nextBillAt": {
            "bsonType": [
              "date",
              "null",
              "string"
            ]
          },
          "amountCents": {
            "bsonType": [
              "long",
              "int"
            ],
            "minimum": 0
          },
          "currency": {
            "bsonType": "string",
            "minLength": 3,
            "maxLength": 3
          }
        }
      },
      "updatedAt": {
        "bsonType": [
          "string",
          "date"
        ]
      }
    },
    "oneOf": [
      {
        "properties": {
          "gateway": {
            "enum": [
              "helcim"
            ]
          }
        },
        "required": [
          "helcim"
        ]
      },
      {
        "properties": {
          "gateway": {
            "enum": [
              "stripe"
            ]
          }
        },
        "required": [
          "stripe"
        ]
      }
    ]
  }
}
```
