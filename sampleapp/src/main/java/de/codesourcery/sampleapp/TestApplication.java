package de.codesourcery.sampleapp;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class TestApplication
{
    private final Random rnd = new Random();

    private final Object LOCK = new Object();

    protected static final int SCALING_FACTOR = 100;

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
    }

    public void run()
    {
        for ( int i = 0 ; i < 100 ; i++ )
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
        try
        {
            Thread.sleep(millis*SCALING_FACTOR);
        }
        catch(InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    private boolean method2()
    {
        do {
            synchronized( LOCK )
            {
                return rnd.nextBoolean();
            }
        } while ( false );
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
       sleep(10);
    }
}
