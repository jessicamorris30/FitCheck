# FitCheck on Azure

[`main.bicep`](main.bicep) describes everything FitCheck runs on in Azure. Deploying it creates
whatever is missing and updates whatever changed; running it again with the same inputs changes
nothing. Secrets are typed into your terminal at deploy time and are never saved in this repo.

| Resource | What it's for | Name |
| --- | --- | --- |
| Resource group | A folder holding everything below; delete it and it's all gone | `rg-fitcheck` |
| Log Analytics workspace | The app's logs | `log-fitcheck` |
| Container Apps environment | Where the app container runs | `cae-fitcheck` |
| PostgreSQL Flexible Server (B1ms) | The database | `psql-fitcheck-<suffix>` |
| Storage account + `clothing-images` container | The photos | `stfitcheck<suffix>` |
| Email Communication Service + Communication Services | Sending email from your domain | `ecs-` / `acs-fitcheck-<suffix>` |
| Container App (step 5.9) | FitCheck itself | `fitcheck` |

## Monthly cost

Prices are approximate (USD, North Central US, late 2026). Check the
[Azure pricing calculator](https://azure.microsoft.com/pricing/calculator/) for exact numbers.

| Item | First 12 months | After that |
| --- | --- | --- |
| PostgreSQL B1ms + 32 GB | **$0** (free offer: 750 h/month + 32 GB) | ~$15–20 |
| Container App, 0.5 vCPU / 1 GB, scales to zero | **$0** (monthly free grant covers ~100 active hours) | $0–3 |
| Blob Storage (a few GB of photos) | **$0** (5 GB free) | < $1 |
| Log Analytics | **$0** (first 5 GB/month free) | $0 |
| Email | ~$0 (fractions of a cent per email) | ~$0 |
| Domain | ~$10–15 **per year** at the registrar | same |
| Claude API | Pay per use, capped by your workspace spend limit | same |
| **Total** | **≈ $1/month + Claude usage** | **≈ $17–25/month + Claude usage** |

The one recurring cost to keep an eye on is PostgreSQL once the free year ends. The budget alert
in step 5.11 will email you before spending gets anywhere unexpected.

## Before you deploy (do these yourself)

1. **An Azure subscription.** FitCheck uses **Azure for Students**:
   - $100 credit for 12 months, renewable while you're a student
   - no card, so there are never surprise charges: when the credit runs out, resources stop

   Student subscriptions can only deploy to a few regions. To list yours:
   `az policy assignment list --query "[].parameters.listOfAllowedLocations.value" -o json`.
   FitCheck uses **North Central US** (Chicago), the closest allowed region to Ontario.

   (The regular [free account](https://azure.microsoft.com/free) works too: it gives $200 for 30 days plus 12 free months,
   needs a card, and allows any region.)

   If `az login` says *No subscriptions found*, the subscription is in another directory. Copy the
   Directory ID from the portal (account picture → Switch directory) and run `az login --tenant <Directory ID>`.
2. **Domain**: buy one at [Cloudflare Registrar](https://www.cloudflare.com/products/registrar/)
   or [Porkbun](https://porkbun.com). Both sell at cost with free WHOIS privacy. A `.com` is about
   $10–12/year. You'll add DNS records there in step 5.9.
3. **Claude production key**: in the [Claude Console](https://platform.claude.com):
   - create a workspace called **FitCheck prod**
   - set a monthly **spend limit** on it, e.g. $10
   - create an API key *inside that workspace*

   Keep the key in a password manager until step 5.9.
   Don't put it in `.env` or paste it into chats. Using a separate key means the dev key can stay on your
   laptop, and a leaked or overused prod key can't cost more than the limit.
4. **Azure CLI**: already installed (`az version`). The Bicep compiler is installed too (`az bicep version`).

## Deploy the core (step 5.8)

Run these in PowerShell from the repo root.

```powershell
# 1. Sign in (opens your browser)
az login

# 2. New subscriptions must switch on each Azure service before first use (one time, ~1 min each)
foreach ($p in "Microsoft.App","Microsoft.OperationalInsights","Microsoft.DBforPostgreSQL","Microsoft.Storage","Microsoft.Communication") {
  az provider register --namespace $p --wait
}

# 3. The resource group, in North Central US (Chicago). Everything inside uses the same region.
az group create --name rg-fitcheck --location northcentralus

# 4. A strong database password, generated on your machine and copied to your clipboard.
#    Paste it into your password manager NOW: you'll need it again in step 5.9.
$bytes = [byte[]]::new(24); [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$pg = "Fc1-" + ([Convert]::ToBase64String($bytes) -replace '[+/=]', 'x')
Set-Clipboard $pg

# 5. Preview: lists what WOULD be created, without creating anything
az deployment group what-if -g rg-fitcheck -f infra/main.bicep -p postgresAdminPassword=$pg emailDomain=<your-domain>

# 6. Create it (~5-10 minutes; PostgreSQL is the slow one)
az deployment group create -g rg-fitcheck -f infra/main.bicep -p postgresAdminPassword=$pg emailDomain=<your-domain> `
  --query properties.outputs
```

Copy only the commands, not the ```` ``` ```` lines around them.

Replace `<your-domain>` with the domain you bought, without `https://` or `www`. This creates the
email resources, but the domain only gets connected for sending in step 5.9: Azure refuses to connect it
until DNS records prove you own it. If you don't have one yet, leave
off `emailDomain=...`. The email resources are added when you run step 6 again later.

To let `psql` on your computer reach the database, add `adminIpAddress=<your IP>`
(find it at <https://ifconfig.me>).

**If step 6 fails with "location is restricted" or "RequestDisallowedByPolicy"**: the region isn't
allowed for your subscription, or PostgreSQL isn't offered there. Delete the group
(`az group delete -n rg-fitcheck`) and repeat steps 3–6 with another region from your allowed list,
e.g. `--location westus2`.

## Check it worked

```powershell
az resource list -g rg-fitcheck --query "[].{name:name, type:type}" -o table
```

You should see the Log Analytics workspace, the Container Apps environment, the PostgreSQL server
and the storage account, plus the two email resources if you passed a domain. The app itself
doesn't exist yet: the image registry and deploy pipeline come in step 5.9.

## Email (step 5.9b)

1. **DNS records** at Porkbun (values from the deployment output `emailVerificationRecords`, or the
   email domain in the portal):

   | Type | Host | Value |
   | --- | --- | --- |
   | TXT | *(blank)* | `ms-domain-verification=…` |
   | TXT | *(blank)* | `v=spf1 include:spf.protection.outlook.com include:_spf.porkbun.com -all` (one SPF record only: merge into an existing one) |
   | CNAME | `selector1-azurecomm-prod-net._domainkey` | `selector1-azurecomm-prod-net._domainkey.azurecomm.net` |
   | CNAME | `selector2-azurecomm-prod-net._domainkey` | `selector2-azurecomm-prod-net._domainkey.azurecomm.net` |
   | TXT | `_dmarc` | `v=DMARC1; p=none` |

2. **Verify** in the portal: Email Communication Service → Provision domains → your domain → Verify
   (Domain first, then SPF and DKIM). DMARC isn't checked by Azure.
3. **Connect the domain** by deploying with `emailDomainVerified=true`. From then on, **always pass
   `emailDomainVerified=true`**, or the deployment disconnects the domain again.
4. **Sending identity**: `.\infra\setup-email-sender.ps1`. It creates the app registration that SMTP
   signs in with and prints the SMTP username. The password is a client secret of that app; it's
   created fresh in the deploy command (step 5.9c), so it's never written down anywhere.

## Turning it all off

```powershell
az group delete --name rg-fitcheck   # deletes EVERYTHING in it, including the database and photos
```
