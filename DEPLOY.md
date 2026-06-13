# Deploy guide (beginner-friendly)

This guide gets your Spring Boot app running on AWS and **auto-deploying every time you push code to GitHub**.

You do **not** need Java or Maven on your laptop. GitHub builds everything for you.

---

## What you are building (big picture)

```
Your laptop                    GitHub                    AWS
──────────                    ──────                    ───

edit code  ──git push──►  builds the app  ──deploy──►  runs 24/7 on a server
                          runs tests                 shows "Hello World" in browser
                                                     talks to DynamoDB
```

AWS service used: **Elastic Beanstalk** (AWS manages a small server for you).

---

## Before you start — checklist

| You need | Do you have it? |
|----------|-----------------|
| GitHub account | Your repo: `compilerNayan/water_meter_data_injection_service` |
| AWS account | Same one as `water_meter_service` (account `748359027058`) |
| Git on your laptop | To push code |
| ~45 minutes | One-time setup |

**Cost:** roughly **$8–15/month** for a small always-on server (`t3.micro` or `t3.small`).

---

## PART 1 — Create the server on AWS (one time, ~15 min)

### Step 1 — Open AWS

1. Go to https://console.aws.amazon.com
2. Sign in
3. Top-right corner: set region to **Asia Pacific (Mumbai) / ap-south-1**

### Step 2 — Open Elastic Beanstalk

1. In the search bar at the top, type **Elastic Beanstalk**
2. Click **Elastic Beanstalk**

### Step 3 — Create the application

1. Click **Create application**
2. Fill in:
   - **Application name:** `water-meter-data-injection`
   - **Platform:** **Java**
   - **Platform branch:** **Corretto 17 running on 64bit Amazon Linux 2023**
   - **Application code:** choose **Sample application** (we replace it later via GitHub)
3. Click **Next**

### Step 4 — Environment settings

1. **Environment name:** `water-meter-data-injection-env`  
   (must match the name in `.github/workflows/deploy.yml`)
2. **Domain:** leave the auto-generated name (e.g. `water-meter-data-injection-env.eba-xxxxx.ap-south-1.elasticbeanstalk.com`)
3. **Presets:** choose **Single instance (free tier eligible)** if you see it, otherwise **Single instance**
4. Click **Next** through the other screens (defaults are fine)
5. Click **Create environment**

### Step 5 — Wait

- Status will show **Launching** then **Ok** (5–10 minutes)
- When done, you get a URL like:  
  `http://water-meter-data-injection-env.eba-xxxxx.ap-south-1.elasticbeanstalk.com`
- Opening it now shows the **sample** app — that is normal until Part 4

**Write down your environment URL** — you will need it later.

---

## PART 2 — Let the app use DynamoDB (one time, ~10 min)

Your app reads/writes the same DynamoDB tables as `water_meter_service`. The **server** needs permission.

### Step 6 — Find the server’s security role

1. Go to **IAM** (search “IAM” in AWS console)
2. Click **Roles** on the left
3. Search for: `aws-elasticbeanstalk-ec2`
4. Click the role named something like **`aws-elasticbeanstalk-ec2-role`**

### Step 7 — Add DynamoDB permission

1. On the role page, click **Add permissions** → **Create inline policy**
2. Click **JSON** tab
3. Paste this exactly:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:Query",
        "dynamodb:BatchWriteItem",
        "dynamodb:DescribeTable"
      ],
      "Resource": "arn:aws:dynamodb:ap-south-1:748359027058:table/WaterMeter*"
    }
  ]
}
```

4. Click **Next**
5. **Policy name:** `WaterMeterDynamoDbAccess`
6. Click **Create policy**

Done — the running app can now access your water meter tables.

---

## PART 3 — Let GitHub deploy to AWS (one time, ~20 min)

GitHub needs permission to upload your app to AWS **without storing AWS passwords**.

### Step 8 — GitHub OIDC provider (skip if you already did this for water_meter_service)

1. **IAM → Identity providers**
2. If you already see `token.actions.githubusercontent.com`, skip to Step 9
3. Otherwise: **Add provider → OpenID Connect**
   - URL: `https://token.actions.githubusercontent.com`
   - Audience: `sts.amazonaws.com`
   - **Add provider**

### Step 9 — Create role for GitHub Actions

1. **IAM → Roles → Create role**
2. **Trusted entity type:** Web identity
3. **Identity provider:** `token.actions.githubusercontent.com`
4. **Audience:** `sts.amazonaws.com`
5. Click **Next**
6. Search and attach: **`AdministratorAccess-AWSElasticBeanstalk`**
7. **Role name:** `github-actions-data-injection-deploy`
8. Click **Create role**

### Step 10 — Lock the role to your repo only

1. Open role **`github-actions-data-injection-deploy`**
2. **Trust relationships** tab → **Edit trust policy**
3. Replace everything with:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::748359027058:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:compilerNayan/water_meter_data_injection_service:*"
        }
      }
    }
  ]
}
```

4. Click **Update policy**

---

## PART 4 — Push code and deploy (every time you change the app)

### Step 11 — Push from your laptop

Open Terminal and run:

```bash
cd /Users/sexydevil/src/automation_src/fresh/water_meter_data_injection_service

git add .
git commit -m "Add deploy workflow"
git push
```

### Step 12 — Watch the deploy on GitHub

1. Go to https://github.com/compilerNayan/water_meter_data_injection_service
2. Click **Actions** tab
3. Click the running workflow **Deploy Data Injection Service**
4. Wait for a **green checkmark** (~5–10 min first time)

If you see a **red X**, open the failed step and read the error (see Troubleshooting below).

### Step 13 — Test your live app

Open your Beanstalk URL in a browser:

| URL | What you should see |
|-----|---------------------|
| `http://YOUR-URL/` | `Hello World` |
| `http://YOUR-URL/actuator/health` | JSON with `"status":"UP"` and `"dynamodb":{"status":"UP"}` |

Replace `YOUR-URL` with the URL from Step 5.

---

## MQTT water data ingestion (24×7)

This service subscribes to AWS IoT MQTT topics (`+/water_meter/#`) on startup and writes water telemetry to DynamoDB.

After deploy, check logs in Beanstalk → **Logs** → look for:

```
MQTT ingestion subscriber active on +/water_meter/#
Subscribed to MQTT topic filter +/water_meter/#
```

Health check: `/actuator/health` should show `"mqtt":{"status":"UP"}` when connected.

**Important:** Only one service should subscribe to MQTT. `water_meter_service` Lambda has MQTT disabled (`DEVICE_MQTT_INGESTION_ENABLED=false`). Redeploy that Lambda stack if it was still subscribing.

---

## Device stream TCP (live 1s water pulses)

IoT devices send **newline-delimited JSON** over plain TCP. The service stores the latest pulse in memory and pushes `water_flow` to tenant WebSocket subscribers (`/ws/live`). MQTT `water/1s` is ignored for live telemetry; `water/30m` remains authoritative via MQTT.

| Variable | Default | Purpose |
|----------|---------|---------|
| `DEVICE_STREAM_ENABLED` | `true` | Enable TCP listener |
| `DEVICE_STREAM_PORT` | `9100` | TCP port (not proxied by nginx) |

**Open port 9100** on the Beanstalk EC2 security group so devices can reach the instance directly.

Example pulse line (one per second while connected):

```json
{"tenantId":"63tk0y1","serialNumber":"QJPDXN094","ml":45,"cumulativeLiters":123.456,"ts":"2026-06-13T10:00:05Z"}
```

Server replies per line: `{"ok":true}` or `{"ok":false,"error":"..."}`.

Verify after deploy:

```bash
nc YOUR-BEANSTALK-HOST 9100
{"tenantId":"63tk0y1","serialNumber":"QJPDXN094","ml":45,"cumulativeLiters":123.4,"ts":"2026-06-13T10:00:05Z"}
```

---

## Tenant live WebSocket (`/ws/live`)

Flutter apps connect here for tenant-scoped live pushes (`water_flow`, `bucket_30m`). `water_flow` is sourced from the device stream socket, not MQTT `water/1s`.

In Beanstalk → **Configuration** → **Software** → **Environment properties**, set:

| Variable | Example | Purpose |
|----------|---------|---------|
| `COGNITO_ISSUER_URI` | `https://cognito-idp.ap-south-1.amazonaws.com/ap-south-1_vm19Xv95r` | JWT validation for subscribe + `/api/**` |
| `LIVE_UPDATES_ENABLED` | `true` | Enable WebSocket hub and live broadcast |
| `DEVICE_STREAM_ENABLED` | `true` | Enable device TCP stream on port 9100 |

WebSocket URL for the app (replace with your Beanstalk hostname):

`ws://water-meter-data-injection-env.eba-xxxxx.ap-south-1.elasticbeanstalk.com/ws/live`

Nginx WebSocket proxy headers are in `.ebextensions/nginx/conf.d/websocket.conf`. Single-instance Beanstalk works without sticky sessions; ALB + multiple instances would need stickiness later.

Verify with `wscat` after deploy:

```bash
wscat -c ws://YOUR-URL/ws/live
# then send: {"type":"subscribe","tenantId":"YOUR_TENANT","token":"YOUR_COGNITO_JWT"}
```

---

## Day to day (after setup)

Whenever you change code:

```bash
git add .
git commit -m "Describe what you changed"
git push
```

GitHub rebuilds and redeploys automatically. Wait for the green check in **Actions**, then refresh your browser.

---

## Troubleshooting

| Problem | What to do |
|---------|------------|
| GitHub Action: **Could not assume role with OIDC** | Step 10 trust policy must say exactly `compilerNayan/water_meter_data_injection_service` |
| GitHub Action: **No Environment found** | Beanstalk environment name must be `water-meter-data-injection-env` (Step 4) |
| GitHub Action: **Access Denied** | Role needs `AdministratorAccess-AWSElasticBeanstalk` (Step 9) |
| GitHub Action: **Region not specified** | Fixed in workflow — push latest code (uses `region`, not `aws_region`) |
| Browser shows old sample app | Wait for deploy to finish; in Beanstalk console check **Health** is green |
| `/actuator/health` shows dynamodb **DOWN** | Do Part 2 again (Step 7) — EC2 role missing DynamoDB policy |
| Beanstalk health **Severe** | Beanstalk → your environment → **Logs** → **Request Logs** → **Last 100 Lines** |
| Browser shows **502 Bad Gateway** | App must listen on port **5000** on Beanstalk (fixed in `Procfile`); push latest code and redeploy |

---

## Quick reference

| Thing | Value |
|-------|-------|
| AWS region | `ap-south-1` (Mumbai) |
| AWS account | `748359027058` |
| GitHub repo | `compilerNayan/water_meter_data_injection_service` |
| Beanstalk application | `water-meter-data-injection` |
| Beanstalk environment | `water-meter-data-injection-env` |
| GitHub IAM role | `github-actions-data-injection-deploy` |
| EC2 IAM role (DynamoDB) | `aws-elasticbeanstalk-ec2-role` |

---

## What you do NOT need on your laptop

- Java
- Maven
- AWS CLI

## What you DO need

- Git
- GitHub account (you have it)
- AWS account (you have it)
- One-time setup in Parts 1–3
