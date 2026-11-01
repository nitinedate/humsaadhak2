# Match Studio candidate decision fix

## Reported symptom

Submitting the decision batch from Match Studio returned HTTP 422 with `unknown-candidate`, even though the candidate card was visible.

## Cause

The candidate listing returned every candidate above the score threshold, including candidates already approved or rejected. Confirmation, however, validated submitted IDs against only pending candidates. A second reviewer or a stale page could therefore submit an ID that was visible earlier but was no longer eligible for review. During a purpose switch, local card order could also briefly remain from the previous purpose while the next candidate query loaded.

## Changes

- The review list now returns only pending candidates at or above the configured threshold.
- The frontend hides the old candidate order until the selected purpose's candidate data is loaded.
- Confirmation locks the purpose's candidate rows while validating and recording the batch, serializing simultaneous reviews.
- A stale submission for an already-reviewed candidate returns HTTP 409 with a reload/review message, and the frontend refreshes the candidate list after any failed submission.

## Build and deployment

The frontend and backend source were updated. Build and deploy using the project's existing procedure; Docker builds the React bundle and packages it into the Spring Boot application. This workspace did not have Maven or installed frontend dependencies, so a local compile could not be run.
