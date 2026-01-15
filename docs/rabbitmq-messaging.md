# RabbitMQ messaging objects

This document captures the RabbitMQ objects created for the email job publisher and
how to inspect them with `rabbitmqadmin`.

## Exchanges

```
+--------------------+---------+---------+
|        name        |  type   | durable |
+--------------------+---------+---------+
|                    | direct  | True    |
| amq.direct         | direct  | True    |
| amq.fanout         | fanout  | True    |
| amq.headers        | headers | True    |
| amq.match          | headers | True    |
| amq.rabbitmq.trace | topic   | True    |
| amq.topic          | topic   | True    |
| rb.email           | topic   | True    |
+--------------------+---------+---------+
```

## Queues

```
+---------------+----------+
|     name      | messages |
+---------------+----------+
| rb.email.jobs |          |
+---------------+----------+
```

## Bindings

```
+----------+---------------+---------------------+
|  source  |  destination  |     routing_key     |
+----------+---------------+---------------------+
|          | rb.email.jobs | rb.email.jobs       |
| rb.email | rb.email.jobs | email.contact       |
| rb.email | rb.email.jobs | email.verify_signup |
+----------+---------------+---------------------+
```

### Why does the first binding have an empty source?

That row represents the implicit binding created for the **default exchange** (name
is the empty string). RabbitMQ automatically binds every queue to the default
exchange using the queue name as the routing key, which is why the source appears
blank and the routing key matches `rb.email.jobs`.
