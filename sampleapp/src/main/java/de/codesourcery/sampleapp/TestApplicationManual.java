package de.codesourcery.sampleapp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import de.codesourcery.toyprofiler.Methods;
import de.codesourcery.toyprofiler.Profile;

public class TestApplicationManual implements Methods
{
    private final Random rnd = new Random();

    public static void main(String[] args) throws InterruptedException, IOException
    {
    	Profile.registerMethod("run",0);
    	Profile.registerMethod("method1",1);
    	Profile.registerMethod("method2",2);
    	Profile.registerMethod("method3",3);
    	Profile.registerMethod("method4",4);

        final CountDownLatch latch = new CountDownLatch(5);
        for ( int i = 0 ; i < 5 ; i++ )
        {
            final Thread t = new Thread(() ->
            {
                try {
                    new TestApplicationManual().run();
                } finally {
                    latch.countDown();
                }
            });
            t.setName("thread-"+i);
            t.start();
        }
        latch.await();

        System.out.println( Profile.printAllThreads() );

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        Profile.save( out );
        System.out.println( out.toString() );

        System.out.println("==== FROM FILE ==== ");
        final ByteArrayInputStream in = new ByteArrayInputStream( out.toByteArray() );
        Profile.load( in ).stream().forEach( System.out::println);
    }

    public void run()
    {
        try
        {
            Profile.methodEntered( 0 );
            for ( int i = 0 ; i < 10 ; i++ )
            {
                method1();
            }
        } finally {
            Profile.methodLeft();
        }
    }

    private void method1()
    {
        Profile.methodEntered( 1 );
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
        Profile.methodEntered( 2 );
        try
        {
        return rnd.nextBoolean();
        } finally {
            Profile.methodLeft();
        }
    }

    private void method3()
    {
        Profile.methodEntered( 3 );
        try
        {
            sleep(20);

        	if ( rnd.nextBoolean() ) {
        		method4();
        	}

        } finally {
            Profile.methodLeft();
        }
    }

    private void method4()
    {
        Profile.methodEntered( 4 );
        try
        {
            sleep(40);
        } finally {
            Profile.methodLeft();
        }
    }
}
