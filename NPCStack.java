import java.io.*;
import java.util.*;

/**
 * A hybrid box-stacker: polynomial DP seed (with reuse), prune to enforce single-use,
 * then simulated annealing to refine.
 *
 * Author: Kai Meiklejohn (1632448)
 */
public class NPCStack {

    /** one orientation of a box (w ≤ d for consistency) */
    static class Orientation {
        int width, depth, height, originalIndex;
        Orientation(int w, int d, int h, int idx) {
            this.width = w;  this.depth = d;
            this.height = h; this.originalIndex = idx;
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("usage: java NPCStack <file> <initial-temp> <cooling-rate>");
            System.exit(1);
        }
        String file     = args[0];
        double T0       = Double.parseDouble(args[1]);
        double cooling  = Double.parseDouble(args[2]);

        // 1) load raw boxes
        List<int[]> raw = readBoxes(file);
        if (raw.isEmpty()) { System.err.println("no boxes!"); System.exit(1); }

        // 2) all 3 orientations each
        List<Orientation> all = buildOrientations(raw);

        // 3) DP allowing unlimited reuse → backtrack full stack
        List<Orientation> dpStack = dpAllowReuse(all);

        // 4) prune duplicates (single-use) from that DP stack
        List<Orientation> seed = pruneToSingleUse(dpStack);

        // 5) refine via simulated annealing
        List<Orientation> best = simulatedAnnealing(seed, all, T0, cooling);

        // 6) output top→bottom
        printStack(best);
    }

    /**
     * finds the tallest stack allowing unlimited reuse of orientations (ignores single-use constraint)
     */
    private static List<Orientation> dpAllowReuse(List<Orientation> orientations) {
        int n = orientations.size();
        // sort by decreasing base area
        orientations.sort((a, b) -> Integer.compare(b.width * b.depth, a.width * a.depth));
        double[] maxHeight = new double[n]; // maxHeight[i]: max stack height ending with orientations[i]
        int[] prev = new int[n];            // prev[i]: previous box in the stack
        for (int i = 0; i < n; i++) {
            maxHeight[i] = orientations.get(i).height;
            prev[i] = -1;
            for (int j = 0; j < i; j++) {
                Orientation lower = orientations.get(j);
                Orientation upper = orientations.get(i);
                // can upper go on lower?
                if (upper.width < lower.width && upper.depth < lower.depth) {
                    double candidate = maxHeight[j] + upper.height;
                    if (candidate > maxHeight[i]) {
                        maxHeight[i] = candidate;
                        prev[i] = j;
                    }
                }
            }
        }
        // find index of tallest stack
        int bestIdx = 0;
        for (int i = 1; i < n; i++)
            if (maxHeight[i] > maxHeight[bestIdx]) bestIdx = i;
        // backtrack to build stack
        LinkedList<Orientation> stack = new LinkedList<>();
        for (int cur = bestIdx; cur != -1; cur = prev[cur])
            stack.addFirst(orientations.get(cur));
        return stack;
    }

    /**
     * removes all but the first occurrence of each originalIndex (enforces single-use)
     */
    private static List<Orientation> pruneToSingleUse(List<Orientation> stack) {
        Set<Integer> usedBoxes = new HashSet<>();
        List<Orientation> pruned = new ArrayList<>();
        for (Orientation o : stack) {
            // only add first occurrence
            if (usedBoxes.add(o.originalIndex)) {
                pruned.add(o);
            }
        }
        return pruned;
    }

    /**
     * refines a stack using simulated annealing
     */
    private static List<Orientation> simulatedAnnealing(
        List<Orientation> current,
        List<Orientation> all,
        double initialTemp,
        double cooling
    ) {
        Random rand = new Random();
        double temp = initialTemp;
        List<Orientation> best = new ArrayList<>(current);
        double bestHeight = stackHeight(best);
        while (temp > 1e-3) {
            // try several neighbors per temperature
            for (int it = 0; it < 20; it++) {
                List<Orientation> neighbor = neighbour(current, all, rand);
                double curHeight = stackHeight(current);
                double neighHeight = stackHeight(neighbor);
                double diff = neighHeight - curHeight;
                // accept if better, or with probability exp(diff/temp)
                if (diff > 0 || rand.nextDouble() < Math.exp(diff / temp)) {
                    current = neighbor;
                    if (neighHeight > bestHeight) {
                        best = new ArrayList<>(neighbor);
                        bestHeight = neighHeight;
                    }
                }
            }
            // cool down
            temp *= (1 - cooling / initialTemp);
        }
        return best;
    }

    /**
     * generates a random neighbor stack by modifying the current stack
     */
    private static List<Orientation> neighbour(
        List<Orientation> stack,
        List<Orientation> all,
        Random rand
    ) {
        List<Orientation> copy = new ArrayList<>(stack);
        int op = rand.nextInt(4);
        if (op == 0 && copy.size() > 1) {
            // swap two positions, then repair
            int i = rand.nextInt(copy.size()), j = rand.nextInt(copy.size());
            if (i != j) {
                Collections.swap(copy, i, j);
                repairFrom(copy, Math.min(i, j));
            }
        } else if (op == 1 && !copy.isEmpty()) {
            // remove a random box, then try to insert a new one
            int pos = rand.nextInt(copy.size());
            copy.remove(pos);
            tryInsert(copy, all, pos, rand);
            repairFrom(copy, pos);
        } else if (op == 2 && !copy.isEmpty()) {
            // replace a random box with another unused orientation that fits
            int pos = rand.nextInt(copy.size());
            Set<Integer> usedBoxes = new HashSet<>();
            for (Orientation o : copy) usedBoxes.add(o.originalIndex);
            List<Orientation> candidates = new ArrayList<>();
            for (Orientation o : all) {
                if (!usedBoxes.contains(o.originalIndex) && fits(copy, o, pos)) {
                    candidates.add(o);
                }
            }
            if (!candidates.isEmpty()) {
                copy.set(pos, candidates.get(rand.nextInt(candidates.size())));
                repairFrom(copy, pos + 1);
            }
        } else {
            // rebuild the stack above a random cut point
            if (copy.isEmpty()) return copy;
            int cut = rand.nextInt(copy.size());
            List<Orientation> prefix = new ArrayList<>(copy.subList(0, cut));
            Set<Integer> usedBoxes = new HashSet<>();
            for (Orientation o : prefix) usedBoxes.add(o.originalIndex);
            List<Orientation> rest = new ArrayList<>();
            Orientation below = cut > 0 ? prefix.get(cut - 1) : null;
            for (Orientation o : all) {
                if (!usedBoxes.contains(o.originalIndex) && (below == null || (o.width < below.width && o.depth < below.depth))) {
                    rest.add(o);
                }
            }
            Collections.shuffle(rest, rand);
            for (Orientation o : rest) {
                if (prefix.isEmpty() || (o.width < prefix.get(prefix.size() - 1).width && o.depth < prefix.get(prefix.size() - 1).depth)) {
                    prefix.add(o);
                }
            }
            copy = prefix;
        }
        return copy;
    }

    /**
     * tries to insert a random valid unused orientation at a position
     */
    private static void tryInsert(List<Orientation> stack, List<Orientation> all, int pos, Random rand) {
        Set<Integer> usedBoxes = new HashSet<>();
        for (Orientation o : stack) usedBoxes.add(o.originalIndex);
        List<Orientation> candidates = new ArrayList<>();
        for (Orientation o : all) {
            if (!usedBoxes.contains(o.originalIndex) && fits(stack, o, pos)) {
                candidates.add(o);
            }
        }
        if (!candidates.isEmpty()) {
            stack.add(pos, candidates.get(rand.nextInt(candidates.size())));
        }
    }

    /**
     * repairs the stack from a given index upward, removing any boxes that violate the strict-fitting rule
     */
    private static void repairFrom(List<Orientation> stack, int from) {
        for (int i = stack.size() - 1; i > from; i--) {
            if (!fits(stack, stack.get(i), i)) {
                stack.remove(i);
            }
        }
    }

    /**
     * checks if orientation o can be inserted at position pos in the stack
     */
    private static boolean fits(
        List<Orientation> stack, Orientation o, int pos
    ) {
        if (pos > 0) {
            Orientation below = stack.get(pos - 1);
            if (!(o.width < below.width && o.depth < below.depth)) return false;
        }
        if (pos < stack.size()) {
            Orientation above = stack.get(pos);
            if (!(above.width < o.width && above.depth < o.depth)) return false;
        }
        return true;
    }

    // ———————————————————————————————————
    // i/o and helpers

    /**
     * returns the total height of a stack
     */
    private static double stackHeight(List<Orientation> stack) {
        return stack.stream().mapToDouble(o -> o.height).sum();
    }

    /**
     * reads box dimensions from a file (three positive integers per line)
     */
    private static List<int[]> readBoxes(String filename) {
        List<int[]> boxes = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] t = line.trim().split("\\s+");
                if (t.length != 3) continue;
                try {
                    int a = Integer.parseInt(t[0]);
                    int b = Integer.parseInt(t[1]);
                    int c = Integer.parseInt(t[2]);
                    if (a > 0 && b > 0 && c > 0) boxes.add(new int[]{a, b, c});
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException e) {
            System.err.println("file error: " + e.getMessage());
            System.exit(1);
        }
        return boxes;
    }

    /**
     * generates all 3 orientations for each box (w <= d for consistency)
     */
    private static List<Orientation> buildOrientations(List<int[]> boxes) {
        List<Orientation> orientations = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            int[] b = boxes.get(i);
            int x = b[0], y = b[1], z = b[2];
            orientations.add(new Orientation(Math.min(x, y), Math.max(x, y), z, i));
            orientations.add(new Orientation(Math.min(x, z), Math.max(x, z), y, i));
            orientations.add(new Orientation(Math.min(y, z), Math.max(y, z), x, i));
        }
        return orientations;
    }

    /**
     * prints the stack from top to bottom, showing cumulative height
     */
    private static void printStack(List<Orientation> stack) {
        double cum = 0;
        for (int i = stack.size() - 1; i >= 0; i--) {
            Orientation o = stack.get(i);
            cum += o.height;
            System.out.printf("%d %d %d %.0f\n", o.width, o.depth, o.height, cum);
        }
    }
}
