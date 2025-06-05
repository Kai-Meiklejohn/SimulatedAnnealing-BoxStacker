import java.io.*;
import java.util.*;

/**
 * Box-stacker: greedy algorithm to make initial stack,,
 * then simulated annealing to refine.
 *
 * Author: Kai Meiklejohn (1632448)
 */
public class NPCStack {

    /**
     * represents a single orientation of a box (width <= depth for consistency)
     */
    static class boxOrientation {
        int width, depth, height, originalBoxIndex;
        boxOrientation(int width, int depth, int height, int originalBoxIndex) {
            this.width = width;
            this.depth = depth;
            this.height = height;
            this.originalBoxIndex = originalBoxIndex;
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("usage: java NPCStack <file> <initial-temp> <cooling-rate>");
            System.exit(1);
        }
        String inputFile = args[0];
        double initialTemp = Double.parseDouble(args[1]);
        double coolingRate = Double.parseDouble(args[2]);

        // load all boxes from file
        List<int[]> boxDimensions = readBoxesFromFile(inputFile);
        if (boxDimensions.isEmpty()) {
            System.err.println("no boxes!");
            System.exit(1);
        }

        // generate all 3 orientations for each box
        List<boxOrientation> allOrientations = generateAllOrientations(boxDimensions);

        // refine the stack using simulated annealing
        List<boxOrientation> bestStack = runSimulatedAnnealing(allOrientations, initialTemp, coolingRate);

        // print the stack from top to bottom
        printStackWithCumulativeHeight(bestStack);
    }

    /**
     * builds a feasible stack greedily always adds the next largest box by base area that fits and is unused
     * this is a simple greedy heuristic not optimal but fast
     */
    private static List<boxOrientation> buildGreedyStack(List<boxOrientation> orientations) {
        // Sort all orientations by decreasing base area (width * depth)
        List<boxOrientation> sorted = new ArrayList<>(orientations);
        sorted.sort((a, b) -> Integer.compare(b.width * b.depth, a.width * a.depth));
        Set<Integer> usedBoxIndices = new HashSet<>();
        List<boxOrientation> stack = new ArrayList<>();
        boxOrientation boxBelow = null;
        for (boxOrientation orientation : sorted) {
            // only use each box once (by original index)
            if (usedBoxIndices.contains(orientation.originalBoxIndex)) continue;
            // only stack if it fits strictly inside the box below
            if (boxBelow == null || (orientation.width < boxBelow.width && orientation.depth < boxBelow.depth)) {
                stack.add(orientation);
                usedBoxIndices.add(orientation.originalBoxIndex);
                boxBelow = orientation;
            }
        }
        return stack;
    }

    /**
     * performs simulated annealing to maximize stack height using multiple random modifications per neighbor
     * this method keeps generating neighbor stacks by making random changes and sometimes accepts them based on their height and the current temperature
     */
    private static List<boxOrientation> runSimulatedAnnealing(
        List<boxOrientation> allOrientations,
        double initialTemp,
        double coolingRate
    ) {
        // start with a greedy initial stack
        List<boxOrientation> currentStack = buildGreedyStack(allOrientations);
        List<boxOrientation> bestStack = new ArrayList<>(currentStack);
        double bestHeight = calculateStackHeight(bestStack);
        Random random = new Random();
        int iterationsPerTemp = 200;
        double temperature = initialTemp;
        while (temperature > 0) {
            for (int iter = 0; iter < iterationsPerTemp; iter++) {
                // create a neighbor stack by making random changes
                List<boxOrientation> neighborStack = new ArrayList<>(currentStack);
                int maxChanges = Math.max(1, (int) Math.floor(temperature));
                int numChanges = 1 + random.nextInt(maxChanges);
                for (int c = 0; c < numChanges; c++) {
                    boolean doRemove = !neighborStack.isEmpty() && random.nextBoolean();
                    if (doRemove) {
                        // Remove a random box and try to restack above
                        int removeIdx = random.nextInt(neighborStack.size());
                        List<boxOrientation> aboveBuffer = new ArrayList<>();
                        // Remove all boxes above the chosen index and store them
                        for (int j = neighborStack.size() - 1; j > removeIdx; j--) {
                            aboveBuffer.add(neighborStack.get(j));
                            neighborStack.remove(j);
                        }
                        neighborStack.remove(removeIdx);
                        Collections.reverse(aboveBuffer);
                        // Try to restack the removed boxes if they still fit
                        for (boxOrientation o : aboveBuffer) {
                            if (neighborStack.isEmpty() || (o.width < neighborStack.get(neighborStack.size() - 1).width && o.depth < neighborStack.get(neighborStack.size() - 1).depth)) {
                                neighborStack.add(o);
                            }
                        }
                    } else {
                        // Try to insert a new box at a random position if possible
                        int insertPos = random.nextInt(neighborStack.size() + 1);
                        Set<Integer> usedBoxIndices = new HashSet<>();
                        for (boxOrientation o : neighborStack) usedBoxIndices.add(o.originalBoxIndex);
                        boxOrientation boxBelow = (insertPos == 0 ? null : neighborStack.get(insertPos - 1));
                        List<boxOrientation> candidates = new ArrayList<>();
                        // Find all unused orientations that fit below and above
                        for (boxOrientation o : allOrientations) {
                            if (usedBoxIndices.contains(o.originalBoxIndex)) continue;
                            if (boxBelow != null && !(o.width < boxBelow.width && o.depth < boxBelow.depth)) continue;
                            // check that all above still fit
                            boolean fitsAbove = true;
                            boxOrientation tempBelow = o;
                            for (int k = insertPos; k < neighborStack.size(); k++) {
                                boxOrientation above = neighborStack.get(k);
                                if (!(above.width < tempBelow.width && above.depth < tempBelow.depth)) {
                                    fitsAbove = false;
                                    break;
                                }
                                tempBelow = above;
                            }
                            if (fitsAbove) candidates.add(o);
                        }
                        if (!candidates.isEmpty()) {
                            boxOrientation toAdd = candidates.get(random.nextInt(candidates.size()));
                            List<boxOrientation> aboveSegment = new ArrayList<>();
                            // Remove all boxes above the insert position
                            for (int j = neighborStack.size() - 1; j >= insertPos; j--) {
                                aboveSegment.add(neighborStack.get(j));
                                neighborStack.remove(j);
                            }
                            neighborStack.add(toAdd);
                            Collections.reverse(aboveSegment);
                            // Try to restack the removed boxes if they still fit
                            for (boxOrientation o : aboveSegment) {
                                if (neighborStack.isEmpty() || (o.width < neighborStack.get(neighborStack.size() - 1).width && o.depth < neighborStack.get(neighborStack.size() - 1).depth)) {
                                    neighborStack.add(o);
                                }
                            }
                        }
                    }
                }
                double currentHeight = calculateStackHeight(currentStack);
                double neighborHeight = calculateStackHeight(neighborStack);
                boolean accept;
                if (neighborHeight > currentHeight) {
                    accept = true;
                } else {
                    double delta = neighborHeight - currentHeight;
                    double probability = Math.exp(delta / temperature);
                    accept = (probability > random.nextDouble());
                }
                if (accept) {
                    currentStack = neighborStack;
                    if (neighborHeight > bestHeight) {
                        bestStack = new ArrayList<>(neighborStack);
                        bestHeight = neighborHeight;
                    }
                }
            }
            temperature -= coolingRate;
        }
        return bestStack;
    }

    /**
     * returns the total height of a stack
     */
    private static double calculateStackHeight(List<boxOrientation> stack) {
        return stack.stream().mapToDouble(o -> o.height).sum();
    }

    /**
     * reads box dimensions from a file three positive integers per line
     */
    private static List<int[]> readBoxesFromFile(String filename) {
        List<int[]> boxes = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.trim().split("\\s+");
                if (tokens.length != 3) continue;
                try {
                    int a = Integer.parseInt(tokens[0]);
                    int b = Integer.parseInt(tokens[1]);
                    int c = Integer.parseInt(tokens[2]);
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
     * generates all 3 orientations for each box width less than or equal to depth for consistency
     */
    private static List<boxOrientation> generateAllOrientations(List<int[]> boxes) {
        List<boxOrientation> orientations = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            int[] b = boxes.get(i);
            int x = b[0], y = b[1], z = b[2];
            orientations.add(new boxOrientation(Math.min(x, y), Math.max(x, y), z, i));
            orientations.add(new boxOrientation(Math.min(x, z), Math.max(x, z), y, i));
            orientations.add(new boxOrientation(Math.min(y, z), Math.max(y, z), x, i));
        }
        return orientations;
    }

    /**
     * prints the stack from top to bottom showing cumulative height at the point where the box is top-most
     * each line is width depth height cumulativeHeightAtTop
     */
    private static void printStackWithCumulativeHeight(List<boxOrientation> stack) {
        double totalHeight = calculateStackHeight(stack);
        double cumulativeHeight = totalHeight;
        // iterate from the top-most box (end of list) to the bottom-most (start)
        for (int i = stack.size() - 1; i >= 0; i--) {
            boxOrientation box = stack.get(i);
            // print current box with its cumulative height from the top
            System.out.printf("%d %d %d %.0f\n", box.width, box.depth, box.height, cumulativeHeight);
            cumulativeHeight -= box.height;
        }
    }
}
