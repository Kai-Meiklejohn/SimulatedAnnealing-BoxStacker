# Simulated Annealing Box Stacker

This project implements a heuristic solver to find the tallest possible stack of 3D boxes under strict geometric constraints. Each box can be rotated into any of its three orientations, and must fit strictly inside the box beneath it (no overhang or flush edges). The goal is to maximise the total height of the stack.

## Why This Project?

Finding the optimal box stack is an NP-hard problem when you allow arbitrary subsets, orientations, and strict-fit constraints. Exact methods become infeasible as the number of boxes grows. Instead, we:

- Generate a high-quality initial solution using dynamic programming, exploiting the box-stacking variant of longest-increasing-subsequence in 3D.
- Refine the solution via Simulated Annealing, a probabilistic metaheuristic that can escape local optima and approach global maxima in large search spaces.

This approach showcases heuristic optimisation techniques and demonstrates how to combine deterministic and stochastic methods effectively.

## How It Works

### 1. Initial Solution via Dynamic Programming

- **Orientations:** For each box, generate all 3 possible (width, depth, height) orientations.
- **Sort:** Sort by descending base area (width × depth).
- **DP Stack:** Compute the tallest stack ending on each orientation, ensuring that:
  - Each box is used only once (track original box index).
  - Each orientation fits strictly within the one below.
- **Backtrack:** Extract a valid stack with maximum height.

### 2. Refinement via Simulated Annealing

Simulated Annealing (SA) mimics the physical process of annealing in metallurgy:

- **State:** a candidate stack.
- **Energy:** negative of stack height (we minimise energy).
- **Neighbourhood:** random local edits (insert, remove, substitute up to ⌈T⌉ boxes).
- **Acceptance:**
  - Always accept improvements.
  - Otherwise accept with probability exp(-ΔE / T), allowing escape from local optima.
- **Cooling:** after each iteration, reduce temperature T ← T - r until T ≤ 0.

## Build & Run

**Compile:**

```
javac NPCStack.java
```

**Run:**

```
java NPCStack <input-file> <initial-temperature> <cooling-rate>
```

- `<input-file>`: path to a text file where each line has three positive integers (box dimensions).
- `<initial-temperature>`: positive integer, controls neighbourhood size in SA.
- `<cooling-rate>`: real value (0.1 ≤ r ≤ initial-temperature), the amount by which temperature decreases each step.

**Usage Example:**

```
java NPCStack boxes.txt 100 0.5
```

This runs the optimiser on `boxes.txt` with an initial temperature of 100 and a cooling rate of 0.5. Output is printed to stdout:

```
<width> <depth> <height> <cumulative-height>
```
(from top box down to the bottom).

## Pseudocode Summary

```python
# INITIAL DP SOLUTION
orientations = generateAllOrientations(boxes)
sortDescendingByBaseArea(orientations)
for i in 0..n-1:
    dp[i] = orientations[i].height
    parent[i] = -1
    for j in 0..i-1:
        if fits(orientations[i], orientations[j])
           and differentOriginalBox:
            if dp[j] + orientations[i].height > dp[i]:
                dp[i] = dp[j] + orientations[i].height
                parent[i] = j
bestIndex = argmax(dp)
initialStack = backtrack(parent, bestIndex)

# SIMULATED ANNEALING REFINEMENT
current = initialStack
best = current
T = initialTemperature
while T > 0:
    neighbour = randomNeighbour(current, T)
    ΔE = energy(neighbour) - energy(current)
    if ΔE <= 0 or rand() < exp(-ΔE/T):
        current = neighbour
        if height(current) > height(best):
            best = current
    T = T - coolingRate

return best
```

