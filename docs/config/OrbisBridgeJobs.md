# OrbisBridgeJobs Configuration

Config folder: `plugins/OrbisBridgeJobs/`

Files:
- `config.yml`

Keys:
- `enabled`
- `scale.money`, `scale.exp` (decimal scaling -> long)
- `emit.actions|money|exp`

Emits actions:
- `jobs_action` (amount=1, key=job_action, value=<job>:<actionType>)
- `jobs_money` (amount=scaled money, key=job, value=<job>)
- `jobs_exp` (amount=scaled exp, key=job, value=<job>)
