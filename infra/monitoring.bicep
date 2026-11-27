// FitCheck monitoring: who gets told when something goes wrong, and when spending creeps up.
//
// Separate from main.bicep on purpose: it needs no secrets, so it can be deployed or changed
// at any time without the database password or Claude key. See infra/README.md (step 5.11).
//
// Cost: metric alerts are about $0.10/month each (4 here); the budget and saved searches are free.

targetScope = 'resourceGroup'

@description('Where alerts and budget warnings are emailed. Passed at deploy time, not stored in the repo.')
param alertEmail string

@description('Monthly spending (USD) for the whole resource group before you get warned.')
param monthlyBudget int = 10

@description('First day of the month the budget starts counting from (yyyy-MM-01).')
param budgetStartDate string = '${utcNow('yyyy-MM')}-01'

var tags = { app: 'fitcheck' }

// Existing resources created by main.bicep (looked up, not changed).
resource app 'Microsoft.App/containerApps@2024-03-01' existing = {
  name: 'fitcheck'
}
resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' existing = {
  name: 'psql-fitcheck-${uniqueString(resourceGroup().id)}'
}
resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' existing = {
  name: 'log-fitcheck'
}

// ---------- Who to tell ----------
resource owner 'Microsoft.Insights/actionGroups@2023-01-01' = {
  name: 'ag-fitcheck-owner'
  location: 'global'
  tags: tags
  properties: {
    groupShortName: 'FitCheck' // shown in SMS/email subjects, max 12 characters
    enabled: true
    emailReceivers: [
      {
        name: 'owner'
        emailAddress: alertEmail
        useCommonAlertSchema: true
      }
    ]
  }
}

// ---------- Budget ----------
// Warns by email; it never stops anything. (Azure for Students also has its own hard stop:
// when the credit runs out, resources are switched off rather than billed.)
resource budget 'Microsoft.Consumption/budgets@2023-11-01' = {
  name: 'budget-fitcheck-monthly'
  properties: {
    category: 'Cost'
    amount: monthlyBudget
    timeGrain: 'Monthly'
    timePeriod: { startDate: budgetStartDate }
    notifications: {
      halfSpent: {
        enabled: true
        operator: 'GreaterThanOrEqualTo'
        threshold: 50
        thresholdType: 'Actual'
        contactEmails: [alertEmail]
      }
      allSpent: {
        enabled: true
        operator: 'GreaterThanOrEqualTo'
        threshold: 100
        thresholdType: 'Actual'
        contactEmails: [alertEmail]
      }
      // "At this rate you'll go over by the end of the month": the earliest warning.
      forecastOver: {
        enabled: true
        operator: 'GreaterThanOrEqualTo'
        threshold: 100
        thresholdType: 'Forecasted'
        contactEmails: [alertEmail]
      }
    }
  }
}

// ---------- Alerts ----------
// Checked every 5 minutes. "Severity" only sorts them in the portal: 1 = urgent ... 3 = FYI.

resource serverErrors 'Microsoft.Insights/metricAlerts@2018-03-01' = {
  name: 'alert-fitcheck-5xx'
  location: 'global'
  tags: tags
  properties: {
    description: 'The app answered with server errors (5xx): something is broken. Check the "FitCheck: errors and warnings" log search.'
    severity: 1
    enabled: true
    scopes: [app.id]
    evaluationFrequency: 'PT5M'
    windowSize: 'PT15M'
    criteria: {
      'odata.type': 'Microsoft.Azure.Monitor.SingleResourceMultipleMetricCriteria'
      allOf: [
        {
          criterionType: 'StaticThresholdCriterion'
          name: 'serverErrors'
          metricName: 'Requests'
          metricNamespace: 'Microsoft.App/containerApps'
          dimensions: [
            { name: 'statusCodeCategory', operator: 'Include', values: ['5xx'] }
          ]
          operator: 'GreaterThan'
          threshold: 3 // a few in 15 minutes; a single one can be a fluke
          timeAggregation: 'Total'
        }
      ]
    }
    actions: [{ actionGroupId: owner.id }]
  }
}

resource restarts 'Microsoft.Insights/metricAlerts@2018-03-01' = {
  name: 'alert-fitcheck-restarts'
  location: 'global'
  tags: tags
  properties: {
    description: 'The app container crashed or failed its health checks and was restarted. Normal scale-to-zero does not count as a restart.'
    severity: 1
    enabled: true
    scopes: [app.id]
    evaluationFrequency: 'PT5M'
    windowSize: 'PT15M'
    criteria: {
      'odata.type': 'Microsoft.Azure.Monitor.SingleResourceMultipleMetricCriteria'
      allOf: [
        {
          criterionType: 'StaticThresholdCriterion'
          name: 'restarts'
          metricName: 'RestartCount'
          metricNamespace: 'Microsoft.App/containerApps'
          operator: 'GreaterThan'
          threshold: 0
          timeAggregation: 'Maximum'
        }
      ]
    }
    actions: [{ actionGroupId: owner.id }]
  }
}

resource databaseStorage 'Microsoft.Insights/metricAlerts@2018-03-01' = {
  name: 'alert-fitcheck-db-storage'
  location: 'global'
  tags: tags
  properties: {
    description: 'The database disk is over 80% full. When it is full the database turns read-only (auto-grow is off to avoid surprise costs).'
    severity: 2
    enabled: true
    scopes: [postgres.id]
    evaluationFrequency: 'PT1H'
    windowSize: 'PT1H'
    criteria: {
      'odata.type': 'Microsoft.Azure.Monitor.SingleResourceMultipleMetricCriteria'
      allOf: [
        {
          criterionType: 'StaticThresholdCriterion'
          name: 'storage'
          metricName: 'storage_percent'
          metricNamespace: 'Microsoft.DBforPostgreSQL/flexibleServers'
          operator: 'GreaterThan'
          threshold: 80
          timeAggregation: 'Average'
        }
      ]
    }
    actions: [{ actionGroupId: owner.id }]
  }
}

resource databaseCpu 'Microsoft.Insights/metricAlerts@2018-03-01' = {
  name: 'alert-fitcheck-db-cpu'
  location: 'global'
  tags: tags
  properties: {
    description: 'Database CPU above 90% for 30 minutes. The B1ms server is "burstable": long busy periods use up its CPU credits and it slows down.'
    severity: 3
    enabled: true
    scopes: [postgres.id]
    evaluationFrequency: 'PT15M'
    windowSize: 'PT30M'
    criteria: {
      'odata.type': 'Microsoft.Azure.Monitor.SingleResourceMultipleMetricCriteria'
      allOf: [
        {
          criterionType: 'StaticThresholdCriterion'
          name: 'cpu'
          metricName: 'cpu_percent'
          metricNamespace: 'Microsoft.DBforPostgreSQL/flexibleServers'
          operator: 'GreaterThan'
          threshold: 90
          timeAggregation: 'Average'
        }
      ]
    }
    actions: [{ actionGroupId: owner.id }]
  }
}

// ---------- Saved log searches (Log Analytics → Logs → Queries → "FitCheck") ----------
var searches = [
  {
    id: 'errors'
    name: 'FitCheck: errors and warnings'
    query: '''
ContainerAppConsoleLogs_CL
| where Log_s has_any (" ERROR ", " WARN ")
| project TimeGenerated, Revision = RevisionName_s, Log_s
| order by TimeGenerated desc'''
  }
  {
    id: 'ai-usage'
    name: 'FitCheck: AI stylist usage per day'
    query: '''
ContainerAppConsoleLogs_CL
| where Log_s has "Outfit suggestions:"
| extend InputTokens = toint(extract(@"input tokens: (\d+) total", 1, Log_s)),
         CacheRead = toint(extract(@"\+ (\d+) cache-read", 1, Log_s)),
         OutputTokens = toint(extract(@"\| (\d+) output tokens", 1, Log_s))
| summarize Requests = count(), InputTokens = sum(InputTokens), CacheReadTokens = sum(CacheRead),
            OutputTokens = sum(OutputTokens) by Day = bin(TimeGenerated, 1d)
| order by Day desc'''
  }
  {
    id: 'startups'
    name: 'FitCheck: app starts (cold starts and deploys)'
    query: '''
ContainerAppConsoleLogs_CL
| where Log_s has "Started FitCheckApplication"
| extend StartupSeconds = todouble(extract(@"in ([\d.]+) seconds", 1, Log_s))
| project TimeGenerated, Revision = RevisionName_s, Image = ContainerImage_s, StartupSeconds
| order by TimeGenerated desc'''
  }
  {
    id: 'platform'
    name: 'FitCheck: platform warnings (crashes, failed probes)'
    query: '''
ContainerAppSystemLogs_CL
| where Type_s == "Warning"
| project TimeGenerated, Reason = Reason_s, Revision = RevisionName_s, Log_s
| order by TimeGenerated desc'''
  }
]

resource savedSearches 'Microsoft.OperationalInsights/workspaces/savedSearches@2020-08-01' = [
  for s in searches: {
    parent: logs
    name: 'fitcheck-${s.id}'
    properties: {
      category: 'FitCheck'
      displayName: s.name
      query: s.query
    }
  }
]
