package de.codesourcery.sampleapp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import de.codesourcery.toyprofiler.MethodStatsHelper;
import de.codesourcery.toyprofiler.Profile;
import de.codesourcery.toyprofiler.Profile.MethodIdentifier;
import de.codesourcery.toyprofiler.ProfileContainer;
import de.codesourcery.toyprofiler.util.XMLSerializer;

public class TestApplicationManual
{
    private final Random rnd = new Random();

    public static void main(String[] args) throws InterruptedException, IOException
    {
    	Profile.registerMethod(new MethodIdentifier(0,"de/codesourcery/sampleapp/TestApplicationManual","run","()") );
    	Profile.registerMethod(new MethodIdentifier(1,"de/codesourcery/sampleapp/TestApplicationManual","method1","()") );
    	Profile.registerMethod(new MethodIdentifier(2,"de/codesourcery/sampleapp/TestApplicationManual","method2","()") );
    	Profile.registerMethod(new MethodIdentifier(3,"de/codesourcery/sampleapp/TestApplicationManual","method3","()") );
    	Profile.registerMethod(new MethodIdentifier(4,"de/codesourcery/sampleapp/TestApplicationManual","method4","()") );

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

        System.out.println( Profile.printAll() );

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        Profile.save( out );
        System.out.println( out.toString() );

        System.out.println("==== FROM FILE ==== ");
        final ByteArrayInputStream in = new ByteArrayInputStream( out.toByteArray() );
        final ProfileContainer container = new XMLSerializer().load( in );
        final MethodStatsHelper helper = new MethodStatsHelper(container);
        container.getProfiles().stream().forEach( p -> System.out.println( helper.print( p ) ) );
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
