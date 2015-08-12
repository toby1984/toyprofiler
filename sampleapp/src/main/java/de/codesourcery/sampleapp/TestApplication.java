package de.codesourcery.sampleapp;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

import de.codesourcery.toyprofiler.Methods;
import de.codesourcery.toyprofiler.Profile;

public class TestApplication implements Methods 
{
    private final Random rnd = new Random();
    
    public static void main(String[] args) throws InterruptedException 
    {
        final CountDownLatch latch = new CountDownLatch(5);
        for ( int i = 0 ; i < 5 ; i++ ) 
        {
            final Thread t = new Thread(() -> 
            {
                try {
                    new TestApplication().run();
                } finally {
                    latch.countDown();
                }
            });
            t.setName("thread-"+i);
            t.start();
        }
        latch.await();
        System.out.println( Profile.printAllThreads() );
    }
    
    public void run() 
    {
        try 
        {
            Profile.methodEntered( "run" );
            for ( int i = 0 ; i < 10 ; i++ ) 
            {
                method1();
            }
        } finally {
            Profile.methodLeft();
        }
//        System.out.println( Profile.printThisThread() );
    }
    
    private void method1() 
    {
        Profile.methodEntered( "method1" );
        try 
        {
            sleep( 20 );
            if ( method2() )
            {
                method3();
            } else {
                method4();
            }
        } finally {
            Profile.methodLeft();
        }
    }
    
    private void sleep(int millis) 
    {
        try {
            Thread.sleep(millis);
        } 
        catch(InterruptedException e) 
        {
            Thread.currentThread().interrupt();
        }
    }
    
    private boolean method2() 
    {
        Profile.methodEntered( "method2" );
        try 
        {
        return rnd.nextBoolean();
        } finally {
            Profile.methodLeft();
        }
    }
    
    private void method3() 
    {
        Profile.methodEntered( "method3" );
        try 
        {
            sleep(20);
        } finally {
            Profile.methodLeft();
        }
    }
    
    private void method4() 
    {
        Profile.methodEntered( "method4" );
        try 
        {
            sleep(40);
        } finally {
            Profile.methodLeft();
        }
    }
}
