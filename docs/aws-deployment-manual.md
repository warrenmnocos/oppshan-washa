# AWS Deployment Guide — Manual (Console Only)

Step-by-step deployment of `washa` to AWS using **only the AWS Management Console** (web UI). No CLI, no Terraform — every action is a click. For the automated paths, see [`aws-deployment-cli.md`](aws-deployment-cli.md) and [`aws-deployment-terraform.md`](aws-deployment-terraform.md); for when something breaks, [`aws-deployment-recovery.md`](aws-deployment-recovery.md).

**Target architecture (same as the CLI and Terraform guides):**
- One **GraalVM-native arm64 Lambda** (`provided.al2023`, 256 MB) that serves the Angular SPA and `/api/**`
- A **Lambda Function URL** with `AWS_IAM` auth — reachable only by CloudFront
- **CloudFront** in front, signing origin requests with an **Origin Access Control** (sigv4)
- **ACM** certificate in **us-east-1** (CloudFront requires it there), DNS-validated
- **Route 53** alias `washa.oppshan.com` → CloudFront
- **SSM Parameter Store** for runtime config/secrets; **Neon** PostgreSQL (external, managed)
- **GitHub Actions OIDC** federation for code deploys (no long-lived AWS key)
- Resulting URL: `https://washa.oppshan.com`

Everything lives in **ap-northeast-1 (Tokyo)** except the ACM certificate (**us-east-1**). There is no EC2, no SSH, and no file storage — nothing to log into.

**Cost:** $0/month in steady state — Lambda, CloudFront, and ACM stay inside the always-free tiers, Neon's free tier autosuspends, and the only standing charge is the existing ~$0.50/mo Route 53 hosted zone shared across `oppshan.com`.

---

## ⚠️ Before you start — fill these in

Placeholders are shown as `<UPPERCASE>`. Have these ready:

- **`<ACCOUNT_ID>`** — your 12-digit AWS account number (top-right of the console). Used in Phases 3 and 4.
- **The Neon connection details** — JDBC URL (`jdbc:postgresql://<host>/<db>?sslmode=require`), username, and password for the `oppshan` database. Provisioned separately at neon.tech (free tier).
- **The Google OAuth client** — client ID + client secret for the Web application, from the Google Cloud console. The redirect URI is added in Phase 9.
- **The token-encryption secret** — any strong random string (used to encrypt the OIDC session cookie).
- **The allowlist JSON** — the two-person allowlist, e.g. `[{"firstName":"...","lastName":"...","emails":["..."]}]`.

**Already provisioned (shared with `oppshan-files`) — confirm, don't recreate:**
- The **AWS account** and an `oppshan-admin` IAM user (sign in as that, never root).
- The **Route 53 hosted zone** for `oppshan.com` (Phase 1 confirms it).
- The **GitHub Actions OIDC provider** (`token.actions.githubusercontent.com`) — Phase 3 reuses it.

---

## Conventions

Every step is a click in the AWS Console; JSON snippets are pasted into console fields. Pick **ap-northeast-1 (Tokyo)** in the region selector and stay there for every step **except Phase 5 (ACM), which must be done in us-east-1** — the guide calls this out explicitly. Names are fixed: the function is `washa`, the SSM prefix is `/oppshan/washa/`, matching `cd.yml` and `application.properties`.

---

## Phase 1: Confirm the shared prerequisites

washa shares an AWS account with `oppshan-files`, so the account, domain, and GitHub OIDC provider already exist. Confirm before building.

### 1.1 Sign in as the admin user
1. Sign in at the account sign-in URL as `oppshan-admin` (never root).
2. Set the region selector (top-right) to **Asia Pacific (Tokyo) ap-northeast-1**.

### 1.2 Confirm the Route 53 hosted zone
1. Console search → **Route 53** → **Hosted zones**.
2. Confirm `oppshan.com` is listed, type **Public**. Click it and note the **Hosted zone ID** (you'll select this zone in Phases 5 and 8). Route 53 is global — no region needed.

### 1.3 Confirm the GitHub OIDC provider
1. Console search → **IAM** → **Identity providers**.
2. Confirm `token.actions.githubusercontent.com` is present (audience `sts.amazonaws.com`). If it is **not** (fresh account), add it: **Add provider** → OpenID Connect → URL `https://token.actions.githubusercontent.com` → **Get thumbprint** → Audience `sts.amazonaws.com` → **Add provider**.

---

## Phase 2: SSM Parameter Store

Create the seven runtime parameters under `/oppshan/washa/`. Region: **ap-northeast-1**.

For **each** parameter: Console search → **Systems Manager** → **Parameter Store** → **Create parameter**, then set Name, Tier **Standard**, Type, and Value as below. (Names match the env var the Lambda reads, matching `oppshan-files`' convention.)

| Name | Type | Value |
|---|---|---|
| `/oppshan/washa/GOOGLE_CLIENT_ID` | SecureString | your Google OAuth client ID |
| `/oppshan/washa/GOOGLE_CLIENT_SECRET` | SecureString | your Google OAuth client secret |
| `/oppshan/washa/TOKEN_ENCRYPTION_SECRET` | SecureString | a strong random string |
| `/oppshan/washa/QUARKUS_DATASOURCE_JDBC_URL` | String | `jdbc:postgresql://<neon-host>/oppshan?sslmode=require` (no password inline) |
| `/oppshan/washa/QUARKUS_DATASOURCE_USERNAME` | String | the Neon username |
| `/oppshan/washa/QUARKUS_DATASOURCE_PASSWORD` | SecureString | the Neon password |
| `/oppshan/washa/WASHA_ALLOWED_IDENTITIES` | SecureString | the allowlist JSON |

For SecureString, leave the KMS key as the default (`alias/aws/ssm`). Click **Create parameter** for each.

---

## Phase 3: IAM roles

### 3.1 Lambda execution role
1. **IAM** → **Roles** → **Create role**.
2. Trusted entity: **AWS service** → use case **Lambda** → **Next**.
3. Skip the managed-policy step (we attach an inline one) → **Next**.
4. Role name: `washa-lambda-exec` → **Create role**.
5. Open the role → **Add permissions** → **Create inline policy** → **JSON** tab → paste (substitute `<ACCOUNT_ID>`):
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [{
       "Sid": "WriteLogs",
       "Effect": "Allow",
       "Action": ["logs:CreateLogStream", "logs:PutLogEvents"],
       "Resource": "arn:aws:logs:ap-northeast-1:<ACCOUNT_ID>:log-group:/aws/lambda/washa:*"
     }]
   }
   ```
6. **Next** → name `logs` → **Create policy**.

### 3.2 GitHub Actions deploy role
1. **IAM** → **Roles** → **Create role**.
2. Trusted entity: **Web identity** → Identity provider `token.actions.githubusercontent.com` → Audience `sts.amazonaws.com` → GitHub org `warrenmnocos` → **Next**.
3. Skip permissions → **Next** → name `washa-github-deploy` → **Create role**.
4. Open the role → **Trust relationships** → **Edit trust policy** → replace with (substitute `<ACCOUNT_ID>`):
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [{
       "Effect": "Allow",
       "Principal": { "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com" },
       "Action": "sts:AssumeRoleWithWebIdentity",
       "Condition": {
         "StringEquals": {
           "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
           "token.actions.githubusercontent.com:sub": "repo:warrenmnocos/oppshan-washa:ref:refs/heads/main"
         }
       }
     }]
   }
   ```
5. **Permissions** → **Add permissions** → **Create inline policy** → **JSON** → paste (substitute `<ACCOUNT_ID>`):
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Sid": "DeployFunctionCodeAndConfig",
         "Effect": "Allow",
         "Action": ["lambda:UpdateFunctionCode", "lambda:GetFunction", "lambda:GetFunctionConfiguration", "lambda:UpdateFunctionConfiguration"],
         "Resource": "arn:aws:lambda:ap-northeast-1:<ACCOUNT_ID>:function:washa"
       },
       {
         "Sid": "ReadRuntimeConfig",
         "Effect": "Allow",
         "Action": ["ssm:GetParameter", "ssm:GetParameters"],
         "Resource": "arn:aws:ssm:ap-northeast-1:<ACCOUNT_ID>:parameter/oppshan/washa/*"
       },
       {
         "Sid": "DecryptViaSsm",
         "Effect": "Allow",
         "Action": "kms:Decrypt",
         "Resource": "*",
         "Condition": { "StringEquals": { "kms:ViaService": "ssm.ap-northeast-1.amazonaws.com" } }
       },
       {
         "Sid": "InvalidateEdgeCache",
         "Effect": "Allow",
         "Action": ["cloudfront:CreateInvalidation", "cloudfront:GetInvalidation"],
         "Resource": "*"
       }
     ]
   }
   ```
   > The CloudFront resource is `*` here only because the distribution doesn't exist yet; after Phase 6 you can tighten it to the distribution ARN.
6. **Next** → name `deploy` → **Create policy**. Note the **role ARN** (top of the page) — it becomes the GitHub repo variable `AWS_DEPLOY_ROLE_ARN` in Phase 10.

---

## Phase 4: Lambda function

Region: **ap-northeast-1**.

### 4.1 Build the artifact first
The function needs a code package. Build the native arm64 zip locally (`./mvnw -Dnative-release package` on an arm64 machine or via the `CD` workflow's artifact) to get `target/function.zip`, or temporarily upload any small zip and let the first real deploy (Phase 10) replace it.

### 4.2 Create the function
1. Console search → **Lambda** → **Create function**.
2. **Author from scratch**. Name: `washa`.
3. Runtime: **Amazon Linux 2023** → **Provide your own bootstrap on Amazon Linux 2023** (`provided.al2023`).
4. Architecture: **arm64**.
5. Permissions → **Use an existing role** → `washa-lambda-exec`.
6. **Create function**.
7. **Code** tab → **Upload from** → **.zip file** → upload `function.zip`.
8. **Configuration** → **General configuration** → **Edit**: Memory **256 MB**, Timeout **30 s** → **Save**.
9. **Configuration** → **Concurrency** → **Edit** → **Reserve concurrency** = **5** → **Save**.

### 4.3 Environment variables
**Configuration** → **Environment variables** → **Edit** → add the seven keys with the same values you stored in Phase 2 (the Lambda reads env vars, not SSM, at runtime):

`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `TOKEN_ENCRYPTION_SECRET`, `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD`, `WASHA_ALLOWED_IDENTITIES`. **Save**.

> The CLI guide automates this with `infra/cli/set-lambda-env.sh`, which reads the SSM values and writes them here in one step.

### 4.4 Function URL
1. **Configuration** → **Function URL** → **Create function URL**.
2. Auth type: **AWS_IAM**. Leave CORS off. **Save**.
3. Copy the **Function URL** (`https://<id>.lambda-url.ap-northeast-1.on.aws/`) — its host is the CloudFront origin in Phase 6.

---

## Phase 5: ACM certificate (us-east-1)

**Switch the region selector to N. Virginia (us-east-1)** — CloudFront only accepts certificates from there.

1. Console search → **Certificate Manager** → **Request** → **Request a public certificate** → **Next**.
2. Fully qualified domain name: `washa.oppshan.com`.
3. Validation method: **DNS validation**. Key algorithm: **RSA 2048**. → **Request**.
4. Open the certificate → in the domain row, click **Create record in Route 53** → **Create records** (this writes the validation CNAME into the `oppshan.com` zone automatically).
5. Wait until **Status: Issued** (a few minutes). Copy the **certificate ARN**.

---

## Phase 6: CloudFront

Back in any region (CloudFront is global; the console defaults fine).

### 6.1 Origin Access Control
1. Console search → **CloudFront** → **Origin access** (left nav) → **Create control setting**.
2. Name: `washa-oac`. Signing behavior: **Sign requests**. Origin type: **Lambda**. → **Create**.

### 6.2 Distribution
1. CloudFront → **Distributions** → **Create distribution**.
2. **Origin domain**: paste the Function URL **host** (the `https://` and trailing `/` removed, e.g. `<id>.lambda-url.ap-northeast-1.on.aws`).
3. **Origin access**: **Origin access control settings** → select `washa-oac`.
4. Protocol: **HTTPS only**. Minimum origin SSL: **TLSv1.2**.
5. **Add custom header** (twice) — these let the app build `https://washa.oppshan.com` for OIDC redirects, since the viewer Host is dropped for OAC signing:
   - `X-Forwarded-Host` = `washa.oppshan.com`
   - `X-Forwarded-Proto` = `https`
6. **Default cache behavior**: Viewer protocol policy **Redirect HTTP to HTTPS**; Allowed methods **GET, HEAD**; Cache policy **CachingOptimized**; Origin request policy **AllViewerExceptHostHeader**; Compress objects **Yes**.
7. **Settings**: Price class **Use North America, Europe, Asia…** (PriceClass_200); Alternate domain name (CNAME) **`washa.oppshan.com`**; Custom SSL certificate → select the ACM cert from Phase 5; Security policy **TLSv1.2_2021**; HTTP versions **HTTP/2 and HTTP/3**.
8. **Create distribution**. Note the **Distribution domain name** (`<id>.cloudfront.net`) and the **Distribution ARN** / ID.

### 6.3 Two more cache behaviors (no caching for dynamic paths)
For each of `/api/*` and `/sso/*`: distribution → **Behaviors** → **Create behavior**:
- Path pattern: `/api/*` (then repeat for `/sso/*`).
- Origin: the Lambda origin.
- Viewer protocol policy: **Redirect HTTP to HTTPS**.
- Allowed methods: **GET, HEAD, OPTIONS, PUT, POST, PATCH, DELETE**.
- Cache policy: **CachingDisabled**. Origin request policy: **AllViewerExceptHostHeader**. Compress: **No**.
- **Create behavior**.

---

## Phase 7: Let CloudFront invoke the Function URL

1. **Lambda** → `washa` → **Configuration** → **Permissions** → scroll to **Resource-based policy statements** → **Add permissions** → **AWS service**.
2. Service: **CloudFront**. Statement ID: `AllowCloudFrontInvokeFunctionUrl`. Action: **lambda:InvokeFunctionUrl**. Source ARN: the **distribution ARN** from Phase 6. → **Save**.

> If the console doesn't offer `InvokeFunctionUrl` for the function-URL auth type, add it via the CLI: `aws lambda add-permission --function-name washa --statement-id AllowCloudFrontInvokeFunctionUrl --action lambda:InvokeFunctionUrl --principal cloudfront.amazonaws.com --source-arn <DISTRIBUTION_ARN> --function-url-auth-type AWS_IAM`.

(Optional) Tighten the deploy role's `InvalidateEdgeCache` resource from `*` to the distribution ARN now that it exists.

---

## Phase 8: Route 53 alias

1. **Route 53** → **Hosted zones** → `oppshan.com` → **Create record**.
2. Record name: `washa`. Record type: **A**. Toggle **Alias** on. Route traffic to **Alias to CloudFront distribution** → select your distribution. → **Create records**.
3. Repeat for an **AAAA** record (same alias target) so IPv6 clients resolve too.

---

## Phase 9: Google OAuth redirect URI

1. Google Cloud console → **APIs & Services** → **Credentials** → your OAuth 2.0 Client ID.
2. Under **Authorized redirect URIs**, add: `https://washa.oppshan.com/sso/sign-in/oidc/callback/google` → **Save**.

---

## Phase 10: First deploy + verify

1. **GitHub repo → Settings → Secrets and variables → Actions → Variables**, add:
   - `AWS_DEPLOY_ROLE_ARN` = the `washa-github-deploy` role ARN (Phase 3.2)
   - `CLOUDFRONT_DISTRIBUTION_ID` = the distribution ID (Phase 6)
2. Run the **CD** workflow (Actions → CD → Run workflow). It builds the native arm64 artifact, `aws lambda update-function-code`s `washa`, and invalidates the CloudFront cache. (Or upload a freshly built `function.zip` by hand in the Lambda **Code** tab.)
3. Wait for the distribution status to be **Deployed** (~15 min on first create), then visit **https://washa.oppshan.com**. You should reach the sign-in page; signing in with an allowlisted Google account lands on the dashboard.

### Smoke checks
- `https://washa.oppshan.com/` returns the SPA shell.
- Sign-in redirects to Google and back to the dashboard (confirms the forwarded-host headers + the OAuth redirect URI).
- A budget action (e.g. compute) succeeds (confirms the `/api/*` behavior + Neon connectivity).
- CloudWatch Logs group `/aws/lambda/washa` shows the request (confirms the execution role).

If anything fails, see [`aws-deployment-recovery.md`](aws-deployment-recovery.md).
