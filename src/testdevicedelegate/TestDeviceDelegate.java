package testdevicedelegate;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

/**
 *
 * @author Tony Mao <tmao@nvidia.com>
 */
public class TestDeviceDelegate {

    private String projectName = null;
    private String rootDir = "TestDeviceProjects";
    private ZooKeeper zk = null;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
//
        String configFilePath = null;
        for (int i = 0; i < args.length; i++) {
            if ("-config".equals(args[i])) {
                configFilePath = args[i + 1];
            }
        }
        if (null != configFilePath) {
            File f = new File(configFilePath);
            if (f.isFile()) {
                try {
                    String hostname = InetAddress.getLocalHost().getHostName();
                    TestDeviceDelegate tdd = new TestDeviceDelegate(configFilePath);
                    if (null != tdd.getZK() && null != tdd.getProjectName()) {
                        while (true) {
                            String deviceId = tdd.GetDeviceNumber();
                            if (null == deviceId) {
                                tdd.DeleteZnode(hostname);
                            } else {
                                tdd.CreateZnode(hostname, deviceId);
                            }
                            Thread.sleep(60 * 1000);
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.out.println("-- Lost ZooKeeper connection.");
            } else {
                System.out.println("-- Can not find the config file.");
            }
        } else {
            System.out.println("-- Can not find the config file.");
        }

        System.exit(0);
    }

    public TestDeviceDelegate(String configFilePath) {
        if (this.zk == null) {
            try {
                this.zk = this.ConnectZK(configFilePath);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private ZooKeeper ConnectZK(String configFilePath) throws Exception {
        SAXReader reader = new SAXReader();
        File file = new File(configFilePath);
        Document document = reader.read(file);
        Element root = document.getRootElement();
        List<Element> addresses = root.element("zookeepers").elements("zookeeper");
        List selectItems = new ArrayList();
        for (Element address : addresses) {
            selectItems.add(address.getTextTrim());
        };
        String serverAddress = StringUtils.join(selectItems.toArray(), ',');

        ZooKeeper zk = new ZooKeeper(serverAddress, 5000, new Watcher() {
            public void process(WatchedEvent event) {
                // Do nothing.
            }
        });
        System.out.println(serverAddress);
        this.projectName = root.elementText("ProjectName");

        return zk;
    }

    private ZooKeeper getZK() {
        return this.zk;
    }

    private String GetDeviceNumber() {
        String deviceNumber = null;
        try {
            Runtime r = Runtime.getRuntime();
            Process p = r.exec("adb devices");
            BufferedReader b = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = null;
            while (null != (line = b.readLine())) {
                if (Pattern.matches(".*device$", line)) {
                    deviceNumber = line.substring(0, line.lastIndexOf("device")).trim();
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return deviceNumber;
    }

    private void DeleteZnode(String hostname) {
        String znode = "/" + this.rootDir + "/" + this.projectName + "/" + hostname + "/device";
        try {
            if (null != zk.exists(znode, false)) {
                zk.delete(znode, -1);
            }
            System.out.println("-- Delete: " + znode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void CreateZnode(String hostname, String deviceId) {
        String znode = "/" + rootDir + "/" + projectName + "/" + hostname;
        try {
            if (null == zk.exists(znode, false)) {
                znode = zk.create(znode, deviceId.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
            if (null == zk.exists(znode + "/device", false)) {
                znode = zk.create(znode + "/device", deviceId.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
            System.out.println("Create: " + znode);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private String getProjectName() {
        return this.projectName;
    }

}
