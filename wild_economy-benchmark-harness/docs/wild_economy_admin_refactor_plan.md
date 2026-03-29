# wild_economy Admin Refactor Plan

Revision date: 2026-03-22  
Reference snapshot: `3d73036e5d764b26f96b8b36738dc8fecb9a8724`  
Purpose: Make the admin code more elegant and less monolithic without changing admin behavior or file formats

---

## 1. Why refactor now

The admin system now does real work:

- reload
- preview
- validate
- diff
- apply
- item inspection
- GUI review
- manual overrides
- generated report loading

That makes the current monolithic shape harder to maintain cleanly.

The main issue is not that the code is non-functional. The issue is that too many responsibilities are accumulated inside a few large classes.

---

## 2. Current monoliths

### 2.1 `ShopAdminCommand`

Current responsibilities are mixed together:

- command parsing
- subcommand dispatch
- permission enforcement
- catalog orchestration
- summary rendering
- validation summary rendering
- diff summary rendering
- reload/apply orchestration
- command usage/help text

This is too much for one class.

### 2.2 `AdminMenuRouter`

Current responsibilities are also mixed together:

- permission enforcement
- state rebuilds
- apply/reload orchestration
- GUI navigation
- back navigation
- generated-report loading
- manual override entry wiring
- menu opening details
- user-facing feedback messages

This makes the class harder to reason about and harder to test.

### 2.3 `AdminManualOverrideEditor`

This is directionally useful, but it risks becoming a hybrid of:

- file I/O
- validation
- value discovery
- policy-profile interpretation
- editor helper logic
- UI helper formatting

That should be watched so it does not become the next monolith.

---

## 3. Refactor goal

Keep behavior the same, but split the admin system into cleaner layers:

- **dispatch**: which action was requested
- **policy**: whether the sender may do it
- **orchestration**: perform the admin action
- **presentation**: render summaries/messages
- **navigation**: open the correct view
- **report access**: load generated artifacts
- **override persistence**: load/save/remove manual overrides

The immediate goal is elegance, not feature creep.

---

## 4. Recommended target shape

### 4.1 Command side

Split `ShopAdminCommand` into:

#### `AdminCommandDispatcher`
Maps input tokens to admin actions.

Responsibilities:

- parse top-level subcommands
- route to the correct handler
- keep usage/help central and small

#### `AdminPermissionService`
Defines the permission contract in one place.

Responsibilities:

- command permission checks
- reusable deny messages
- future narrower capability checks

#### `AdminCatalogActionService`
Runs the actual admin workflows.

Responsibilities:

- reload
- preview
- validate
- diff
- apply
- inspect item

This should become the main orchestration service shared by command and GUI paths.

#### `AdminCatalogSummaryFormatter`
Formats command-facing summaries.

Responsibilities:

- summary lines
- validation excerpts
- policy counts
- diff summaries
- item inspection text blocks

This removes presentation logic from orchestration logic.

### 4.2 GUI side

Split `AdminMenuRouter` into:

#### `AdminNavigationService`
Open menus and handle back navigation.

Responsibilities:

- root navigation
- bucket navigation
- rule impact navigation
- inspector navigation
- editor navigation

#### `AdminViewStateFactory`
Builds `AdminCatalogViewState` from generated artifacts.

Responsibilities:

- call the catalog action service
- load generated reports
- build the immutable view state used by the menus

#### `AdminGeneratedReportLoader`
Load generated admin artifacts from disk.

Responsibilities:

- rule impacts
- review buckets
- later any other generated report families

This keeps file parsing away from navigation code.

#### `AdminOverrideWorkflowService`
Coordinate save/remove override flows from the GUI.

Responsibilities:

- validate selected stock profile / eco envelope
- save override
- remove override
- rebuild state after changes
- return to the correct inspector view

This prevents `AdminMenuRouter` from becoming an orchestration/service class.

---

## 5. Recommended refactor order

### Slice A — extract orchestration first

Create `AdminCatalogActionService` and move shared admin workflows out of `ShopAdminCommand`.

Why first:

- biggest reduction in monolithic command logic
- creates a shared service for both command and GUI paths
- lowers duplication risk immediately

### Slice B — extract summary formatting

Move command-facing summary text into `AdminCatalogSummaryFormatter`.

Why second:

- low risk
- removes a lot of visual noise from `ShopAdminCommand`
- makes command code easier to read

### Slice C — extract `AdminViewStateFactory`

Move build/result/report loading out of `AdminMenuRouter`.

Why third:

- `AdminMenuRouter` becomes mostly navigation
- state construction becomes testable and reusable

### Slice D — extract `AdminGeneratedReportLoader`

Move generated YAML parsing out of `AdminMenuRouter`.

Why fourth:

- narrows responsibilities sharply
- makes report loading independently testable
- keeps view-state assembly cleaner

### Slice E — narrow permission handling

After structure is cleaner, move permission logic into a dedicated admin permission service and then split plugin permissions more safely.

---

## 6. What not to do

Do **not** do the following in the first refactor slice:

- do not redesign the admin feature set
- do not change file formats
- do not merge GUI and command presentation into one giant abstraction
- do not build a generic framework for future hypothetical admin systems
- do not refactor everything at once

The best outcome is small, behavior-preserving slices.

---

## 7. Recommended first code slice

The cleanest next refactor slice is:

### Extract `AdminCatalogActionService`

Move these responsibilities out of `ShopAdminCommand`:

- build preview/validate/diff/apply
- reload runtime
- inspect one item
- return rich result objects instead of preformatted chat messages

Then let:

- `ShopAdminCommand` focus on dispatch + sender interaction
- `AdminMenuRouter` reuse the same action service for GUI-driven workflows

This is the best first cut because it reduces duplication and creates a clean seam for later slices.

---

## 8. Success criteria

A good refactor should leave behavior unchanged while achieving:

- smaller `ShopAdminCommand`
- smaller `AdminMenuRouter`
- shared admin action orchestration
- less duplicate apply/reload logic
- clearer permission boundaries
- cleaner future path for granular permissions
- easier testing of report loading and action workflows

---

## 9. Bottom line

The admin code does not need a rewrite. It needs decomposition.

The first target should be orchestration, not micro-cleanup.

Best next implementation direction:

1. extract `AdminCatalogActionService`
2. extract `AdminCatalogSummaryFormatter`
3. extract `AdminViewStateFactory`
4. extract `AdminGeneratedReportLoader`
5. then narrow permissions cleanly

