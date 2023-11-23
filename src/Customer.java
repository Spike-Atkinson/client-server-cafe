import Helpers.Order;

import java.io.PrintWriter;
import java.net.Socket;

import java.util.Scanner;

public class Customer implements Runnable {

    int id;
    boolean idle;
    public Socket socket;
    public String name = "";
    public static void main(String[] args) {

    }
    public Customer(Socket socket, int id){
        this.socket = socket;
        this.id = id;
        this.idle = true;



    }
    @Override
    public void run() {
        while (!socket.isClosed()){

        }
        System.out.println(name + ": \"Bye!\"");
    }

}