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

Prices are approximate (USD, Canada Central, late 2026). Check the
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

1. **Azure free account**: <https://azure.microsoft.com/free>. It needs a card to confirm your identity,
   but you aren't charged during the trial. You get $200 credit for 30 days plus 12 months of free services.
   When the 30 days are up, Azure asks you to *upgrade to pay-as-you-go* to keep the 12 free months. Without that, everything stops.
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

# 3. The resource group, in Canada Central (Toronto)
az group create --name rg-fitcheck --location canadacentral

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

Replace `<your-domain>` with the domain you bought, without `https://` or `www`. If you don't have one yet, leave
off `emailDomain=...`. The email resources are added when you run step 6 again later.

To let `psql` on your computer reach the database, add `adminIpAddress=<your IP>`
(find it at <https://ifconfig.me>).

**If step 6 fails with "location is restricted"**: free subscriptions sometimes can't create PostgreSQL
in a given region. Delete the group (`az group delete -n rg-fitcheck`) and repeat steps 3–6 with
`--location canadaeast` (Quebec City).

## Check it worked

```powershell
az resource list -g rg-fitcheck --query "[].{name:name, type:type}" -o table
```

You should see the Log Analytics workspace, the Container Apps environment, the PostgreSQL server
and the storage account, plus the two email resources if you passed a domain. The app itself
doesn't exist yet: the image registry and deploy pipeline come in step 5.9.

## Turning it all off

```powershell
az group delete --name rg-fitcheck   # deletes EVERYTHING in it, including the database and photos
```
