package Helpers;

public class Drink {
    public int id;
    public boolean ready;
    public DrinkType type;

    public Drink(int id, DrinkType type){
        this.id = id;
        this.type = type;
        this.ready = false;
    }
    public enum DrinkType {
        Coffee("Coffee"),
        Tea("Tea");
        public final String label;
        private DrinkType(String label) {
            this.label = label;
        }};
}
