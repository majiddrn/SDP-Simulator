import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class DomainParticipant extends Entity{
    private final ArrayList<DomainParticipant> dpDiscovered;
    private final int domainParticipantId;
    private final int periodTime = 5000;                                  // Every 5000ms = 5 seconds send packets
    private final int packetTimes = 10;                                   //  Number of times that packets are sent
    private final ArrayList<String> networks;
    private static final int SEARCH_STEPS = 5;
    private static final int INNER_SEARCH_STEPS = 255 / SEARCH_STEPS;
    private static final int START_PERIOD_PORT = 1507;
    private static final int END_PERIOD_PORT = 1520;
    private static final ArrayList<String> acceptedRequests = new ArrayList<>();

    public DomainParticipant(String ip, int port, String name) {
        super(ip, port, name);
        this.domainParticipantId = port;                                // Port can be from 1000 to 1500
        dpDiscovered = new ArrayList<>();
        networks = new ArrayList<>();
    }

    private void setIps() {
        String[] ips = getIp().split(" ");
        networks.addAll(Arrays.asList(ips));
        networks.add("127.0.0.1");
    }

    public void enable() {
        Thread dpServer = new Thread(new DPServer());
        Thread dpClient = new Thread(new DPClient());

        setIps();

        dpServer.start();
        dpClient.start();
    }
    public ArrayList<String> getNetworks() {
        return networks;
    }
    private boolean jsonIsValid(String jsonString) {
        try {
            new JSONObject(new JSONTokener(jsonString));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private JSONObject getPeerJsonData() throws JSONException {
        JSONObject jsonObject = new JSONObject();

        JSONArray jsonArray = new JSONArray(getNetworks().toArray());

        jsonObject.put("ipsArr", jsonArray);
        jsonObject.put("idPort", getPort());
        jsonObject.put("name", getName());

        return jsonObject;
    }

    private static class PeerData {
        private final String[] ipsArr;
        private final int idPort;
        private final String name;
        private String ipsStr;

        public PeerData(String[] ips, int idPort, String name) {
            this.ipsArr = ips;
            this.idPort = idPort;
            this.name = name;
            makeIpsStr();
        }

        private void makeIpsStr() {
            for (int i = 0; i < ipsArr.length; i++) {
                ipsStr += ipsArr[i] + " ";
                ipsStr.substring(0, ipsStr.length() - 1);
            }
        }

        public String getIpsStr() {
            return ipsStr;
        }

        public String[] getIpsArr() {
            return ipsArr;
        }

        public int getIdPort() {
            return idPort;
        }

        public String getName() {
            return name;
        }
    }

    private String[] convertIpsToArr(JSONArray jsonArray) throws JSONException {
        String[] ips = new String[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++)
            ips[i] = (String) jsonArray.get(i);
        return ips;
    }

    private PeerData makeJsonData(String msg) throws JSONException {
        JSONObject jsonObject = new JSONObject(msg);

        JSONArray ips = (JSONArray) jsonObject.get("ipsArr");
        int idPort = (int) jsonObject.get("idPort");
        String name = (String) jsonObject.get("name");
        String[] ipsArr = convertIpsToArr(ips);
        return new PeerData(ipsArr, idPort, name);
    }

    private void sendPeerData(Socket socket) throws JSONException, IOException {
        JSONObject jsonObject = getPeerJsonData();

        OutputStream outputStream = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(outputStream, true);
        writer.println(jsonObject);
    }

    private boolean isDPDiscovered(DomainParticipant dp) {
        for (DomainParticipant i : dpDiscovered) {
            if (dp.getDomainParticipantId() == i.getDomainParticipantId() && Objects.equals(dp.getIp(), i.getIp()))
                return true;
        }
        return false;
    }

    private boolean isDPDiscovered(String ip, int id) {
        if (acceptedRequests.contains(ip + ":" + id))
            return true;
        return false;
    }

    private class DPServer implements Runnable {
        ServerSocket serverSocket;
        @Override
        public void run() {
            try {
                this.serverSocket = new ServerSocket(getPort());
                while (true) {
                    Socket socket = this.serverSocket.accept();
                    InputStream inputStream = socket.getInputStream();
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

                    String msg = bufferedReader.readLine();
                    if (jsonIsValid(msg)) {
                        PeerData peerData = makeJsonData(msg);
                        DomainParticipant dp = new DomainParticipant(peerData.getIpsStr(), peerData.getIdPort(), peerData.getName());
                        if (!isDPDiscovered(dp)) {
                            System.out.println();
                            addDPDiscovered(dp);
                            sendPeerData(socket);
                            System.out.println("New peer request found. sent back request");
                        }
                    }
                }
            } catch (IOException | JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private class DPSearchInnerThread implements Runnable {
        private int step;
        private String ipRange;

        public DPSearchInnerThread(int step, String ipRange) {
            this.step = step;
            this.ipRange = ipRange;
        }

        @Override
        public void run() {
            int i;
            for (i = step; i <= 255; i+=INNER_SEARCH_STEPS) {
                String ipSearch = ipRange + i;
                dpSearch(ipSearch);
            }
        }
    }

    private void dpSearch(String ipSearch) {
        for (int p2 = START_PERIOD_PORT; p2 <= END_PERIOD_PORT; p2++) {
            if (networks.contains(ipSearch) && domainParticipantId == p2) continue;             // Not to send packet to itself
            Socket socket;
            try {
                if (!isDPDiscovered(ipSearch, p2)) {
                    socket = new Socket(ipSearch, p2);
                    sendPeerData(socket);
                    System.out.println("Available " + ipSearch + ":" + p2 + ". Request sent.");
                    acceptedRequests.add(ipSearch + ":" + p2);                                  // Add to the list of accepted ones
                    InputStream inputStream = socket.getInputStream();
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

                    String msg = bufferedReader.readLine();
                    PeerData peerData = makeJsonData(msg);
                    DomainParticipant dp = new DomainParticipant(peerData.getIpsStr(), peerData.getIdPort(), peerData.getName());
                    addDPDiscovered(dp);

                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            } catch (IOException e1) {
                System.out.println("Not available " + ipSearch + ":" + p2);
                continue;
            }
        }
    }

    private class DPSearch implements Runnable {

        private String ownIp;
        private String ipRange;
        public DPSearch(String ownIp) {
            this.ownIp = ownIp;
            String[] myIpSplitted = ownIp.split("\\.");
            this.ipRange = myIpSplitted[0] + "." + myIpSplitted[1] + "." + myIpSplitted[2] + ".";
        }

        @Override
        public void run() {
            String ipSearch = "IPSEARCH";
            if (ownIp.compareTo("127.0.0.1") == 0) {
                ipSearch = "127.0.0.1";
                dpSearch(ipSearch);
            } else {
                for (int step = 0; step < SEARCH_STEPS; step++) {
                    Thread dpSearchInnerThread = new Thread(new DPSearchInnerThread(step, ipRange));
                    dpSearchInnerThread.start();
                }
            }
        }

    }

    private class DPClient implements Runnable {
        @Override
        public void run() {
            for (int i = 0; i < packetTimes; i++) {
                ArrayList<String> myIps = getNetworks();
                for (String ip : myIps) {
                    Thread dpSearchThread = new Thread(new DPSearch(ip));
                    dpSearchThread.start();
                }
                try {
                    Thread.sleep(periodTime);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public int getDomainParticipantId() {
        return domainParticipantId;
    }

    @Override
    public void addDPDiscovered(DomainParticipant domainParticipant) {
        dpDiscovered.add(domainParticipant);
    }

    @Override
    public void addDPDiscovered(String ip, int port, String name) {
        DomainParticipant dp = new DomainParticipant(ip, port, name);
        dpDiscovered.add(dp);
    }

    @Override
    public DomainParticipant getDPById(int id) {

        for (DomainParticipant obj : dpDiscovered) {
            if (obj.getDomainParticipantId() == id)
                return obj;
        }

        return null;
    }

    @Override
    public ArrayList<DomainParticipant> getDomainParticipantsDiscovered() {
        return dpDiscovered;
    }
}
