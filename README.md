# Simulated Annealing Box Stacker

This project implements a heuristic solver to find the tallest possible stack of 3D boxes under strict geometric constraints. Each box can be rotated into any of its three orientations, and must fit strictly inside the box beneath it (no overhang or flush edges). The goal is to maximise the total height of the stack.

## Why This Project?

Finding the optimal box stack is an NP-hard problem when you allow arbitrary subsets, orientations, and strict-fit constraints. Exact methods become infeasible as the number of boxes grows. Instead, we:

- Generate a high-quality initial solution using dynamic programming (DP), exploiting the box-stacking variant of longest-increasing-subsequence in 3D.
- Refine the solution via Simulated Annealing (SA), a probabilistic metaheuristic that can escape local optima and approach global maxima in large search spaces.

This approach showcases heuristic optimisation techniques and demonstrates how to combine deterministic and stochastic methods effectively.

## How It Works

### 1. Initial Solution via Dynamic Programming

- **Orientations:** For each box, generate all 3 possible (width, depth, height) orientations.
- **Sort:** Sort by descending base area (width × depth).
- **DP Stack:** Compute the tallest stack ending on each orientation, allowing unlimited reuse (ignoring single-use constraint).
- **Prune:** Remove all but the first occurrence of each box to enforce single-use.
- **Backtrack:** Extract a valid stack with maximum height.

> **Note:** The DP+prune approach is extremely strong. For large, diverse inputs like `boxes.txt`, it is rare for simulated annealing to find a better stack than the pruned DP seed. Most improvements are seen only on small or specially crafted test cases.

### 2. Refinement via Simulated Annealing

Simulated Annealing (SA) mimics the physical process of annealing in metallurgy:

- **State:** a candidate stack.
- **Energy:** negative of stack height (we minimise energy).
- **Neighbourhood:** random local edits (swap, remove/insert, replace, or rebuild part of the stack).
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

---

## How to Run Simulated Annealing *Only* (Bypass DP)

If you want to test simulated annealing starting from a short random seed (not the DP/pruned stack), do the following in `NPCStack.java`:

1. **Comment out the DP and prune code:**
   ```java
   // List<Orientation> dpStack = dpAllowReuse(all);
   // System.out.println("pre-prune height: " + stackHeight(dpStack));
   // List<Orientation> seed = pruneToSingleUse(dpStack);
   // System.out.println("seed height: " + stackHeight(seed));
   ```
2. **Insert this code instead (after building `all`):**
   ```java
   // generate a short random valid initial seed
   List<Orientation> seed = new ArrayList<>();
   Set<Integer> used = new HashSet<>();
   Random rnd = new Random();
   int maxLen = Math.min(5, all.size());
   for (int i = 0; i < maxLen; i++) {
       List<Orientation> candidates = new ArrayList<>();
       for (Orientation o : all) {
           if (!used.contains(o.originalIndex) && (seed.isEmpty() || (o.width < seed.get(seed.size()-1).width && o.depth < seed.get(seed.size()-1).depth))) {
               candidates.add(o);
           }
       }
       if (candidates.isEmpty()) break;
       Orientation chosen = candidates.get(rnd.nextInt(candidates.size()));
       seed.add(chosen);
       used.add(chosen.originalIndex);
   }
   System.out.println("random seed height: " + stackHeight(seed));
   ```
3. **Leave the rest of the code unchanged:**
   ```java
   List<Orientation> best = simulatedAnnealing(seed, all, T0, cooling);
   printStack(best);
   ```

This will let you observe how simulated annealing alone can build up a stack from a random starting point. This is useful for testing and for small/simple test cases.

---


