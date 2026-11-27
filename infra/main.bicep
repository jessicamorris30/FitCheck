// FitCheck on Azure: "infrastructure as code".
//
// This file DESCRIBES every Azure resource FitCheck needs. Azure compares it with what
// already exists and creates or updates only the differences, so running it twice is safe.
// See infra/README.md for how to run it and what it costs.
//
// It's deployed in stages, switched on by parameters:
//   5.8  core:  logs, Container Apps environment, PostgreSQL, Blob Storage      (always)
//        email: Azure Communication Services + your domain                    (emailDomain set;
//               connected for sending once verified: emailDomainVerified=true in 5.9)
//   5.9  app:   the FitCheck container itself                                   (appImage set)

targetScope = 'resourceGroup'

// ---------- Parameters (the inputs) ----------

@description('Azure region for everything (defaults to the region of the resource group; FitCheck uses North Central US).')
param location string = resourceGroup().location

@description('Database admin user name (not a secret).')
param postgresAdminUser string = 'fitcheckadmin'

@secure()
@description('Database admin password. Typed at deploy time, never saved in a file.')
param postgresAdminPassword string

@description('Optional: your home IP address, to connect to the database with psql. Empty = no access from outside Azure.')
param adminIpAddress string = ''

@description('Optional: your domain for email, e.g. fitcheck.example. Empty = skip email resources for now.')
param emailDomain string = ''

@description('Step 5.9: true once the DNS records for emailDomain are added and Azure shows the domain as Verified. Azure refuses to connect an unverified domain.')
param emailDomainVerified bool = false

@description('Step 5.9: the container image to run, e.g. ghcr.io/<owner>/fitcheck:<commit>. Empty = do not create the app yet.')
param appImage string = ''

@description('Step 5.9: the public address of the site, e.g. https://fitcheck.example')
param siteUrl string = ''

// Secrets for the app (step 5.9). Defaults are empty so the core can be deployed without them.
@secure()
param jwtSecret string = ''
@secure()
param anthropicApiKey string = ''
@secure()
param smtpUsername string = ''
@secure()
param smtpPassword string = ''

// ---------- Names ----------
// Some names must be unique across ALL of Azure, so they get a short suffix that's random-looking
// but always the same for this resource group (so re-running doesn't create duplicates).
var suffix = uniqueString(resourceGroup().id)
var storageAccountName = 'stfitcheck${suffix}' // 3-24 lowercase letters/digits, no dashes
var postgresServerName = 'psql-fitcheck-${suffix}'
var databaseName = 'fitcheck'
var imagesContainerName = 'clothing-images'
var tags = { app: 'fitcheck' }

// ---------- Logs ----------
// Collects the app's console output and Azure's own logs. First 5 GB/month are free.
resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: 'log-fitcheck'
  location: location
  tags: tags
  properties: {
    sku: { name: 'PerGB2018' }
    retentionInDays: 30
  }
}

// ---------- Container Apps environment ----------
// The "neighbourhood" the app container lives in: networking, logging, scaling.
// Consumption plan = pay only while a container is actually running (scale to zero).
// The mode must be set explicitly: left out, Azure now picks "Express", which can't use
// custom domains. (This is why a newer API version is used here.)
resource containerEnv 'Microsoft.App/managedEnvironments@2025-10-02-preview' = {
  name: 'cae-fitcheck'
  location: location
  tags: tags
  properties: {
    // Bicep's type list doesn't know this property yet, but the Azure API does.
    #disable-next-line BCP037
    environmentMode: 'WorkloadProfiles'
    workloadProfiles: [
      {
        name: 'Consumption'
        workloadProfileType: 'Consumption'
      }
    ]
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logs.properties.customerId
        sharedKey: logs.listKeys().primarySharedKey
      }
    }
  }
}

// ---------- PostgreSQL ----------
// Burstable B1ms (1 vCPU, 2 GB RAM) with 32 GB: covered by the 12-month free offer.
resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: postgresServerName
  location: location
  tags: tags
  sku: {
    name: 'Standard_B1ms'
    tier: 'Burstable'
  }
  properties: {
    version: '16' // same major version as docker-compose.yml and CI
    administratorLogin: postgresAdminUser
    administratorLoginPassword: postgresAdminPassword
    storage: {
      storageSizeGB: 32
      autoGrow: 'Disabled' // growing costs money; an alert in 5.11 warns first
    }
    backup: {
      backupRetentionDays: 7 // daily automatic backups, restorable to any point in the last 7 days
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: { mode: 'Disabled' } // a standby copy would double the price
    network: { publicNetworkAccess: 'Enabled' } // reachable only through the firewall rules below
  }

  resource db 'databases' = {
    name: databaseName
    properties: {
      charset: 'UTF8'
      collation: 'en_US.utf8'
    }
  }

  // The special 0.0.0.0 rule means "services running inside Azure", like our container.
  // Connections still need the password, and SSL is required by default.
  resource allowAzure 'firewallRules' = {
    name: 'AllowAzureServices'
    properties: {
      startIpAddress: '0.0.0.0'
      endIpAddress: '0.0.0.0'
    }
  }

  resource allowAdmin 'firewallRules' = if (!empty(adminIpAddress)) {
    name: 'AllowAdminIp'
    properties: {
      startIpAddress: adminIpAddress
      endIpAddress: adminIpAddress
    }
  }
}

// ---------- Blob Storage (clothing photos) ----------
resource storage 'Microsoft.Storage/storageAccounts@2023-05-01' = {
  name: storageAccountName
  location: location
  tags: tags
  kind: 'StorageV2'
  sku: { name: 'Standard_LRS' } // 3 copies in one data centre: cheapest, fine for photos
  properties: {
    accessTier: 'Hot'
    allowBlobPublicAccess: false // nobody can make a container public by accident
    minimumTlsVersion: 'TLS1_2'
    supportsHttpsTrafficOnly: true
    // The app signs photo links (SAS) with the account key, so key access stays on.
    // (Switching to managed identity is on the "later" list.)
    allowSharedKeyAccess: true
  }

  resource blobService 'blobServices' = {
    name: 'default'

    resource images 'containers' = {
      name: imagesContainerName
      properties: { publicAccess: 'None' } // private: photos only load through signed links
    }
  }
}

// ---------- Email (Azure Communication Services) ----------
// Only created once you have a domain. Verifying it (DNS records) and SMTP sign-in are step 5.9.
resource emailService 'Microsoft.Communication/emailServices@2023-04-01' = if (!empty(emailDomain)) {
  name: 'ecs-fitcheck-${suffix}'
  location: 'global'
  tags: tags
  properties: { dataLocation: 'United States' }

  resource domain 'domains' = {
    name: empty(emailDomain) ? 'unused' : emailDomain
    location: 'global'
    properties: {
      domainManagement: 'CustomerManaged' // your own domain (not an azurecomm.net one)
      userEngagementTracking: 'Disabled' // no tracking pixels in our emails
    }

    resource noReply 'senderUsernames' = {
      name: 'no-reply'
      properties: {
        username: 'no-reply'
        displayName: 'FitCheck'
      }
    }
  }
}

resource communication 'Microsoft.Communication/communicationServices@2023-04-01' = if (!empty(emailDomain)) {
  name: 'acs-fitcheck-${suffix}'
  location: 'global'
  tags: tags
  properties: {
    dataLocation: 'United States'
    linkedDomains: emailDomainVerified ? [emailService::domain.id] : []
  }
}

// ---------- The app (step 5.9) ----------
var storageConnectionString = 'DefaultEndpointsProtocol=https;AccountName=${storage.name};AccountKey=${storage.listKeys().keys[0].value};EndpointSuffix=${environment().suffixes.storage}'

resource app 'Microsoft.App/containerApps@2024-03-01' = if (!empty(appImage)) {
  name: 'fitcheck'
  location: location
  tags: tags
  properties: {
    managedEnvironmentId: containerEnv.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: true // reachable from the internet
        targetPort: 8080
        allowInsecure: false // http:// is redirected to https://
      }
      // Stored encrypted by Azure; the container sees them as environment variables.
      secrets: [
        { name: 'db-password', value: postgresAdminPassword }
        { name: 'jwt-secret', value: jwtSecret }
        { name: 'storage-connection-string', value: storageConnectionString }
        { name: 'anthropic-api-key', value: anthropicApiKey }
        { name: 'smtp-username', value: smtpUsername }
        { name: 'smtp-password', value: smtpPassword }
      ]
    }
    template: {
      containers: [
        {
          name: 'fitcheck'
          image: appImage
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: [
            { name: 'SPRING_PROFILES_ACTIVE', value: 'prod' }
            { name: 'DB_URL', value: 'jdbc:postgresql://${postgres.properties.fullyQualifiedDomainName}:5432/${databaseName}?sslmode=require' }
            { name: 'POSTGRES_USER', value: postgresAdminUser }
            { name: 'POSTGRES_PASSWORD', secretRef: 'db-password' }
            { name: 'JWT_SECRET', secretRef: 'jwt-secret' }
            { name: 'AZURE_STORAGE_CONNECTION_STRING', secretRef: 'storage-connection-string' }
            { name: 'ANTHROPIC_API_KEY', secretRef: 'anthropic-api-key' }
            { name: 'MAIL_HOST', value: 'smtp.azurecomm.net' }
            { name: 'MAIL_PORT', value: '587' }
            { name: 'MAIL_USERNAME', secretRef: 'smtp-username' }
            { name: 'MAIL_PASSWORD', secretRef: 'smtp-password' }
            { name: 'MAIL_FROM', value: 'FitCheck <no-reply@${emailDomain}>' }
            { name: 'FRONTEND_URL', value: siteUrl }
            { name: 'CORS_ALLOWED_ORIGINS', value: siteUrl }
          ]
          probes: [
            {
              // Checked every 3 s so a visitor waiting on a cold start gets in as soon as the app is
              // up (it starts in ~5 s with the startup cache). Up to 10 misses are allowed (Azure's
              // maximum): 3 + 10 × 3 = ~33 s before Azure gives up and restarts it.
              // (Was: first check after 10 s, then every 10 s: up to 10 s of pure waiting.)
              type: 'Startup'
              httpGet: { path: '/actuator/health/liveness', port: 8080 }
              initialDelaySeconds: 3
              periodSeconds: 3
              failureThreshold: 10
            }
            {
              type: 'Liveness' // stuck? restart it
              httpGet: { path: '/actuator/health/liveness', port: 8080 }
              periodSeconds: 30
              failureThreshold: 3
            }
            {
              type: 'Readiness' // not ready (e.g. database down)? stop sending it visitors
              httpGet: { path: '/actuator/health/readiness', port: 8080 }
              // Visitors are only let in after this passes, so check often (was every 15 s).
              periodSeconds: 3
              failureThreshold: 5
            }
          ]
        }
      ]
      scale: {
        minReplicas: 0 // sleep when nobody is using it: ~free
        maxReplicas: 1 // the rate limiter keeps its counts in memory, so exactly one copy
        rules: [
          {
            name: 'http'
            http: { metadata: { concurrentRequests: '50' } }
          }
        ]
      }
    }
  }
}

// ---------- Outputs (printed after deploying; no secrets here) ----------
output postgresHost string = postgres.properties.fullyQualifiedDomainName
output storageAccount string = storage.name
output containerAppsEnvironment string = containerEnv.name
output appUrl string = empty(appImage) ? '' : 'https://${app!.properties.configuration.ingress.fqdn}'
output emailVerificationRecords object = empty(emailDomain) ? {} : emailService::domain!.properties.verificationRecords
