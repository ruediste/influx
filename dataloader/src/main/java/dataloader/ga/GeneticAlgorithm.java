package dataloader.ga;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class GeneticAlgorithm<T extends GeneticAlgorithm.Speciem<T>> {
    public static class GAParameters {
        public int populationSize = 100;
        public double mutationDistance = 0.1;

        public double pMutation = 0.45;
        public double pMerge = 0.45;
    }

    public interface ParameterAccessor<T> {
        void setFrom(T other);

        void mutate(double distance);
    }

    public static abstract class Speciem<TSelf> implements Cloneable {
        public double fitness;

        abstract public List<ParameterAccessor<TSelf>> getParameters();

        abstract public void calculateFitness();

        @SuppressWarnings("unchecked")
        @Override
        public TSelf clone() {
            try {
                return (TSelf) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public GAParameters parameters = new GAParameters();

    public Supplier<T> speciemFactory;
    public BooleanSupplier finishCriteria = new BooleanSupplier() {

        int count = 0;

        @Override
        public boolean getAsBoolean() {
            return count++ > 100;
        }
    };

    public List<T> run() {
        Random random = new Random();
        // initialize first generation
        ArrayList<T> currentGeneration = new ArrayList<>(parameters.populationSize);
        for (int i = 0; i < parameters.populationSize; i++) {
            currentGeneration.add(speciemFactory.get());
        }

        int generation = 0;
        // loop generations
        while (!finishCriteria.getAsBoolean()) {
            // calculate fitness
            currentGeneration.forEach(Speciem::calculateFitness);
            // order speciem by fitness
            currentGeneration.sort(Comparator.comparing(x -> x.fitness));

            ArrayList<T> nextGeneration = new ArrayList<>(parameters.populationSize);
            {
                T fittest = currentGeneration.get(currentGeneration.size() - 1);
                fittest.calculateFitness();
                System.out.println("Generation " + generation + ": fitness: " + fittest.fitness + " " + fittest);
                nextGeneration.add(fittest);
            }

            // derive next generation from previous generation
            while (nextGeneration.size() < parameters.populationSize) {
                double r = random.nextDouble();
                r -= parameters.pMutation;
                if (r <= 0) {
                    // mutate
                    T source = chooseRandom(random, currentGeneration).clone();
                    List<ParameterAccessor<T>> params = source.getParameters();
                    int idx = random.nextInt(params.size());
                    params.get(idx).mutate(this.parameters.mutationDistance);
                    nextGeneration.add(source);
                    continue;
                }

                r -= parameters.pMerge;
                if (r <= 0) {
                    // merge
                    T source1 = chooseRandom(random, currentGeneration).clone();
                    T source2 = chooseRandom(random, currentGeneration);
                    if (source1 != source2) {
                        List<ParameterAccessor<T>> params = source1.getParameters();
                        int idx = random.nextInt(params.size());
                        params.get(idx).setFrom(source2);
                    }
                    nextGeneration.add(source1);
                    continue;
                }

                // generate new
                nextGeneration.add(speciemFactory.get());
            }
            currentGeneration = nextGeneration;
            generation++;
        }

        return currentGeneration;
    }

    private T chooseRandom(Random r, List<T> list) {
        int n = list.size();
        int s = r.nextInt((n + 1) * n / 2);
        // s=(n+1)*n/2
        // n^2+n-2s=0
        // n=(-1+sqrt(1+8*s))/2
        int idx = (int) Math.round(-1 + Math.sqrt(1 + 8 * s) / 2);
        return list.get(idx);
    }
}
