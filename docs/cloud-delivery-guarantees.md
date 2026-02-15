# Cloud Delivery Guarantees (ACK + Retry + Replay Safety)

Overview
- Queue entries are processed once per delivery id and ACKed back to the cloud.
- Dedupe + inflight tracking protects against duplicate/replayed deliveries.
- ACKs are batched and retried until maxAttempts.

Flow
1) Poll: GET /v1/agent/queue/{serverId}
2) Validate: resolve player + parse script
3) Inflight: mark delivery id to prevent concurrent duplicates
4) Execute: run ACE on the player's region thread
5) ACK: send status back in batch
6) Replay safety: processed ids are cached for dedupe TTL

ACK Payload (default)
- POST to `cloud.ack.path` with JSON body:
  {
    "server_id": "survival-1",
    "serverId": "survival-1",
    "acks": [
      {"id":"...","status":"applied","attempt":1}
    ]
  }

Statuses
- applied: script executed
- duplicate: already processed
- invalid: missing/empty script
- failed: exceeded maxFailures

Config (zakum-core config.yml)
cloud:
  dedupe:
    enabled: true
    ttlSeconds: 300
    maximumSize: 50000
    inflightTtlSeconds: 120
    persist:
      enabled: false
      file: "cloud-dedupe.yml"
      flushSeconds: 60
  ack:
    enabled: true
    path: "/v1/agent/queue/ack"   # supports {serverId}
    batchSize: 200
    flushSeconds: 2
    maxAttempts: 5
  delivery:
    maxFailures: 3
http:
  resilience:
    enabled: true
    circuitBreaker: ...
    retry: ...

Admin Status
- /zakum cloud status
  - shows ack counters, pending acks, last ack status, inflight/processed sizes
  - shows shared HTTP resilience counters (calls/success/fail/retry/short-circuit + circuit state)
- /zakum cloud flush
  - triggers immediate ACK flush + dedupe persist write

Failure Handling
- If a player is offline: no ACK (entry can be replayed).
- If script is invalid: ACK as invalid to stop infinite replay.
- If execution throws repeatedly: after maxFailures, ACK as failed.
- If ACK endpoint returns 404/501: ACKs are disabled for the session.

Notes
- Dedupe is in-memory with TTL; replays beyond TTL will be re-processed.
- For best results, keep dedupe TTL >= cloud queue retry window.
- Optional dedupe persistence stores processed ids on disk for restart safety.
- Cloud transport uses the same http.* policy as ControlPlane for retry/circuit consistency.
