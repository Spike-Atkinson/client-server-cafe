package Helpers;

public class BrewingJob implements Runnable {
    Drink drink;
    public BrewingJob(Drink drink){
        this.drink = drink;
    }
    @Override
    public void run() {

        if (this.drink.type == Drink.DrinkType.Tea){
            try {
                Thread.sleep(30000); // 30 seconds
                drink.ready = true;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        else if (this.drink.type == Drink.DrinkType.Coffee){
            try {
                Thread.sleep(45000); // 45 seconds
                drink.ready = true;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
