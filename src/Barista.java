import Helpers.BrewingJob;
import Helpers.Drink;
import Helpers.Order;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class Barista {

    private final static int port = 8888;
    static AtomicReferenceArray<Order> orders = new AtomicReferenceArray<>(4);

    static ArrayList<Drink> waiting = new ArrayList<>();
    static HashMap<Thread, Drink> brewing = new HashMap<>();
    static ArrayList<Drink> tray = new ArrayList<>();

    static Thread listenT;
    //static Thread listenR;

    static AtomicReferenceArray<Customer> customers = new AtomicReferenceArray<>(4);
    static AtomicInteger customerCount = new AtomicInteger(0);
    static AtomicInteger orderCount = new AtomicInteger(0);

    public static void main(String[] args) {
        brewing.put(new Thread("tea"), null);
        brewing.put(new Thread("tea"), null);
        brewing.put(new Thread("coffee"), null);
        brewing.put(new Thread("coffee"), null);

        listenT = new Thread(new Runnable() {
            @Override
            public void run() {
                listenForCustomers();
            }
        });
        listenT.start();

        serve();
    }

    private static void serve() {

        // add new customers
        int id = 1;
        while (true) {
            for (int c = 0; c < customers.length(); c++){
                if (customers.get(c) != null && customers.get(c).socket.isClosed()){
                    customers.set(c, null);
                }
            }

            for (int o = 0; o < orders.length(); o++) {
                Order order = orders.get(o);
                if (orders.get(o) != null) {
                    boolean customerPresent = false;

                    for (int c = 0; c < customers.length(); c++) {
                        Customer cust = customers.get(c);
                        if (cust != null && order.id == cust.id && !cust.socket.isClosed()) {
                            customerPresent = true;
                            break;
                        }

                    }
                    if (!customerPresent) {
                        System.out.println("Customer left without order");
                        orders.set(o, null);
                        orderCount.decrementAndGet();
                        //remove all drinks from absent customer from all trays
                        ArrayList<Drink> unwantedDrinks = new ArrayList<>();
                        for (Drink drink : waiting) {
                            if (drink != null && drink.id == order.id) {
                                unwantedDrinks.add(drink);
                            }
                        }
                        for (Drink drink : unwantedDrinks) {
                            waiting.remove(drink);
                        }
                        unwantedDrinks.clear();

                        for (Thread key : brewing.keySet()) {
                            Drink drink = brewing.get(key);
                            if (drink != null && drink.id == order.id) {
                                brewing.put(key, null);
                            }
                        }

                        for (Drink drink : tray) {
                            if (drink != null && drink.id == order.id) {
                                unwantedDrinks.add(drink);
                            }
                        }
                        for (Drink drink : unwantedDrinks) {
                            tray.remove(drink);
                        }
                        unwantedDrinks.clear();
                        callOutStatus();
                    }
                }
            }

            //take any drinks that are ready and put them in the tray
            boolean drinksFinished = false;
            for (Thread key : brewing.keySet()) {
                Drink drink = brewing.get(key);
                if (drink != null) {
                    if (!key.isAlive() && drink.ready) {
                        // move drink
                        drinksFinished = true;
                        tray.add(drink);
                        brewing.put(key, null);
                    }
                }
            }
            if (drinksFinished) {
                callOutStatus();
            }

            ArrayList<Thread> drinklessThreads = new ArrayList<>();
            for (Thread key : brewing.keySet()) {
                if (brewing.get(key) == null) {
                    drinklessThreads.add(key);
                }
            }
            boolean drinksAdded = false;
            for (Thread key : drinklessThreads) { // add drinks that need to be prepared to empty threads
                if (!waiting.isEmpty()) {
                    if (key.getName().equals("tea")) {
                        for (int d = 0; d < waiting.size(); d++) {
                            Drink drink = waiting.get(d);
                            if (drink != null && drink.type == Drink.DrinkType.Tea) {
                                waiting.remove(drink);
                                brewing.remove(key);
                                key = new Thread(new BrewingJob(drink), "tea");
                                key.start();
                                brewing.put(key, drink);
                                drinksAdded = true;
                                break;
                            }
                        }
                    } else if (key.getName().equals("coffee")) {
                        for (int d = 0; d < waiting.size(); d++) {
                            Drink drink = waiting.get(d);
                            if (drink != null) {
                                if (waiting.get(d).type == Drink.DrinkType.Coffee) {
                                    waiting.remove(drink);
                                    brewing.remove(key);
                                    key = new Thread(new BrewingJob(drink), "coffee");
                                    key.start();
                                    brewing.put(key, drink);
                                    drinksAdded = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            if (drinksAdded) {
                callOutStatus();
            }

            //deliver completed orders
            if (!tray.isEmpty()) {
                for (int o = 0; o < orders.length(); o++) {
                    Order order = orders.get(o);
                    if (order != null) {
                        int coffees = 0;
                        int teas = 0;
                        for (Drink drink : tray) {
                            if (drink.id == order.id) {
                                if (drink.type == Drink.DrinkType.Tea) {
                                    teas++;
                                } else if (drink.type == Drink.DrinkType.Coffee) {
                                    coffees++;
                                }
                            }
                        }
                        if (teas == order.teas && coffees == order.coffees) { // order is complete
                            for (int c = 0; c < customers.length(); c++) {
                                if (customers.get(c) != null) {
                                    Customer cust = customers.get(c);
                                    if (cust.id == order.id) {
                                        String orderStr = formatOrder(order.teas, order.coffees);
                                        talkToCustomer(cust.socket, "Order delivered to " + cust.name + " ( " + orderStr + " )");
                                        cust.idle = true;
                                        orders.set(o, null);
                                        orderCount.decrementAndGet();
                                        break;
                                    }
                                }
                            }
                            ArrayList<Drink> serveDrinks = new ArrayList<>();
                            for (Drink drink : tray) {
                                if (drink.id == order.id) {
                                    serveDrinks.add(drink);
                                }
                            }
                            for (Drink drink : serveDrinks) {
                                tray.remove(drink);
                            }
                            callOutStatus();
                        }
                    }
                }

            }
        }
    }


    public static synchronized void addOrder(Customer cust, int teas, int coffees){
        Order order = null;
        if (!cust.idle) {
            talkToCustomer(cust.socket, "I'll add that to your order.");
            for (int i = 0; i < orders.length(); i++) {
                if (orders.get(i) != null && orders.get(i).id == cust.id) {
                    order = orders.get(i);
                    orders.get(i).teas += teas;
                    orders.get(i).coffees += coffees;
                    break;
                }
            }

        } else {
            order = new Order(cust.id, teas, coffees);
            for (int c = 0; c < orders.length(); c++) {
                if (!(orders.get(c) instanceof Order)) {
                    orders.set(c, order);
                    orderCount.incrementAndGet();
                    break;
                }
                else if (c == orders.length()-1){
                    AtomicReferenceArray<Order> tempArray = new AtomicReferenceArray<>(orders.length()*2);
                    for (int s = 0; s < orders.length(); s++){
                        tempArray.set(s, orders.get(s));
                    }
                    orders = tempArray;
                }
            }
            int activeOrders = 0;
            for (int a = 0; a < orders.length(); a++){
                if (orders.get(a) != null){
                    activeOrders++;
                }
            }
            orderCount.set(activeOrders);

            if (activeOrders < (orders.length() / 4)){
                AtomicReferenceArray<Order> tempArray = new AtomicReferenceArray<>(orders.length()/2);
                int used = 0;
                for (int s = 0; s < orders.length(); s++){
                    if (orders.get(s) != null) {
                        tempArray.set(used, orders.get(s));
                        used++;
                    }
                }
                orders = tempArray;
            }
}
        if (order != null) {
            for (int i = 0; i < teas; i++) {
                waiting.add(new Drink(order.id, Drink.DrinkType.Tea));
            }
            for (int i = 0; i < coffees; i++) {
                waiting.add(new Drink(order.id, Drink.DrinkType.Coffee));
            }
            callOutStatus();
        }
    }
    public static synchronized void getOrderStatus(int ID){
        for (int i = 0; i < customers.length(); i++){
            if (customers.get(i) != null) {
                Customer customer = customers.get(i);
                if (customer.id == ID) {
                    if (customer.idle) {
                        talkToCustomer(customer.socket, "No orders for " + customer.name + ".");
                    } else {
                        // order status
                        String status = "Order Status for " + customer.name + ":\n";
                        String[] areas = trayContents(ID);
                        if (areas[0] != null && areas[0].length() > 0) {
                            status += " - " + areas[0] + " in waiting area.\n";
                        }

                        if (areas[1] != null && areas[1].length() > 0) {
                            status += " - " + areas[1] + " currently being prepared.\n";
                        }
                        if (areas[2] != null && areas[2].length() > 0) {
                            status += " - " + areas[2] + " currently in the tray.\n";
                        }
                        talkToCustomer(customer.socket, status);
                    }
                }
            }
        }
    }
    static synchronized void callOutStatus(){

        String status = customerCount + " customers in the cafe. " + orderCount + " waiting for orders.\n";

        String[] areas = trayContents(0);
        if (areas[0] != null && areas[0].length() > 0){
            status += areas[0];
        }else {
            status += "Nothing";
        }
        status += " in the waiting area. ";

        if (areas[1] != null && areas[1].length() > 0){
            status += areas[1];
        }else {
            status += "Nothing";
        }
        status += " in the brewing area. ";

        if (areas[2] != null && areas[2].length() > 0){
            status += areas[2];
        }else {
            status += "Nothing";
        }
        status += " in the tray area.\n";

        for (int i = 0; i < customers.length(); i++) {
            if (customers.get(i) != null) {
                Customer customer = customers.get(i);
                talkToCustomer(customer.socket, status);
            }
        }
    }
    private static void talkToCustomer(Socket socket, String message){
        try {
            if (!socket.isClosed()) {
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println(message);
            }
        }catch (Exception e){
            System.out.println(e);
        }
    }
    private static String formatOrder(int teas, int coffees){
        String line = "";
        if (teas > 0){if(teas == 1){line += "1 Tea";} else if(teas > 1){line += (teas + " Teas");}}
        if (teas > 0 && coffees > 0){line += " and ";}
        if (coffees > 0){if(coffees == 1){line += "1 Coffee";} else if(coffees > 1){line += (coffees + " Coffees");}}
        return line;
    }
    private static String[] trayContents(int ID){
        String[] status = new String[3];
        int waitingTeas = 0;
        int waitingCoffees = 0;
        for (Drink drink : waiting){
            if (drink != null){
                if( drink.id == ID || ID == 0) {
                    if (drink.type == Drink.DrinkType.Tea) {
                        waitingTeas++;
                    } else if (drink.type == Drink.DrinkType.Coffee) {
                        waitingCoffees++;
                    }
                }
            }
        }
        if(waitingTeas > 0 || waitingCoffees > 0){
            status[0] = formatOrder(waitingTeas, waitingCoffees);
        }
        int brewingTeas = 0;
        int brewingCoffees = 0;
        for (Thread key : brewing.keySet()){
            Drink drink = brewing.get(key);
            if (drink != null){
                if(drink.id == ID || ID == 0) {
                    if (drink.type == Drink.DrinkType.Tea) {
                        brewingTeas++;
                    } else if (drink.type == Drink.DrinkType.Coffee) {
                        brewingCoffees++;
                    }
                }
            }
        }
        if(brewingTeas > 0 || brewingCoffees > 0){
            status[1] = formatOrder(brewingTeas, brewingCoffees);
        }
        int trayTeas = 0;
        int trayCoffees = 0;
        for (Drink drink : tray){
            if (drink != null) {
                if (drink.id == ID || ID == 0) {
                    if (drink.type == Drink.DrinkType.Tea) {
                        trayTeas++;
                    } else if (drink.type == Drink.DrinkType.Coffee) {
                        trayCoffees++;
                    }
                }
            }
        }
        if(trayTeas > 0 || trayCoffees > 0){
            status[2] = formatOrder(trayTeas, trayCoffees);
        }
        return status;
    }
    private static void listenForCustomers() {
        try {
            ServerSocket server = new ServerSocket(port);
            System.out.println("Waiting for customers...");
            int id = 1;
            while (true) {
                Socket clientSocket = server.accept();
                for (int i = 0; i < customers.length(); i++){
                    if (customers.get(i) == null || customers.get(i).socket.isClosed()){
                        Customer customer = new Customer(clientSocket, id);
                        customers.set(i, customer);
                        customerCount.incrementAndGet();
                        Thread cThread = new Thread(customer);
                        cThread.start();
                        id++;

                        // new thread with instance of listen for requests
                        Thread listenR = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                listenForRequests(customer);
                            }
                        });
                        listenR.start();

                        callOutStatus();
                        break;
                    }
                    if (i == customers.length() -1){
                        AtomicReferenceArray<Customer> tempArray = new AtomicReferenceArray<>(customers.length()*2);
                        for (int s = 0; s < customers.length(); s++){
                            tempArray.set(s, customers.get(s));
                        }
                        customers = tempArray;
                    }
                    int socketsInUseNewVal = 0;
                    for (int s = 0; s < customers.length(); s++){
                        if (customers.get(s) != null && !customers.get(s).socket.isClosed()){
                            socketsInUseNewVal++;
                        }
                    }
                    customerCount.set(socketsInUseNewVal);

                    if (customerCount.intValue() < (customers.length() / 4)){
                        AtomicReferenceArray<Customer> tempArray = new AtomicReferenceArray<>(customers.length()/2);
                        int used = 0;
                        for (int s = 0; s < customers.length(); s++){
                            if (customers.get(s) != null && !customers.get(s).socket.isClosed()) {
                                tempArray.set(used, customers.get(s));
                                used++;
                            }
                        }
                        customers = tempArray;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static void listenForRequests(Customer customer){
        try (
                Scanner scanner = new Scanner(customer.socket.getInputStream());
                PrintWriter writer = new PrintWriter(customer.socket.getOutputStream(), true)) {
            try {
                while (customer.name.length() == 0){
                    writer.println("What is your name, please?");
                    customer.name = scanner.nextLine();
                }
                System.out.println("New connection; " + customer.name);
                writer.println("What can I get for you? (X Tea/X Coffee/X Tea and X Coffee)");

                while (true) {
                    String line = scanner.nextLine().toLowerCase();
                    if (line.length() == 0){
                        System.out.println("boop");
                    }
                    else if (line.equals("order status")) {
                        Barista.getOrderStatus(customer.id);
                    } else if (line.equals("exit")) {
                        break;
                    } else if (line.contains(" ") && !line.startsWith(" ") && !line.endsWith(" ") && !line.contains("  ")) {

                        String[] substrings = line.split(" ");

                        // remove s's
                        if (substrings[1].endsWith("s")) {
                            substrings[1] = substrings[1].substring(0, substrings[1].length() - 1);
                        }
                        if (substrings.length >= 4) {
                            if (substrings[substrings.length-1].endsWith("s")) {
                                substrings[substrings.length-1] = substrings[substrings.length-1].substring(0, substrings[substrings.length-1].length() - 1);
                            }
                        }
                        boolean intAt0 = true;
                        int quantity = 0;
                        try{
                            quantity = Integer.parseInt(substrings[0]);
                        }catch (Exception e){
                            intAt0 = false;
                        }

                        if ((substrings.length >= 2 && intAt0)&& substrings.length <= 5 ) {
                            boolean understood = true;
                            int quantiTea = 0;
                            int quanCoffee = 0;
                            switch (substrings[1].toLowerCase()) {
                                case "tea":
                                    System.out.print(quantity + " Tea");
                                    quantiTea += quantity;
                                    if (quantity > 1) {
                                        System.out.print("s");
                                    }
                                    break;

                                case "coffee":
                                    System.out.print(quantity + " Coffee");
                                    quanCoffee += quantity;
                                    if (quantity > 1) {
                                        System.out.print("s");
                                    }
                                    break;
                                default:
                                    writer.println("Sorry, I'm not sure I understand... ");
                                    understood = false;
                            }
                            if (substrings.length == 5 || substrings.length == 4) { //5 if "x drink and x drink" format, 4 if just "x drink x drink" format
                                try {
                                    int otherQuantity = Integer.parseInt(substrings[substrings.length - 2]);
                                    System.out.print(" and ");
                                    switch (substrings[substrings.length - 1].toLowerCase()) {
                                        case "tea":
                                            System.out.print(otherQuantity + " Tea");
                                            quantiTea += otherQuantity;
                                            if (otherQuantity > 1) {
                                                System.out.print("s");
                                            }
                                            understood = true;
                                            break;
                                        case "coffee":
                                            System.out.print(otherQuantity + " Coffee");
                                            quanCoffee += otherQuantity;
                                            if (otherQuantity > 1) {
                                                System.out.print("s");
                                            }
                                            break;
                                        default:
                                            writer.println("Sorry, I'm not sure I understand... ");
                                            understood = false;
                                    }
                                }catch (Exception e){
                                    System.out.println(e);
                                }
                            }
                            System.out.println("");
                            if (understood) {
                                Barista.addOrder(customer, quantiTea, quanCoffee);
                                customer.idle = false;
                            }
                        } else {
                            writer.println("Sorry, I'm not sure I understand... ");
                        }
                        System.out.println("");
                    }
                }
            } catch (Exception e) {
                writer.println("Sorry, I'm not sure I understand... ");
            }
        } catch (Exception e) {
            System.out.println(e);
        } finally {
            try{
                customer.socket.close();
            }catch(Exception e){
                System.out.println("error: " + e);
            }
            System.out.println("Customer " + customer.name + " disconnected.");
            callOutStatus();
        }
    }
}