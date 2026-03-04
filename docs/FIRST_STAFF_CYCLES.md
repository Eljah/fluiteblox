# First staff recognition cycles (hypothesis → code → test → analysis)

## Setup
- Reference "first staff" is taken from MusicXML measures **4-7** (13 notes).
- Comparison script: `scripts/analyze_first_staff_mismatches.sh`.
- Duration regression script: `scripts/test_stem_duration_recognition.sh`.

## Cycle 0 (baseline, commit `08d2821`)
### Observation
- Too many recognized notes on top corridor and many wrong staff positions (line/gap mismatch).

### Metrics
- Stem duration regression: `lcs=9, expectedStem=56, actualStem=63, recall=0.1607, precision=0.1429`.
- First staff analysis summary: `expected=13 corridor0=30 compared=13 pitch_ok=2 duration_ok=2`.

## Cycle 1
### Hypothesis
- Over-detection is partly caused by weak x-slot dedupe in note-head candidates.
- Increasing x-slot width and selecting candidate by note-head-like score should remove duplicate/nearby false heads.

### Code change
- In `dedupeNoteHeads`: increased `slotW` from `0.85*staffSpacing` to `1.25*staffSpacing`.
- Added `noteHeadSlotScore(...)` and switched per-slot choice from `max area` to `max score`.

### Metrics after cycle
- Top corridor candidate count reduced from 30 to 25.

## Cycle 2
### Hypothesis
- Additional false positives come from non-note compact blobs with extreme fill/aspect.
- Tightening contour fill/ratio acceptance should suppress non-head contours.

### Code change
- Added non-recall `effectiveMinFill/effectiveMaxFill` for contour filtering.
- Tightened relaxed note-head filter bounds for ratio/fill.

### Metrics after cycle
- Stem duration regression: `lcs=9, expectedStem=56, actualStem=58, recall=0.1607, precision=0.1552`.
- First staff analysis summary: `expected=13 corridor0=25 compared=13 pitch_ok=2 duration_ok=1`.

## Current conclusion
- Precision improved slightly (0.1429 → 0.1552) due to fewer extra detections, but pitch placement on first staff remains poor (2/13 exact pitch matches).
- Next hypothesis: transition from contour-first notehead detection to **stem-anchored head detection** (find stem candidates first, then search oval head near stem end) to remove line-fragment false positives.


## Why corridor-by-staff does not work reliably (despite looking algorithmic)
1. **Corridor is a broad gate, not a note-head model.**
   Inside one corridor there are stems, beams, barline fragments, slur pieces, and print artifacts; contour-first detection confuses these with heads.
2. **Global line geometry vs local photo warp.**
   Staff lines are treated almost horizontally at group scale, while real phone photo introduces local tilt/curvature and thickness variation.
3. **Merged components.**
   A head touching stem/beam becomes one component, and rectangle-center approximation can shift head center to a wrong line/gap.
4. **Duration coupling amplifies head errors.**
   Wrong head blob means wrong stem neighborhood, so duration from stem/flag also drifts.

## Next concrete hypothesis (Cycle 3)
- Move from `corridor -> contour -> classify` to **stem-anchored head localization**:
  1) detect vertical stem candidates first;
  2) around each stem endpoint, search elliptical head with gradient/shape prior;
  3) only then map localized head center to nearest local staff step.
- Keep corridor only as spatial prior, not as primary discriminator.


## Cycle 3
### Hypothesis
- Since noteheads are already filtered, pitch should be refined in a dedicated second pass using known note x-coordinate on the same staff:
  - detect local staff lines at note x;
  - decide if head is on line vs between lines by center-to-line band and ink on both sides;
  - if not on line, decide upper/lower gap by side of nearest line.

### Code change
- Added second-pass pitch refinement in `fillNotesWithDurationFeatures`:
  - gather known notehead candidates first (with duration),
  - run `refinePitchStepsSecondPass(...)`,
  - inside it call `refinedStepByKnownHeadPosition(...)`.
- `refinedStepByKnownHeadPosition(...)` now explicitly performs line-side decision for each known notehead.

### Metrics after cycle
- First staff analysis: `expected=13 corridor0=25 compared=13 pitch_ok=2 duration_ok=1` (no change).
- Stem duration regression: `actualStem=57, precision=0.1579` (no change).

### Interpretation
- Second-pass logic is now in place, but dominant error source is still wrong **notehead candidate identity** (artifacts accepted as heads), not only line/gap assignment.


## Cycle 4
### Hypothesis
- Use OpenCV-style local connectivity cue around nearest staff line for each already-found notehead:
  - if white region connects left-right **above** the line but not below, head is likely above line;
  - if opposite, head is likely below line;
  - if both or neither, keep previous center-delta fallback.

### Code change
- Added `detectGapSideByWhiteConnectivity(...)` and `hasWhiteConnectionAcrossBand(...)` in `OpenCvScoreProcessor`.
- Integrated connectivity decision into `refinedStepByKnownHeadPosition(...)` before delta fallback.

### Metrics after cycle
- First staff analysis: `expected=13 corridor0=25 compared=13 pitch_ok=2 duration_ok=1`.
- Stem duration regression: `lcs=9, expectedStem=56, actualStem=57, precision=0.1579`.
- Parameter sweep top remains unchanged.

### Interpretation
- Connectivity cue is implementable and now in pipeline, but on this photo it does not move aggregate metrics yet.
- Primary bottleneck is still false notehead identity, not only side-of-line decision.
