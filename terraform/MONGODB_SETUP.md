# MongoDB Atlas Manual Setup Guide

This guide details the **one-time manual setup** required to allow Terraform to provision databases in MongoDB Atlas.

Because Terraform creates **Projects** dynamically, it requires **Organization-Level API Keys**. Setting this up involves navigating hidden menus and bypassing specific IP restrictions.

---

## 📝 Step 1: Create an Account

1.  **Register:** Go to [account.mongodb.com](https://account.mongodb.com/) and create a free account.
2.  **Create Organization:** Follow the prompts to create your first Organization (e.g., "MyCompany Org").
    * *Note:* You do not need to create a Project or Cluster manually yet; Terraform will handle that.

---

## 🔑 Step 2: Generate Organization API Keys

Terraform needs "Organization Owner" permissions to create projects.

1.  **Navigate to Organization View:**
    * Ensure you are looking at the **Organization** level (top-left dropdown), not a specific Project.
2.  **Go to API Keys:**
    * Navigate to **Identity & Access** -> **Applications** -> **API Keys**.
    * *Troubleshooting:* If you cannot find the tab, force the browser to this URL:
      `https://cloud.mongodb.com/v2/{YOUR_ORG_ID}#/access/apiKeys`
3.  **Create Key:**
    * Click **Create API Key**.
    * **Description:** `Terraform Deployer`
    * **Permissions:** Select **Organization Owner**.
4.  **Save Credentials:**
    * Copy the **Public Key** and **Private Key** immediately. You will not see the Private Key again.

---

## 🌍 Step 3: The "Split Internet" IP Whitelist (The Trick)

MongoDB Atlas blocks the CIDR `0.0.0.0/0` (Allow All) for API Keys security. However, GitHub Actions runners use random IP addresses, so we must allow all traffic.

**The Workaround:**
We add the "whole internet" in two halves to bypass the validation check.

1.  In the API Keys list, click the **"..."** menu next to your new key -> **Manage Access List**.
2.  Add the **First Half**:
    * **IP Address:** `0.0.0.0/1`
    * **Description:** `Global Access Part 1`
3.  Add the **Second Half**:
    * **IP Address:** `128.0.0.0/1`
    * **Description:** `Global Access Part 2`

*Together, these two rules cover every IPv4 address in existence.*

---

## ⚙️ Step 4: Add Secrets to GitHub

These secrets are **Global** (Organization-level). You use the **same set of credentials** for Staging, Production, and any future environments.

Add these as **Repository Secrets**, not Environment Secrets.

1.  Go to your GitHub Repository.
2.  Navigate to **Settings** -> **Secrets and variables** -> **Actions**.
3.  Click **New repository secret** (green button).

| Secret Name | Value |
| :--- | :--- |
| `MONGODB_ATLAS_PUBLIC_KEY` | The Public Key from Step 2 |
| `MONGODB_ATLAS_PRIVATE_KEY` | The Private Key from Step 2 |
| `MONGODB_ATLAS_ORG_ID` | Your Org ID (Found in Organization Settings) |