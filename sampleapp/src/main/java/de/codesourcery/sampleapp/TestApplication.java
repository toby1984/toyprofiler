package de.codesourcery.sampleapp;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

import de.codesourcery.toyprofiler.Methods;

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
        System.out.println("main() returned");
    }

    public void run()
    {
        for ( int i = 0 ; i < 10 ; i++ )
        {
            method1();
        }
    }

    private void method1()
    {
        sleep( 20 );
        if ( method2() )
        {
            method3();
        } else {
            method4();
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
        return rnd.nextBoolean();
    }

    private void method3()
    {
    	sleep(20);
    	if ( rnd.nextBoolean() ) {
    		method4();
    	}
    }

    private void method4()
    {
       sleep(40);
    }
}
