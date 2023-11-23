package Helpers;

public class Order {
    public int id;
    public int teas;
    public int coffees;
    public boolean complete;

    public Order(int id, int tea, int coffee){
        this.id = id;
        this.teas = tea;
        this.coffees = coffee;
        this.complete = false;
    }

}
