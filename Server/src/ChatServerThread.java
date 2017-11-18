import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.Socket;
import java.util.Random;

public class ChatServerThread extends Thread {
    private ChatServer server = null;
    private Socket socket = null;
    private int ID = -1;
    private DataInputStream streamIn = null;
    private DataOutputStream streamOut = null;

    protected ChatServerThread(ChatServer _server, Socket _socket) {
        super();
        server = _server;
        socket = _socket;
        ID = socket.getPort();
    }

    public void send(String msg) {
        try {
            streamOut.writeUTF(msg);
            streamOut.flush();
        } catch (IOException ioe) {
            System.out.println(ID + " ERROR sending: " + ioe.getMessage());
            server.remove(ID);
            stop();
        }
    }

    public int getID() {
        return ID;
    }

    public void run() {
        System.out.println("Network.Server Thread " + ID + " running.");
        while (true) {
            try {
                String s = streamIn.readUTF();  //read data
                System.out.println(s);
                JSONObject obj = null;  //Create and setup JSON object
                org.json.simple.parser.JSONParser parser = new org.json.simple.parser.JSONParser();
                try {
                    obj = (JSONObject) parser.parse(s);
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                DB db = new DB();
                //If it is for database synchronisation
                if (obj.containsKey("database")) {
                    System.out.println("Got database request.");
                    db.makeSelection(getName(), (String) obj.get("address"));
                    if (!db.hasNext()) {
                        JSONObject send = new JSONObject();
                        String receiver = (String) obj.get("address");
                        send.put("chat", receiver);
                        send.put("address", "null");
                        send.put("format", "null");
                        send.put("message", "null");
                        server.handle(send.toJSONString(), getName());
                    } else {
                        while (db.hasNext()) {
                            JSONObject send = new JSONObject();
                            String receiver = (String) obj.get("address");
                            send.put("chat", receiver);
                            send.put("address", db.get("user"));
                            send.put("format", db.get("format"));
                            send.put("message", db.get("content"));

                            System.out.println(send.toJSONString());
                            server.handle(send.toJSONString(), getName());
                            db.next();
                        }
                    }
                    db.reset();
                } else {
//                    Or send message to user.
//                    (size, compressed, encoded, encodedTime, compressedTime,
//                     user, recipient, compression, coding,
//                     format, content)
                    JSONObject send = new JSONObject();
                    String receiver = (String) obj.get("address");

                    send.put("chat", obj.get("address"));
                    send.put("address", getName());
                    send.put("format", obj.get("format"));
                    Object[] noise = makeSomeNoise((String) obj.get("message"), 0.03);
                    send.put("message", (String) noise[0]);
                    send.put("compression", obj.get("compression"));
                    send.put("encoding", obj.get("encoding"));

                    db.insert((String) obj.get("encoded_size"), (String) obj.get("compressed_size"), (int) noise[1],
                            -1, -1, -1, getName(), (String) obj.get("address"),
                            (String) obj.get("compression"), (String) obj.get("encoding"),
                            (String) obj.get("format"), (String) obj.get("message"));


                    server.handle(send.toJSONString(), getName());

                    send.put("chat", getName());
                    send.put("address", getName());

                    server.handle(send.toJSONString(), receiver);
                }
                db.close();
            } catch (IOException ioe) {
                System.out.println(getName() + " ERROR reading: " + ioe.getMessage());
                server.remove(ID);
                stop();
            }
        }
    }

    public Object[] makeSomeNoise(String message, double thr) {
        Random rand = new Random();
        int n = 0;
        char[] seq = message.toCharArray();
        byte store[] = new byte[seq.length];
        for (int i = 0; i < seq.length; i++) {
            store[i] = (byte) seq[i];
        }
        for (int i = 0; i < store.length; ++i) {
            for (int j = 0; j < 8; ++j) {
                if(rand.nextDouble() < thr){
                    store[i] ^= (byte)~(store[i] & (1 << j));
                    ++n;
                }

            }
        }
        for (int i = 0; i < seq.length; i++) {
            seq[i] = (char) store[i];
        }
        Object[] res = {new String(seq), n};
        return res;
    }

    public void open() throws IOException {
        streamIn = new DataInputStream(new
                BufferedInputStream(socket.getInputStream()));
        streamOut = new DataOutputStream(new
                BufferedOutputStream(socket.getOutputStream()));
        System.out.println("Waiting for a name...");
        setName(streamIn.readUTF());
        System.out.println(getName() + " has logged in.");
    }

    public void close() throws IOException {
        if (socket != null) socket.close();
        if (streamIn != null) streamIn.close();
        if (streamOut != null) streamOut.close();
    }
}