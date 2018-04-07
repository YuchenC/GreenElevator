package elevator.controller;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import elevator.rmi.*;
import java.util.NoSuchElementException;
/**
 * Title:        Green Elevator
 * Description:  Green Elevator, 2G1915
 * Copyright:    Copyright (c) 2001
 * Company:      IMIT/KTH
 * @author Vlad Vlassov
 * @version 1.0
 */

public class Controller extends Thread implements ActionListener {
    public static Controller[] threads;     //Array to store all the threads so that thread thats controlls outside buttons can enqueue floor to elevators 
    Motor motor;
    Door door;
    Scale scale;
    Elevators elevators;
    String rmihost;
    double location;                // The current location of the elevator
    boolean needStop = false;       // Indicator of emergency break (stop) has been activated

    Queue myqueue;                  // Queue with stops for elevator

    public Queue getQueue() {       // Get the queue (used by thread that contolls the outside buttons)
	   return myqueue;
    }

    public double Location() {      // Get the location (used by thread that contolls the outside buttons)
        return location;
    }


    /**
    * Creates an instace of <code>Controller</code> to run each elevator in a separate thread
    */
    public Controller(String[] args) {
        rmihost = (args.length > 0)? args[0] : "localhost";
    }

    /**
    * Run method for each elevator
    */
    public void run() {


        System.out.println("I am the controller, thread " + getName().charAt(7));

        try {
            MakeAll.init(rmihost);
            int temp = Character.getNumericValue(getName().charAt(7));
            
            //All threads except one will act like an elevator
            if(Character.getNumericValue(getName().charAt(7)) != threads.length) {       
            
                MakeAll.addInsideListener(temp, this);
                MakeAll.addPositionListener(temp, this);
                Elevator myelevator = MakeAll.getElevator(Character.getNumericValue(getName().charAt(7)));
                myqueue = new Queue();

                while(true) {
                    sleep(1000);

                    //If there are missions inte the elevators queue do them
                    while (!(myqueue.isEmpty())) {              
                        
                        //The next destination is the fisrt floor in the queue
                        int destination = myqueue.first.floor;
                        //System.out.println("elevator " + getName() + ", des = " + destination);

                        //Check where the elevator are now and decide to go up or down
                        double where = myelevator.whereIs();
                        if (where <= destination) {
                            myelevator.up();

                            do {
                                
                                //If emergency break have been activated the elevator stops
                                while(needStop)
                                    myelevator.stop();
                                
                                
                                myelevator.setScalePosition((int)myelevator.whereIs());
                                
                                //Update current location
                                location = myelevator.whereIs();
                                
                                //Checking if new floor has been added to in the head of the queue
                                destination = myqueue.first.floor;
                            
                            }
    	                    while ((where = myelevator.whereIs()) < destination - 0.001);

    	                }
    	                else {
                            myelevator.down();

                            do {
                                //If emergency break have been activated the elevator stops
                                while(needStop)
                                    myelevator.stop();
                                
                                myelevator.setScalePosition((int)myelevator.whereIs());
                                
                                //Update current location
                                location = myelevator.whereIs();
                                
                                //Checking if new floor has been added to in the head of the queue
                                destination = myqueue.first.floor;

                            }
    	                    while ( (where = myelevator.whereIs()) > destination + 0.001);

    	               }
                       //When destination reached remove floor from queue    
                       myqueue.dequeue();
                       
                       //myqueue.print();
                    
                       //Stop the elevator, open and close the doors and get next destiantion if any in next iteration    
                       myelevator.stop();
    	               myelevator.open();
    	               sleep(2000);
    	               myelevator.close();
                    }
                }

            }
            
            //One threrad will handle outside buttons
            else {           
                MakeAll.addFloorListener(this);
            }
        }
        
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }


    /**
    * The entry point of the <code>Controller</code> application. Create
    *  <code>Controller</code> threads based on the number of elevators and start them and one 
    *  extra <code>Controller</code> thread to listen at outside buttons.
    */
    public static void main(String[] args) {

        int numberOfElevators = 0;

        try {
            numberOfElevators = MakeAll.getNumberOfElevators();
        }
        catch(Exception e)
        {
            System.out.println("exception catched");
            e.printStackTrace();
            System.exit(1);
        }

        Controller[] t = new Controller[numberOfElevators+1];    //Array for all elevator threads


        for (int i = 1; i <= (numberOfElevators+1); i++) {
            Controller con = new Controller(args);
            t[i-1] = con;

            //System.out.println("Thread " + i + " is going to start.");
            con.start();
        }

        threads = t;   //Make the gobal array refernce (threads[]) in Controller to refernce this t array so they are reachable for outside main.  
    }
    /**
    * Invoked when any button on the Elevators is pressed. Prints an action command
    * assigned to the button.
    */
    public void actionPerformed(ActionEvent e) {
        
        String s = e.getActionCommand();
    
        //Parse butten inside is activated
        if (s.charAt(0) == 'p') {
            
            //For elevator stop event
            if(s.length() > 5 && Character.getNumericValue(s.charAt(4)) == 3 && Character.getNumericValue(s.charAt(5)) == 2)
                needStop = true;
            
            else  
                //Parse and put requested floor in elevators queue
                getQueue().enqueue(Character.getNumericValue(s.charAt(4)), location);
            
        }

        //Parse butten outeside is activated
        if (s.charAt(0) == 'b') {
            
            //Parse the floor
            int buttonfloor = Character.getNumericValue(s.charAt(2));
            
            //Parse wished direction
            int buttondir =  Character.getNumericValue(s.charAt(4));
            
            //Create an array for costs
            double[] costlist = new double[threads.length - 1];

            //Get cost for each elevator to pick up at specified floor
            for(int j = 0; j < (threads.length-1); j++) {
               
                    costlist[j] = threads[j].getQueue().cost(threads[j].getQueue(), threads[j].Location(), buttonfloor, buttondir);
                    //System.out.println("elevator " + j + " cost: " + costlist[j]);  
            }
            
            //Compare the costs and enqued the floor to elevator with the least cost
            int i=0;
            double minCost=costlist[0];
            for(int k=1; k<threads.length-1; k++) {
                if(minCost > costlist[k]) {
                    minCost = costlist[k];
                    i = k;
                }
            }
            threads[i].getQueue().enqueue(buttonfloor, threads[i].Location());
            //System.out.println("Elevator " + i + "Gets floor: " + buttonfloor);
            
        }
    }

    //Monitor class with synchronized enqueue and dequeue methods
    public static class Queue {
        private Node first;                                 // beginning of queue
        private Node last;                                  // end of queue
        private static boolean isReversePoint = false;      // true if the queue has a turning point otherwise false


        // helper linked list class
        private static class Node {
            private int floor;
            private Node next;
        }

        /**
        * Initializes an empty queue.
        */
        public Queue() {
            first = null;
            last  = null;
        }

        //Checks if the queue is empty
        public boolean isEmpty() {
            return first == null;
        }

        //Returns the length of the queue
        public static int length(Node len)
        {
            if(len == null)
                return 0;
        	int length = 0;
        	Node x = len;
        	while (x != null)
        	{
        		length++;
        		x = x.next;
        	}
        	return length;
        }
        
        //Dequeue to dequeue a floor
        public synchronized int dequeue() {
            
            if(isEmpty()) throw new NoSuchElementException("Queue underflow");
            int floor = first.floor;
            first = first.next;

            if (isEmpty()) last = null;   // to avoid loitering
            return floor;
        }
        
        //Enqueue method enqueues a floor to an elevator. 
        public synchronized void enqueue(int floor, double location) {
        
            if(floorInQueue(floor) == false) {
            
                Node oldlast = last;
                last = new Node();
                last.floor = floor;
                last.next = null;

                if (isEmpty())
                    first = last;
                else
                    oldlast.next = last;

                int dir;
                int destination = first.floor;

                if(location < destination)
                    dir = 1;
                else
                    dir = 0;

                reorder(location, dir);
            }

        }
    
        //Method reorder to order the queue after an insertion 
        public void reorder (double l, int dir) {
            Node less = new Node();
            int lengthLess = 0;
            Node lessFirst = less;
            Node lessLast = null;
            Node higher = new Node();
            int lengthHigher = 0;
            Node higherFirst = higher;
            Node higherLast = null;

            Node n = first;
            while(n != null) {
                if(n.floor < l) {
                    if(lengthLess==0) {
                        less.floor = n.floor;
                        lessLast= less;
                        lengthLess++;
                        n = n.next;
                    }
                    else {
                        less.next = new Node();
                        less = less.next;
                        less.floor = n.floor;
                        lessLast = less;
                        lengthLess++;
                        n = n.next;
                    }
                }
                else {
                    if(lengthHigher ==0) {
                        higher.floor = n.floor;
                        lengthHigher++;
                        higherLast = higher;
                        n = n.next;
                    }   
                    else {
                        higher.next = new Node();
                        higher = higher.next;
                        higher.floor = n.floor;
                        lengthHigher++;
                        higherLast = higher;
                        n = n.next;
                    }
                }
            }
        
            BubbleSortRev(lessFirst, lengthLess);
            BubbleSort(higherFirst, lengthHigher);

            if(dir == 0) {
                if(lengthLess == 0) {
                    first = higherFirst;
                    last = higherLast;
                }
                else if(lengthHigher == 0) {
                    first = lessFirst;
                    last = lessLast;
                }
                else {
                    first = lessFirst;
                    lessLast.next = higherFirst;
                    last = higherLast;
                }
            }
            else {
                if(lengthLess == 0) {
                    first = higherFirst;
                    last = higherLast;
                }
                else if(lengthHigher == 0) {
                    first = lessFirst;
                    last = lessLast;
                }
                else {
                    first = higherFirst;
                    higherLast.next = lessFirst;
                    last = lessLast;
                }
            }
        }
        
        //Sorting method, an helper to reorder
        public static void BubbleSort(Node a, int n) {

            int R = n-2;
            boolean swapped = true;

            while(R >=0 && swapped == true){
                swapped = false;
                Node x = a;

                for(int i=0; i<=R; i++){
                    if (x.floor > x.next.floor) {
                        swapped = true;
                        swap(x);
                    }
                    x = x.next;
                }
                R--;
            }
        }
        
        //Helper method to bubblesort
        public static void swap(Node a){
            int temp = a.floor;
            a.floor = a.next.floor;
            a.next.floor = temp;
        }
        
        //Sorting method, an helper to reorder
        public static void BubbleSortRev(Node a, int n) {
            int R = n-2;
            boolean swapped = true;

            while(R >=0 && swapped == true){
                swapped = false;
                Node x = a;

                for(int i=0; i<=R; i++){
                    if (x.floor < x.next.floor) {
                        swapped = true;
                        swap(x);
                    }
                    x = x.next;
                }
                R--;
            }
        }

        //Print method to print a queue
        public void print() {
            Node p = first;
            boolean nextp = false;
            if (first != null)
                nextp = true;

            System.out.println("queue");
            while(nextp) {
                System.out.print(p.floor + " ");
                if (p.next == null)
                    nextp = false;
                p = p.next;
            }
        }
        
        //Checks if the floor is allready in the queue, returns true if it is otherwise false
        public boolean floorInQueue(int newFloor) {
        
            Node n = first;
            while(n != null) {
                if(newFloor == n.floor) {
                    return true;
                }
                n = n.next;
            }
            return false;
        }
        
        //Cost method calculates and returns the cost for the elevator to reach the buttonfloor
        public double cost(Queue currentQueue, double location, int buttonfloor, int buttondir)
        {
            
        	double cost = 0;
        	Node costfirst = currentQueue.first;
           
        	Node costlast = currentQueue.last;
            Node pointReverse = currentQueue.reversePoint(costfirst);
            
        	if (currentQueue.length(costfirst) == 0) {
                return cost = Math.abs(location - buttonfloor);
            }
                
            int eldir = 0;
            if (location < first.floor)
            	eldir = 1;
            else
            	eldir = -1;

        	// no pointReverse
        	if (!currentQueue.isReversePoint)
        	{
        		// same direction
        		if (buttondir == eldir)
        		{
        			// have passed
        			if ((location > buttonfloor && buttondir == 1) || (location < buttonfloor && buttondir == -1))
        			{
        				cost = length(costfirst);
        				cost += Math.abs(costlast.floor - location) + Math.abs(costlast.floor - buttonfloor);
        			}
        			// have not passed
        			else
        			{
        				Node x = costfirst;
        				// up
        				if (buttondir == 1)
        				{
        					while ( x != null && x.floor < buttonfloor )
        					{
        						cost++;
        						x = x.next;
        					}
        				}
        				// down
        				else
        				{
        					while ( x != null && x.floor > buttonfloor )
        					{
        						cost++;
        						x = x.next;
        					}
        				}
        				cost += Math.abs(buttonfloor - location);
        			}
        		}
        		// different directions
        		else
                    
        		{
        			// have passed
        			if ((location < buttonfloor && buttondir == 1) || (location > buttonfloor && buttondir == -1))
        			{
        				cost = length(costfirst);
        				cost += Math.abs(location - costlast.floor) + Math.abs(buttonfloor - costlast.floor);
                        
        			}
        			// have not passed
        			else
        			{
        				// will pass
        				if ((costlast.floor < buttonfloor && eldir == -1) || (costlast.floor > buttonfloor && eldir == 1))
        				{
        					cost = length(costfirst);
        					cost += Math.abs(location - costlast.floor) + Math.abs(costlast.floor - buttonfloor);
        				}
        				// will not pass
        				else
        				{
        					cost = length(costfirst);
        					cost += Math.abs(location - costlast.floor);
        				}
        			}
        		}
        	}
        	// pointReverse exists
        	else
        	{
        		// same direction
        		if (eldir == buttondir)
        		{
        			// have passed
        			if ((location > buttonfloor && buttondir == 1) || (location < buttonfloor && buttondir == -1))
        			{
        				cost = length(costfirst);
        				cost += Math.abs(pointReverse.floor - location) +
        						Math.abs(pointReverse.floor - costlast.floor) +
        						Math.abs(costlast.floor - buttonfloor);
                                
        			}
        			// have not passed
        			else
        			{
        				Node x = costfirst;
        				// up
        				if (buttondir == 1)
        				{
        					while (x.floor < buttonfloor)
        					{
        						cost++;
        						x = x.next;
        					}
        				}
        				// down
        				else
        				{
        					while (x.floor > buttonfloor)
        					{
        						cost++;
        						x = x.next;
        					}
        				}
        				cost += Math.abs(buttonfloor - location);
        			}
        		}
        		// different directions has passed or has not passed
        		else
        		{

        			Node x = pointReverse;
                    cost = length(costfirst) - length(pointReverse);
                    // up
                    if (buttondir == 1) {
                        while (x != null && buttonfloor > x.floor)
                        {
                            cost++;
            				x = x.next;
                        }
                    }
                    // down
                    else
                    {
        				while (x != null && buttonfloor < x.floor)
                        {
                            cost++;
            				x = x.next;
                        }
                    }
                    cost += Math.abs(location - pointReverse.floor) + Math.abs(buttonfloor - pointReverse.floor);		
        		}
        	}
        	return cost;
        }
        
        //Check if the queue has a turning point, if so return the turning point otherwise return the beginning
        public static Node reversePoint(Node rpFirst)
        {
        	Node x = rpFirst;
        	int direction = 0;
            
            if(rpFirst == null)
                return rpFirst;
            
        	if (rpFirst.next == null)
        	{        		
        		return rpFirst;
        	}

        	if ((rpFirst.floor - rpFirst.next.floor) < 0)
        		direction = 1 ;
        	else
        		direction = 0;

        	if (direction == 1)
        	{
        		while (x != null && x.next != null)
        		{
        			if (x.next.floor < x.floor)
            	    {
        				
        				isReversePoint = true;
            		    return x;
            		}
        			else
        				x = x.next;
        		}
        	}
        	else
        	{
        		while (x != null && x.next != null)
        		{
        			if (x.next.floor > x.floor)
        			{
        				isReversePoint = true;
        				return x;
        			}
        			else
        				x = x.next;
        		}
        	}
        	return rpFirst;
        }

    }
}


