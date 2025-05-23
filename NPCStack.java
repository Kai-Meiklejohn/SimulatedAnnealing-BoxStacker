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

        System.out.println("pre-prune height: " + stackHeight(dpStack));

        // 4) prune duplicates (single-use) from that DP stack
        List<Orientation> seed = pruneToSingleUse(dpStack);

        System.out.println("seed height: " + stackHeight(seed));

        // 5) refine via simulated annealing
        List<Orientation> best = simulatedAnnealing(seed, all, T0, cooling);

        // 6) output top→bottom
        printStack(best);
    }

    /** DP that allows reuse of any orientation (i.e. ignores originalIndex) */
    private static List<Orientation> dpAllowReuse(List<Orientation> orients) {
        int n = orients.size();
        orients.sort((a,b)->Integer.compare(b.width*b.depth, a.width*a.depth));
        double[] dp     = new double[n];
        int[]    parent = new int[n];
        for (int i = 0; i < n; i++) {
            dp[i] = orients.get(i).height;
            parent[i] = -1;
            for (int j = 0; j < i; j++) {
                Orientation top = orients.get(j), cur = orients.get(i);
                if (cur.width < top.width && cur.depth < top.depth) {
                    double cand = dp[j] + cur.height;
                    if (cand > dp[i]) {
                        dp[i] = cand;
                        parent[i] = j;
                    }
                }
            }
        }
        // find best index
        int bestIdx = 0;
        for (int i = 1; i < n; i++)
            if (dp[i] > dp[bestIdx]) bestIdx = i;

        // backtrack bottom→top
        LinkedList<Orientation> stack = new LinkedList<>();
        for (int cur = bestIdx; cur != -1; cur = parent[cur])
            stack.addFirst(orients.get(cur));
        return stack;
    }

    /** remove all but first occurrence of each originalIndex */
    private static List<Orientation> pruneToSingleUse(List<Orientation> stack) {
        Set<Integer> used = new HashSet<>();
        List<Orientation> pruned = new ArrayList<>();
        for (Orientation o : stack) {
            if (used.add(o.originalIndex)) {
                pruned.add(o);
            }
        }
        return pruned;
    }

    /** pure simulated annealing improving on the single-use candidate */
    private static List<Orientation> simulatedAnnealing(
        List<Orientation> current,
        List<Orientation> all,
        double T0,
        double cooling
    ) {
        Random rnd = new Random();
        double T = T0;
        List<Orientation> best = new ArrayList<>(current);
        double bestH = stackHeight(best);

        while (T > 1e-3) {
            // try several neighbours per temperature
            for (int it = 0; it < 20; it++) {
                List<Orientation> cand = neighbour(current, all, rnd);
                double hCur  = stackHeight(current);
                double hCand = stackHeight(cand);
                double Δ = hCand - hCur;
                if (Δ > 0 || rnd.nextDouble() < Math.exp(Δ/T)) {
                    current = cand;
                    if (hCand > bestH) {
                        best = new ArrayList<>(cand);
                        bestH = hCand;
                    }
                }
            }
            T *= (1 - cooling/T0);
        }
        return best;
    }

    /**
     * More exploratory neighbour: swap, remove+insert, replace, or rebuild above a cut point.
     */
    private static List<Orientation> neighbour(
        List<Orientation> cur,
        List<Orientation> all,
        Random rnd
    ) {
        List<Orientation> copy = new ArrayList<>(cur);
        int op = rnd.nextInt(4);

        if (op == 0 && copy.size() > 1) {
            // Swap two positions, then repair above the lower index
            int i = rnd.nextInt(copy.size()), j = rnd.nextInt(copy.size());
            if (i != j) {
                Collections.swap(copy, i, j);
                repairFrom(copy, Math.min(i, j));
            }
        } else if (op == 1 && !copy.isEmpty()) {
            // Remove a random box, then try to insert a new one at that position
            int pos = rnd.nextInt(copy.size());
            copy.remove(pos);
            tryInsert(copy, all, pos, rnd);
            repairFrom(copy, pos);
        } else if (op == 2 && !copy.isEmpty()) {
            // Replace a random box with another unused orientation that fits
            int pos = rnd.nextInt(copy.size());
            Set<Integer> used = new HashSet<>();
            for (Orientation o : copy) used.add(o.originalIndex);
            List<Orientation> candidates = new ArrayList<>();
            for (Orientation o : all) {
                if (!used.contains(o.originalIndex) && fits(copy, o, pos)) {
                    candidates.add(o);
                }
            }
            if (!candidates.isEmpty()) {
                copy.set(pos, candidates.get(rnd.nextInt(candidates.size())));
                repairFrom(copy, pos + 1);
            }
        } else {
            // Rebuild the stack above a random cut point
            if (copy.isEmpty()) return copy;
            int cut = rnd.nextInt(copy.size());
            List<Orientation> prefix = new ArrayList<>(copy.subList(0, cut));
            Set<Integer> used = new HashSet<>();
            for (Orientation o : prefix) used.add(o.originalIndex);
            List<Orientation> rest = new ArrayList<>();
            Orientation below = cut > 0 ? prefix.get(cut - 1) : null;
            for (Orientation o : all) {
                if (!used.contains(o.originalIndex) && (below == null || (o.width < below.width && o.depth < below.depth))) {
                    rest.add(o);
                }
            }
            Collections.shuffle(rest, rnd);
            for (Orientation o : rest) {
                if (prefix.isEmpty() || (o.width < prefix.get(prefix.size() - 1).width && o.depth < prefix.get(prefix.size() - 1).depth)) {
                    prefix.add(o);
                }
            }
            copy = prefix;
        }
        return copy;
    }

    /** Try to insert a random valid unused orientation at a position. */
    private static void tryInsert(List<Orientation> stack, List<Orientation> all, int pos, Random rnd) {
        Set<Integer> used = new HashSet<>();
        for (Orientation o : stack) used.add(o.originalIndex);
        List<Orientation> candidates = new ArrayList<>();
        for (Orientation o : all) {
            if (!used.contains(o.originalIndex) && fits(stack, o, pos)) {
                candidates.add(o);
            }
        }
        if (!candidates.isEmpty()) {
            stack.add(pos, candidates.get(rnd.nextInt(candidates.size())));
        }
    }

    /** Repair stack from a given index upward (removes any boxes violating the strict-fitting rule). */
    private static void repairFrom(List<Orientation> stack, int from) {
        for (int i = stack.size() - 1; i > from; i--) {
            if (!fits(stack, stack.get(i), i)) {
                stack.remove(i);
            }
        }
    }

    /** test if o may be inserted at position pos in stack */
    private static boolean fits(
        List<Orientation> stack, Orientation o, int pos
    ) {
        if (pos>0) {
            Orientation below = stack.get(pos-1);
            if (!(o.width<below.width && o.depth<below.depth)) return false;
        }
        if (pos<stack.size()) {
            Orientation above = stack.get(pos);
            if (!(above.width<o.width && above.depth<o.depth)) return false;
        }
        return true;
    }

    // ———————————————————————————————————
    // I/O and helpers

    private static double stackHeight(List<Orientation> stk) {
        return stk.stream().mapToDouble(o->o.height).sum();
    }

    private static List<int[]> readBoxes(String fn) {
        List<int[]> L = new ArrayList<>();
        try(BufferedReader r=new BufferedReader(new FileReader(fn))){
            String line;
            while((line=r.readLine())!=null){
                String[] t=line.trim().split("\\s+");
                if(t.length!=3) continue;
                try {
                    int a=Integer.parseInt(t[0]),
                        b=Integer.parseInt(t[1]),
                        c=Integer.parseInt(t[2]);
                    if(a>0&&b>0&&c>0) L.add(new int[]{a,b,c});
                } catch(NumberFormatException ignored){}
            }
        } catch(IOException e){
            System.err.println("file error: "+e.getMessage());
            System.exit(1);
        }
        return L;
    }

    private static List<Orientation> buildOrientations(List<int[]> boxes) {
        List<Orientation> out = new ArrayList<>();
        for(int i=0;i<boxes.size();i++){
            int[] b = boxes.get(i);
            int x = b[0], y = b[1], z = b[2];
            out.add(new Orientation(Math.min(x,y), Math.max(x,y), z, i));
            out.add(new Orientation(Math.min(x,z), Math.max(x,z), y, i));
            out.add(new Orientation(Math.min(y,z), Math.max(y,z), x, i));
        }
        return out;
    }

    private static void printStack(List<Orientation> stk) {
        double cum=0;
        for(int i=stk.size()-1;i>=0;i--){
            Orientation o=stk.get(i);
            cum += o.height;
            System.out.printf("%d %d %d %.0f%n",
                              o.width, o.depth, o.height, cum);
        }
    }
}
