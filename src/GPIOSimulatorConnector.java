import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GPIOSimulatorConnector{

    private enum CommandList
    {
        SEND_PIN_UPDATE (0x35),
        REQ_SYNC(0x10);

        private int cmd_num;

        CommandList(int cmd_number)
        {
            this.cmd_num = cmd_number;
        }

        public int getCommandNumber() {
            return cmd_num;
        }
    }

    private Socket clientSocket;

    public GPIOSimulatorConnector() throws IOException
    {
        clientSocket = new Socket();
        try {
            clientSocket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), 9596));
        }catch (IOException ioE)
        {
            System.out.println("Failed to connect to simulator host. Details: " + ioE);
            throw ioE;
        }
    }

    public Map<Integer, Boolean> requestPinSync() throws IOException
    {
        if(clientSocket.isClosed()) {
            System.out.println("Socket is closed");
            return Collections.emptyMap();
        }

        try
        {
            // Send pin sync request to sim host
            OutputStream oS = clientSocket.getOutputStream();
            oS.write(CommandList.REQ_SYNC.getCommandNumber());
            oS.flush();

            InputStream iS = clientSocket.getInputStream();
            Map<Integer, Boolean> pinStates = new HashMap<>();


            // Wait for input
            while(iS.available() == 0);

            while(iS.available() > 0) {
                int cur = iS.read();


                if(cur == CommandList.SEND_PIN_UPDATE.getCommandNumber() && iS.available() >= 2)
                {
                    int pin = iS.read();
                    int state = iS.read();

                    int pinIndex = pin - 1;

                    if(pinIndex < 0)
                    {
                        continue;
                    }

                    pinStates.put(pinIndex, state == 1);
                }
            }

            return pinStates;

        }catch (IOException ioE)
        {
            System.out.println(ioE);
            throw ioE;
        }
    }

    public void sendPinStateChange(int pin, boolean state) throws IOException
    {
        if(clientSocket.isClosed()) {
            System.out.println("Socket is closed");
            return;
        }

        try
        {

            // Send pin update to sim host
            OutputStream oS = clientSocket.getOutputStream();
            oS.write(CommandList.SEND_PIN_UPDATE.getCommandNumber());
            oS.write(pin);
            oS.write(state ? 1 : 0);
            oS.flush();



        }catch (IOException ioE)
        {
            System.out.println(ioE);
            throw ioE;
        }
    }





    public static void main(String[] args) {



        PinModel o = new PinModel();

        JFrame f = new JFrame("GPIO Simulator");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setLocationByPlatform(true);


        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("File");
        menuBar.add(menu);
        JMenuItem connectItem = new JMenuItem("Connect");
        connectItem.addActionListener((e) -> {

            try {
                GPIOSimulatorConnector conn = new GPIOSimulatorConnector();
                o.setConnector(conn);
                connectItem.setEnabled(false);
                o.syncPins();
            }catch (IOException ioE)
            {
                JOptionPane.showMessageDialog(f, "Failed to connect to host");
            }

        });
        menu.add(connectItem);


        f.setJMenuBar(menuBar);

        f.setContentPane(o.getUI());
        f.pack();
        f.setMinimumSize(f.getSize());

        f.setVisible(true);
    }
}

class PinModel {
    private JComponent mainFrame = null;
    private JCheckBox[] pinCheckBoxes = new JCheckBox[26];
    private GPIOSimulatorConnector connector;

    PinModel() {
        if (mainFrame != null)
            return;

        // Use JPanel -> grid layout for pins
        mainFrame = new JPanel(new GridLayout(0, 13, 2, 20));

        // Set surrounding border
        mainFrame.setBorder(new EmptyBorder(4, 4, 4, 4));

        // Set pin name, add pin to UI
        for (int i = 0; i < pinCheckBoxes.length; i++) {
            pinCheckBoxes[i] = new JCheckBox("Pin " + (i + 1), false);

            JCheckBox cur = pinCheckBoxes[i];

            cur.setEnabled(false);
            cur.setName(String.valueOf(i + 1));
            cur.addActionListener((e) ->{

                // Checks for null source and invalid casts
                if (!(e.getSource() instanceof JCheckBox))
                    return;

                JCheckBox sourceCheckbox = (JCheckBox)e.getSource();
                System.out.println("Action: " + sourceCheckbox.getActionCommand());
                try {
                    int checkIndex = Integer.parseInt(sourceCheckbox.getName());
                    System.out.println("Pin: " + checkIndex + " has been changed!");

                    if(connector != null)
                        connector.sendPinStateChange(checkIndex, sourceCheckbox.getModel().isSelected());

                }catch (NumberFormatException nFE)
                {
                    System.out.println("Failed to parse checkbox index");
                }
                catch (IOException ioE)
                {
                    // Handle ioe using UI
                }


            });

            mainFrame.add(pinCheckBoxes[i]);
        }
    }

    public void setConnector(GPIOSimulatorConnector connector)
    {
        this.connector = connector;
    }

    public void syncPins()
    {
        if(connector == null)
            return;

        try {

            Map<Integer, Boolean> pinMap = this.connector.requestPinSync();
            System.out.println("Pin Sync: " + pinMap.size());

            for (Integer pin : pinMap.keySet()) {
                JCheckBox jCheckBox = this.pinCheckBoxes[pin];
                jCheckBox.setEnabled(true);
                jCheckBox.getModel().setSelected(pinMap.get(pin));
            }
        }catch (IOException ioE)
        {
            JOptionPane.showMessageDialog(mainFrame, "Failed to request pin sync. Exception: " + ioE.getMessage());
            ioE.printStackTrace();
        }
    }

    public JComponent getUI() {
        return mainFrame;
    }


}