import java.io.*;
import java.util.*;

/**
 * A heuristic solver for the “tallest box stack” problem.
 * uses a greedy base‐area seed, then refines via simulated annealing.
 *
 * Author: Kai Meiklejohn (1632448)
 */
public class NPCStack {

    /** represents one orientation of a box. */
    static class Orientation {
        int width, depth, height;
        int originalIndex;  // index of the source box

        Orientation(int w, int d, int h, int idx) {
            this.width = w;
            this.depth = d;
            this.height = h;
            this.originalIndex = idx;
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("usage: java NPCStack <input-file> <initial-temp> <cooling-rate>");
            System.exit(1);
        }

        String filename = args[0];
        int initialT = Integer.parseInt(args[1]);
        double coolingR = Double.parseDouble(args[2]);

        List<int[]> rawBoxes = readBoxes(filename);
        if (rawBoxes.isEmpty()) {
            System.err.println("no valid boxes found.");
            System.exit(1);
        }

        List<Orientation> orientations = buildOrientations(rawBoxes);

        List<Orientation> current = generateInitialSolutionGreedy(orientations);





        // MAKE SURE TO GET RID OF THIS LINE
        System.out.println("Initial greedy seed height: " + stackHeight(current));








        List<Orientation> best = simulatedAnnealing(current, orientations, initialT, coolingR);

        printStack(best);
    }

    private static List<Orientation> generateInitialSolutionGreedy(List<Orientation> orients) {
        orients.sort((a,b) -> Integer.compare(b.width*b.depth, a.width*a.depth));
        List<Orientation> stack = new ArrayList<>();
        for (Orientation o : orients) {
            if (stack.isEmpty()) {
                stack.add(o);
            } else {
                Orientation top = stack.get(stack.size() - 1);
                if (o.width < top.width
                 && o.depth < top.depth
                 && !containsBox(stack, o.originalIndex)) {
                    stack.add(o);
                }
            }
        }
        return stack;
    }

    private static List<int[]> readBoxes(String fn) {
        List<int[]> boxes = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(fn))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] tok = line.trim().split("\\s+");
                if (tok.length != 3) continue;
                try {
                    int d1 = Integer.parseInt(tok[0]);
                    int d2 = Integer.parseInt(tok[1]);
                    int d3 = Integer.parseInt(tok[2]);
                    if (d1>0 && d2>0 && d3>0) {
                        boxes.add(new int[]{d1,d2,d3});
                    }
                } catch (NumberFormatException e) {
                    System.err.println("invalid box dimensions: " + line);
                    continue;
                }
            }
        } catch (IOException e) {
            System.err.println("error reading file: " + e.getMessage());
            System.exit(1);
        }
        return boxes;
    }

    private static List<Orientation> buildOrientations(List<int[]> boxes) {
        List<Orientation> list = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            int[] b = boxes.get(i);
            int x = b[0], y = b[1], z = b[2];
            list.add(new Orientation(Math.min(x,y), Math.max(x,y), z, i));
            list.add(new Orientation(Math.min(x,z), Math.max(x,z), y, i));
            list.add(new Orientation(Math.min(y,z), Math.max(y,z), x, i));
        }
        return list;
    }

    private static List<Orientation> simulatedAnnealing(
        List<Orientation> current,
        List<Orientation> allOrients,
        int initialT,
        double coolingR
    ) {
        Random rnd = new Random();
        double t = initialT;
        List<Orientation> best = new ArrayList<>(current);
        double bestHeight = stackHeight(best);

        while (t > 0.1) {
            for (int iter = 0; iter < 10; iter++) {
                List<Orientation> neighbour = neighbour(current, allOrients, t, rnd);
                double curH   = stackHeight(current);
                double neighH = stackHeight(neighbour);
                double delta  = neighH - curH;

                if (delta > 0 || rnd.nextDouble() < Math.exp(delta / t)) {
                    current = neighbour;
                    if (neighH > bestHeight) {
                        best = new ArrayList<>(current);
                        bestHeight = neighH;
                    }
                }
            }
            t *= (1 - coolingR / initialT);
        }
        return best;
    }

    private static double stackHeight(List<Orientation> stack) {
        return stack.stream().mapToDouble(o -> o.height).sum();
    }

    private static List<Orientation> neighbour(
        List<Orientation> current,
        List<Orientation> all,
        double t,
        Random rnd
    ) {
        int k = 1 + rnd.nextInt((int)Math.ceil(t));
        List<Orientation> copy = new ArrayList<>(current);
        for (int i = 0; i < k; i++) {
            int op = rnd.nextInt(3);
            if (op == 0 && !copy.isEmpty()) {
                copy.remove(rnd.nextInt(copy.size()));
            } else if (op == 1) {
                Orientation o = all.get(rnd.nextInt(all.size()));
                if (!containsBox(copy, o.originalIndex)) {
                    for (int pos = 0; pos <= copy.size(); pos++) {
                        if (fitsAt(copy, o, pos)) {
                            copy.add(pos, o);
                            break;
                        }
                    }
                }
            } else {
                if (!copy.isEmpty()) {
                    int idx = rnd.nextInt(copy.size());
                    copy.remove(idx);
                    Orientation o = all.get(rnd.nextInt(all.size()));
                    if (!containsBox(copy, o.originalIndex) && fitsAt(copy, o, idx)) {
                        copy.add(idx, o);
                    }
                }
            }
            repair(copy);
        }
        return copy;
    }

    private static boolean containsBox(List<Orientation> stack, int boxIdx) {
        return stack.stream().anyMatch(o -> o.originalIndex == boxIdx);
    }

    private static boolean fitsAt(List<Orientation> stack, Orientation o, int pos) {
        if (pos > 0) {
            Orientation below = stack.get(pos-1);
            if (!(o.width < below.width && o.depth < below.depth)) return false;
        }
        if (pos < stack.size()) {
            Orientation above = stack.get(pos);
            if (!(above.width < o.width && above.depth < o.depth)) return false;
        }
        return true;
    }

    private static void repair(List<Orientation> stack) {
        for (int i = stack.size()-1; i > 0; i--) {
            Orientation top = stack.get(i), below = stack.get(i-1);
            if (!(top.width < below.width && top.depth < below.depth)) {
                stack.remove(i);
            }
        }
    }

    private static void printStack(List<Orientation> stack) {
        double cumH = 0;
        for (int i = stack.size() - 1; i >= 0; i--) {
            Orientation o = stack.get(i);
            cumH += o.height;
            System.out.printf("%d %d %d %.0f%n",
                              o.width, o.depth, o.height, cumH);
        }
    }
}
