# Simulated Annealing Box Stacker

This project finds the tallest possible stack of 3D boxes under strict geometric constraints. Each box can be rotated into any of its three orientations and must fit strictly inside the box beneath it (no overhang or flush edges). The goal is to maximise the total height of the stack.

## Why This Project?

Finding the optimal box stack is an NP-hard problem when you allow arbitrary subsets, orientations, and strict-fit constraints. Exact methods become infeasible as the number of boxes grows. Instead, we:

- Generate an initial solution using a greedy heuristic (by base area).
- Refine the solution via simulated annealing (SA), a probabilistic heurisit method that can escape local optima and approach global maxima in large search spaces.

This approach showcases heuristic optimisation techniques and demonstrates how to combine deterministic and stochastic methods effectively.

## How It Works

### 1. Initial Solution via Greedy Heuristic

- Orientations: For each box, generate all 3 possible (width, depth, height) orientations (width is always less than or equal to depth for consistency).
- Sort: Sort all orientations by descending base area (width × depth).
- Greedy stack: Build a stack by always adding the next largest box (by base area) that fits strictly inside the box below and hasn't been used yet. Each box is used at most once.

### 2. Refinement via Simulated Annealing

Simulated annealing (SA) mimics the physical process of annealing in metallurgy:

- State: A candidate stack (list of box orientations).
- Energy: Negative of stack height (we want to maximise height).
- Neighbourhood: Random local edits (remove/insert, rebuild part of the stack, etc.). The number of boxes that can be changed in a move is controlled by the temperature parameter t.
- Acceptance:
  - Always accept improvements (taller stacks).
  - Otherwise, accept with probability exp(Δh / t), where Δh is the change in height, allowing escape from local optima.
- Cooling: After each iteration, reduce temperature using t = t - r until t ≤ r.

## Build & Run

**Compile:**

```
javac NPCStack.java
```

**Run:**

```
java NPCStack <input-file> <initial-temperature> <cooling-rate>
```

- `<input-file>`: Path to a text file where each line has three positive integers (box dimensions).
- `<initial-temperature>`: Integer > 0, controls the number of boxes that can be changed in a move (neighbourhood size) in SA.
- `<cooling-rate>`: Real value (0.1 ≤ r ≤ t), the amount by which temperature is reduced after each new solution (i.e., t = t - r).

**Usage Example:**

```
java NPCStack Boxes.txt 20 1
```

This runs the optimiser on `boxes.txt` with an initial temperature of 20 and a cooling rate of 0.1. Output is printed to stdout:

```
<width> <depth> <height> <cumulative-height>
```
(from top box down to the bottom)

---

## Notes on the Implementation

- The code uses a class called `boxOrientation` to represent each possible orientation of a box. Each orientation records its width, depth, height, and the index of the original box.
- The greedy stack is built by always picking the next largest box (by base area) that fits strictly inside the previous one and hasn't been used yet.
- Simulated annealing starts from the greedy stack and repeatedly makes random changes (removing, inserting, or rebuilding parts of the stack) to try to find a taller stack. Moves are accepted based on the improvement in height and the current temperature.
- All comments in the code use a natural, conversational style and start with lowercase letters.

---
