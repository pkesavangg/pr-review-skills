# Account Flag Service and Scale Review Service

## Overview

This document provides a comprehensive explanation of the `AccountFlagService` and `ScaleReviewService` in our application, which work together to manage user prompts for product reviews and app ratings.

## AccountFlagService

### API Operations

* **`getFlag`**: Retrieves flags array from the server.
* **`deleteFlag`**: Deletes a flag by its ID to ensure it is not shown again.

### Trigger Types

There are two types of triggers:

* `login`: Shows the IAM model when the user logs in for now.
* `entry`: Shows the scale review or app review model based on the flag type.

### Flag Selection Logic

If multiple flags exist:

1. Prefer the flag with `trigger === 'login'`.
2. If none exist, pick the first flag in the array.

### Flag Structure

```ts
{
  id: string;         // Unique identifier
  type: string;       // Type: "scale-review-ask {SKU}" or "app-rate-ask"
  trigger: string;    // When to show: "login" or "entry"
}
```

## ScaleReviewService

### Key Responsibilities

* Triggers and shows scale review modal based on custom logic.
* Stores review status in both local and remote storage.
* Only stores star count on the server.

### Status Types

```ts
export type ScaleReviewStatus = 'exitA' | 'exitB' | 'exitC' | 'reviewed' | 'feedback';
```

* `reviewed`: User rated 5 stars and navigated to Amazon.
* `exitA`: User dismissed the modal.
* `exitB`: Rating < 5, feedback requested.
* `exitC`: Rating = 5, reviewed updated on Amazon.

### Trigger Object

Stored in local storage:

```ts
{
  sku: string | null,
  dateInterval: number | null,
  entryInterval: number | null,
  triggerDate: string | null,
  entryTrigger: number | null
}
```

### Why Intervals Are Needed in Scale Review Logic

You're asking a great question about the design—why track `entryInterval` and `dateInterval` instead of just relying on the calculated `entryTrigger` and `triggerDate` values? The intervals serve several important purposes:

#### 1. Progressive Backoff Implementation

The most important reason is to implement the progressive backoff strategy. In the `updateTriggerString` method:

```ts
// Double the intervals when a user dismisses a review
this.currentTrigger.dateInterval *= 2;
this.currentTrigger.entryInterval *= 2;
```

Without storing these interval values, the system wouldn't know how much to increase the thresholds by when a user dismisses a review.

#### 2. Preserving the Algorithm's State

The intervals represent the current "patience level" of the algorithm:

* Initially starts at 10 entries and 14 days
* First dismissal: increases to 20 entries and 28 days
* Second dismissal: increases to 40 entries and 56 days
* And so on...
  This provides a gentler, less intrusive experience for users who aren't ready to review.

#### 3. Algorithm Consistency Across Sessions

By storing the intervals, the app maintains consistent behavior even if:

* The app is reinstalled
* The user switches devices
* The app is updated

#### 4. Future Flexibility

Having the intervals as separate parameters allows for:

* Adjusting one threshold without affecting the other
* Implementing different backoff rates for different conditions
* A/B testing different interval strategies

#### 5. Algorithm Transparency

Storing the intervals makes the algorithm's state easier to:

* Debug
* Log
* Analyze in metrics
* Adjust based on user behavior patterns

The alternative design (only storing target values) would lose information about the current state of the backoff algorithm, making it harder to properly adjust the next set of thresholds when a user dismisses a review.

### Flow

* When a scale is paired, a review trigger is created.
* Each time an entry is synced, if a trigger exists, review modal may be shown.
* Triggers are validated based on entry count and date interval.

### Trigger Management

* **`createReviewTrigger(sku)`**: Initializes trigger.
* **`updateReviewTrigger(sku)`**: Updates intervals and reschedules.
* **`clearTriggers()`**: Clears local trigger.

### Modal Display Logic

* If current trigger is valid and not reviewed, modal is shown via `accountFlag.appReview.next()`.
* If dismissed (`exitA`), trigger is updated.
* For all other statuses, trigger is cleared and flag is set as reviewed.

### Review Submission

* Star count is submitted to the server via `sendReport()`.

## AccountFlagService Integration

* Subscribes to `appReview` Subject.
* Based on `type`, launches one of the following modals:

    * `AmazonReviewComponent`
    * `ScaleReviewComponent`
    * `FeedbackComponent`
    * `AccountSettingsPage`
* On modal trigger, deletes the associated flag.

## Summary

The `AccountFlagService` handles prompt retrieval and prioritization while `ScaleReviewService` manages local state, trigger timing, and modal display. This coordination ensures timely, relevant, and non-repetitive prompts for users to submit scale reviews or app ratings.
