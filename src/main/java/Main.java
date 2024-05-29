import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

public class Main {

    public static final int TOTAL_DISTANCE_TO_RUN = 1000;

    public static void main(String[] args) throws InterruptedException {
        Road road = new Road(TOTAL_DISTANCE_TO_RUN);
        List<HorseThread> horses = initializeHorses(road);
        Announcer announcer = new Announcer(horses, road);
        Linesman linesman = new Linesman(horses);
        horses.forEach(Thread::start);
        announcer.start();
        linesman.start();
        linesman.join();
        announcer.announceWinners(linesman.getWinnersByOrder());
    }

    private static List<HorseThread> initializeHorses(Road road) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Please enter the horse amount to run:");
        int horsesAmount = scanner.nextInt();
        List<HorseThread> horses = new ArrayList<>();

        for (int i = 0; i < horsesAmount; i++) {
            HorseThread horse = new HorseThread("c"+ (i+1), road);
            horses.add(horse);
        }
        return horses;
    }

    public static class HorseThread extends Thread{
        private final Road road;
        private volatile int distanceTraveled = 0;
        private final Integer velocity;
        private final Integer resistance;
        private final String name;
        private final Random random = new Random();

        public HorseThread(String name, Road road) {
            this.velocity = random.nextInt(1, 4);
            this.resistance = random.nextInt(1, 4);
            this.name = name;
            this.road = road;
            setName(name);
            System.out.printf("""
                Creando caballo con las siguientes estadísticas:
                nombre = %s
                velocidad = %s
                resistencia = %s
                %n""", name, velocity, resistance
            );
        }

        public synchronized void moveForward(Integer distance) {
            distanceTraveled += distance == null ? velocity : distance;
        }

        public void rest() {
            int timeToRest;
            synchronized (random) {
                timeToRest = Math.max(random.nextInt(1, 6) - this.resistance, 0);
            }
            sleep(timeToRest);
        }

        private static void sleep(int seconds) {
            try {
                Thread.sleep(seconds * 1000L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        public synchronized int getActualPosition() {
            return distanceTraveled;
        }

        @Override
        public void run() {
            while (distanceTraveled < road.getTotalDistance()) {
                moveForward(null);
                road.checkPowerUpIsAvailable(this);
                rest();
            }
        }

        public boolean hasFinished() {
            return distanceTraveled >= road.totalDistance;
        }

        public String getHorseName() {
            return name;
        }
    }

    public static class Road {
        private final int totalDistance;
        private final Semaphore powerUp = new Semaphore(1, true);
        private final Random random = new Random();
        private final Range powerUpRange;

        public Road(int totalDistance) {
            this.totalDistance = totalDistance;
            this.powerUpRange = generatePowerUpZone();
        }

        private Range generatePowerUpZone() {
            int lowerLimit = random.nextInt(0, 951);
            int upperLimit = lowerLimit + 50;
            return new Range(lowerLimit, upperLimit);
        }

        public void checkPowerUpIsAvailable(HorseThread horse) {
            if (horse.getActualPosition() > powerUpRange.lowerLimit() && horse.getActualPosition() < powerUpRange.upperLimit()) {
                try {
                    powerUp.acquire();
                    Thread.sleep(7 * 1000);
                    horse.moveForward(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    powerUp.release();
                }
            }
        }

        public int getTotalDistance() {
            return totalDistance;
        }

        public Range getPowerUpRange() {
            return powerUpRange;
        }
    }

    public static class Announcer extends Thread {
        private final List<HorseThread> horses;
        private final Road road;

        public Announcer(List<HorseThread> horses, Road road) {
            this.horses = horses;
            this.road = road;
            setDaemon(true);
            setName("Announcer Thread");
            announceRoad();
        }

        public void announceWinners(List<HorseThread> winners) {
            System.out.printf("""
                    horse winners:
                    1. %s
                    2. %s
                    3. %s
                    %n""", winners.get(0).getHorseName(), winners.get(1).getHorseName(), winners.get(2).getHorseName()
            );
        }

        public void announceRoad() {
            System.out.printf("""
                    Total road distance: %s
                    powerup generated between %s and %s
                    %n""", road.getTotalDistance(), road.getPowerUpRange().lowerLimit(), road.powerUpRange.upperLimit());

        }

        @Override
        public void run() {
            while (true) {
                StringBuilder output = new StringBuilder();
                horses.forEach(h -> {
                    output.append(h.name).append(": ").append(h.getActualPosition()).append(" | ");
                });
                System.out.print(output + "\r");
            }
        }
    }

    public static class Linesman extends Thread {
        private final List<HorseThread> horses;
        private final List<HorseThread> winnersByOrder = new ArrayList<>();
        private final ReentrantLock lock = new ReentrantLock();

        public Linesman(List<HorseThread> horses) {
            this.horses = horses;
            setName("Linesman Thread");
        }

        public List<HorseThread> getWinnersByOrder() {
            return winnersByOrder;
        }

        @Override
        public void run() {
            while (winnersByOrder.size() != horses.size()) {
                try {
                    lock.lock();
                    horses.forEach(h -> {
                        if (h.hasFinished() && !winnersByOrder.contains(h)) {
                            winnersByOrder.add(h);
                        }
                    });
                } finally {
                    lock.unlock();
                }
            }
        }
    }
}
