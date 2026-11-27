# Running FitCheck in production

FitCheck runs at **https://fitcheckwardrobe.com** on Azure. This page covers day-to-day operations.
For first-time setup, see [infra/README.md](../infra/README.md).

## At a glance

| What | Where |
| --- | --- |
| Website + API (one container) | Azure Container Apps `fitcheck`, resource group `rg-fitcheck`, North Central US |
| Database | Azure Database for PostgreSQL Flexible Server (B1ms, 7-day backups) |
| Photos | Azure Blob Storage, private container `clothing-images` |
| Email | Azure Communication Services, sending as `no-reply@fitcheckwardrobe.com` |
| AI | Claude API, "FitCheck prod" workspace with a monthly spend limit |
| DNS | Porkbun |
| Images | `ghcr.io/jessicamorris30/fitcheck:<commit>` |
| Logs and alerts | Log Analytics `log-fitcheck`, alerts emailed to the owner ([monitoring.bicep](../infra/monitoring.bicep)) |

## Deploying

**Push to `main`. That's it.** GitHub Actions ([ci.yml](../.github/workflows/ci.yml)) runs:

```
backend tests + frontend lint/build + Docker check → publish image → deploy → check the site serves it
```

- Nothing deploys unless every check passes. Pull requests are checked but never deployed.
- Azure starts the new version next to the old one and only switches visitors over once its
  health checks pass, so a version that can't start never replaces a working one.
- Database changes ship as Flyway migrations (`backend/src/main/resources/db/migration`); they run
  automatically when the new version starts. **A migration can't be undone by a rollback**, so make
  schema changes backwards-compatible (add columns before using them, remove them a release later).
- Watch a deploy: GitHub → Actions, or the repo page → Deployments → production.

**Releases:** bump `"version"` in `frontend/package.json` (shown bottom-right on every page) and tag
the commit: `git tag -a v1.1.0 -m "..." && git push origin v1.1.0`.

## Rolling back

Pick the last good commit id (GitHub → Actions → a green run, or `git log`). Then either:

- GitHub → Actions → that run → **Re-run all jobs** (it redeploys that commit's image), or
- `az containerapp update -g rg-fitcheck -n fitcheck --image ghcr.io/jessicamorris30/fitcheck:<commit id>`

Every published image is kept, so any earlier commit can be restored in about a minute.

## Changing settings and secrets

All settings live in [infra/main.bicep](../infra/main.bicep). Secrets are stored as Container Apps
secrets, never in the repo. To change any of them, run:

```powershell
.\infra\deploy-app.ps1
```

It asks (hidden) for the database password and the Claude key, and **always** creates a new JWT
signing key and a new SMTP password. Logged-in users stay logged in: their refresh tokens don't
depend on the JWT key.

| Secret | How to rotate |
| --- | --- |
| Claude API key | Claude Console → FitCheck prod workspace → create a new key, run `deploy-app.ps1` with it, then delete the old key |
| SMTP password | Just run `deploy-app.ps1` (the old one stops working) |
| JWT signing key | Just run `deploy-app.ps1` |
| Database password | Generate a new one (see infra/README.md step 4), save it in your password manager, run `deploy-app.ps1` with it. The database and the app are updated together |
| Storage account key | `az storage account keys renew -g rg-fitcheck -n <storage account> --key key1`, then `deploy-app.ps1` (photo links signed with the old key stop working within 15 minutes) |

## When something goes wrong

**You'll get an email** (to the address on the Azure account) when:

| Alert | Means | First look |
| --- | --- | --- |
| `alert-fitcheck-5xx` | Several server errors in 15 minutes | "FitCheck: errors and warnings" log search |
| `alert-fitcheck-restarts` | The container crashed or failed health checks | "platform warnings" log search, then "errors and warnings" |
| `alert-fitcheck-db-storage` | Database disk over 80% | Raise `storageSizeGB` in main.bicep (it can only grow) |
| `alert-fitcheck-db-cpu` | Database busy for 30+ minutes | Usually a runaway query or abuse; check the errors search |
| Budget emails | Spending at 50% / 100% of $10, or forecast to exceed it | Azure portal → Cost Management → Cost analysis, grouped by resource |

**Logs:** Azure portal → `log-fitcheck` → Logs → Queries → category **FitCheck**:
- *errors and warnings*: the app's own ERROR/WARN lines
- *AI stylist usage per day*: requests and tokens (cost check)
- *app starts*: cold starts and deploys, with startup time
- *platform warnings*: crashes, failed health checks, image pull problems

Or from a terminal: `az containerapp logs show -g rg-fitcheck -n fitcheck --tail 100`.

**Common situations**

- *Site down after a deploy*: roll back (above), then look at that run's logs.
- *AI says "unavailable"*: check status.claude.com; check the Claude Console for the spend limit
  being reached. Users get their daily request refunded when the AI fails.
- *Emails not arriving*: Communication Services → Insights; check the domain is still "Verified"
  (DNS records at Porkbun must stay in place).
- *Restore the database*: Azure portal → the PostgreSQL server → Restore (any point in the last 7
  days). This creates a **new** server; point `DB_URL` at it via main.bicep.

## Known limits (accepted for now)

- **Cold start**: after a few quiet minutes the app scales to zero, and the next visitor waits while
  Azure finds a machine, downloads the image (~6 s) and starts the app. Since v1.1 the image carries a
  Java startup cache (made during `docker build`, see the Dockerfile) and the health checks run every
  3 s, which together cut the wait a lot (it was ~43 s). Keeping one copy always running
  (`minReplicas: 1`) would remove the wait but costs ~$14 CAD/month, more than the student credit lasts.
  If a startup ever fails after a change, check the build log for the "AOTCache creation is
  complete" line, and the startup probe in main.bicep (Azure gives up after ~33 s).
- **Memory**: the container has 1 GiB and Java's heap may use 60% of it (Dockerfile
  `JAVA_TOOL_OPTIONS`). The container sits around 950 MiB under heavy photo uploads: Java claims its
  whole heap allowance up front. What matters is the heap actually in use, which stays below about
  420 of 595 MiB with 6 phone photos uploading at once. At most 2 photos are processed at the same
  moment (`ThumbnailService.MAX_CONCURRENT`), and images over 100 megapixels are refused. To test changes
  under the same limits: `.\scripts\run-prod-local.ps1` (it applies Azure's 0.5 CPU / 1 GiB).
- **One copy maximum**: rate limits are kept in memory, so `maxReplicas` is 1. Scaling out needs a
  shared store (e.g. Redis) first.
- **Login tokens after account deletion / password change**: access tokens are checked by signature
  only, so one issued earlier keeps working until it expires (at most 15 minutes). Refresh tokens are
  revoked immediately, so it can't be renewed. Deleted accounts have no data left to reach.
- **Storage key signing**: photo links are signed with the storage account key. Moving to a managed
  identity (user delegation SAS) and Key Vault is on the roadmap.

## Costs

About **$1/month plus Claude usage** while the free offers last; roughly $17-25/month after the
first year, mostly the database. Details in [infra/README.md](../infra/README.md#monthly-cost).
The $10/month budget emails you well before anything unexpected; Azure for Students also stops
resources rather than billing when the credit runs out.
