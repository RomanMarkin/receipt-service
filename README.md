# Receipt Processing Service API

This service exposes a single endpoint to handle the ingestion, validation, and processing of fiscal receipts for bonus assignment. It accepts raw receipt data, validates it, and orchestrates the verification process.

## **Endpoint: Process Receipt**

`POST /processReceipt`

Initiates the receipt processing workflow. This endpoint parses the incoming receipt data, creates a submission record, and immediately triggers the verification and then bonus assignment logic.

### **1. Request Structure**

The endpoint accepts a JSON object containing the raw receipt payload and user metadata.

**Content-Type:** `application/json`

```json
{
  "receiptData": "raw_qr_code_string",
  "playerId": "12345",
  "countryCode": "PE"
}
```

| Field | Type | Required | Description                                                                                                                  |
| :--- | :--- | :--- |:-----------------------------------------------------------------------------------------------------------------------------|
| `receiptData` | String | Yes | The raw string content scanned from the receipt QR code (e.g., "12345678903\|03\|B444\|00153692\|18.0\|150.50\|2025-11-15"). |
| `playerId` | String | Yes | The unique identifier of the player submitting the receipt.                                                                  |
| `countryCode` | String | Yes | The 2-letter ISO country code (e.g., `PE`) indicating the origin of the receipt.                                             |

---

### **2. Success Response**

If the request is valid and accepted, the service returns **HTTP 200 OK**.

**Response Body:**

```json
{
  "receiptSubmissionId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "BonusAssigned",
  "message": "Bonus Code: 123"
}
```

| Field | Type | Description |
| :--- | :--- | :--- |
| `receiptSubmissionId` | String | The unique ID generated for this submission. |
| `status` | String | The current state of the submission (see **Submission Statuses** below). |
| `message` | String | Optional descriptive message. |

#### **Submission Statuses**
The `status` field indicates the outcome of the initial processing.

| Status | Description                                                                                                                                          |
| :--- |:-----------------------------------------------------------------------------------------------------------------------------------------------------|
| `VerificationPending` | **(Standard Success)** The receipt structure is valid, and async verification with the tax authority has been scheduled.                             |
| `InvalidReceiptData` | The input was accepted, but the `receiptData` could not be parsed into a valid fiscal document format.                                               |
| `Verified` | The receipt was verified successfully (usually instantly by a fast provider). This status is transient and never returned to the client. |
| `VerificationRejected` | The tax authority explicitly rejected the receipt (e.g., invalid issuer, annulled receipt).                                                          |
| `VerificationFailed` | The verification process failed technically (e.g., provider timeouts) after all retries were exhausted.                                              |
| `NoBonusAvailable` | The receipt is valid, but no bonus campaign matches this receipt's criteria.                                                                         |
| `BonusAssignmentPending` | The receipt is verified, and a bonus is applicable; the assignment job has been scheduled.                                                           |
| `BonusAssignmentRejected` | A bonus was applicable, but the assignment was rejected (e.g., player reached a limit).                                                              |
| `BonusAssignmentFailed` | The system failed to assign the bonus due to a technical error after retries.                                                                        |
| `BonusAssigned` | The complete flow finished successfully: receipt verified AND bonus credited to the player.                                                          |
---

### **3. Error Responses**

The API uses standard HTTP status codes and a consistent JSON error structure for all failures.

**Error Body Structure:**

```json
{
  "code": "ErrorCodeString",
  "message": "Human readable description of the error"
}
```

#### **Possible Errors**

| HTTP Status | Error Code | Description |
| :--- | :--- | :--- |
| **400 Bad Request** | `InvalidJson` | The request body is malformed or invalid JSON. |
| **400 Bad Request** | `InvalidParameters` | Domain validation failed (e.g., missing `playerId`, unsupported `countryCode`). |
| **409 Conflict** | `DuplicateReceipt` | This receipt has already been submitted and processed previously. |
| **500 Server Error** | `SystemError` | An unexpected internal failure occurred (e.g., database connectivity issues). |

#### **Error Examples**

**400 - Invalid JSON:**
```json
{
  "code": "InvalidJson",
  "message": "Invalid JSON format: (specific parsing error details)"
}
```

**400 - Invalid Parameters:**
```json
{
  "code": "InvalidParameters",
  "message": "Country code 'XX' is not supported."
}
```

**409 - Duplicate Receipt:**
```json
{
  "code": "DuplicateReceipt",
  "message": "Receipt already exists"
}
```

**500 - System Crash:**
```json
{
  "code": "SystemError",
  "message": "Internal Server Error"
}
```