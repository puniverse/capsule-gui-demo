package foo;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.FiberExecutorScheduler;
import co.paralleluniverse.fibers.FiberScheduler;
import co.paralleluniverse.strands.Strand;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import co.paralleluniverse.strands.channels.ReceivePort;
import co.paralleluniverse.strands.dataflow.Val;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) throws Exception {
        final JFrame frame = new JFrame("Reactive Window");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        final JPanel panel = new JPanel();
        frame.getContentPane().add(panel);
        final JLabel label1 = new JLabel();
        final JLabel label2 = new JLabel();
        label1.setFont(label2.getFont().deriveFont(100.0f));
        label2.setFont(label2.getFont().deriveFont(100.0f));

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                frame.setMinimumSize(new Dimension(600, 300));
                panel.add(label1);
                panel.add(label2);
                frame.pack();
                frame.setVisible(true);
            }
        });

        Channel<Integer> t = Channels.newChannel(1, Channels.OverflowPolicy.DISPLACE);
        Val<String> x = new Val<>();

        // publish lots of numbers
        new Fiber(() -> {
            for (int i = 0; i < 100000; i++) {
                Strand.sleep(10);
                t.send(i);
            }
        }).start();

        // x will only be available in 5 seconds
        new Fiber(() -> {
            Strand.sleep(5, TimeUnit.SECONDS);
            x.set("!!!");
        }).start();

        // create a fiber scheduler that runs fibers on the UI thread
        FiberScheduler UIScheduler = new FiberExecutorScheduler("UI-fiber-scheduler", new Executor() {
            @Override
            public void execute(Runnable command) {
                EventQueue.invokeLater(command);
            }
        });

        // set text for the first label
        new Fiber(UIScheduler, () -> {
            ReceivePort<Integer> c = Channels.newTickerConsumerFor(t);
            Integer num;
            while ((num = c.receive()) != null) {
                assert EventQueue.isDispatchThread(); // see, we're on the UI thread!

                label1.setText("foo: " + num);
                Strand.sleep(100); // ... yet we can sleep
            }
        }).start();

        new Fiber(UIScheduler, () -> {
            ReceivePort<Integer> c = Channels.newTickerConsumerFor(t);
            Integer num;
            while ((num = c.receive()) != null) {
                assert EventQueue.isDispatchThread(); // see, we're on the UI thread!

                label2.setText("bar: " + num + x.get()); // yet, we block until x is available
                Strand.sleep(500); // we'll update the seond label more slowly
            }
        }).start();
    }
}
