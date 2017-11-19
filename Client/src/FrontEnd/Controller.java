package FrontEnd;

import Algorithm.*;
import Algorithm.JPEG.JpegCompression;
import Network.ChatClient;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.io.*;
import java.util.*;

public class Controller {
    @FXML
    private TextField textBar;
    @FXML
    private TabPane tabs;
    @FXML
    private TextField sendTo;
    @FXML
    private Label alias;
    @FXML
    private VBox encodingHBOX;
    @FXML
    private VBox compressionHBOX;
    @FXML
    private Button attachment;

    /*
        Necessary items
        :: socket - server/client interaction
        :: tabMap - maps aliases to tabs
    */
    private ChatClient socket;
    private Map<String, Tab> tabMap;

    /* GUI items */
    private ToggleGroup compression;
    private ToggleGroup encoding;

    /* Event handlers for GUI */
    private EventHandler<KeyEvent> keyHandler;
    private EventHandler<KeyEvent> sendHandler;
    private EventHandler<Event> tabCloseHandler;

    /* Some temp value I do not remember for */
    private int temporaryIndex = 0;

    /* Algorithms */
    private CompressionAlgorithm lzw;
    private CompressionAlgorithm huffman;
    private CompressionAlgorithm jpeg;
    private EncodeAlgorithm repetition3;
    private EncodeAlgorithm repetition5;
    private EncodeAlgorithm reedMuller;
    private EncodeAlgorithm hamming;

    /**
     * Like constructor for Controller
     * Used to initialize GUI variables, set handlers, etc.
     */
    @FXML
    public void initialize() {
        lzw = new LZW();
        huffman = new Huffman();
        repetition3 = new Repetition(3);
        repetition5 = new Repetition(5);
        reedMuller = new ReedMuller();
        hamming = new Hamming();
        jpeg = new JpegCompression();

        tabMap = new HashMap<>();

        setUpGUIItems();
    }

    /**
     * Set up specific items in GUI and customize event handlers
     */
    private void setUpGUIItems() {
        /* It is possible to close any tab */
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        /* Creation of ToggleGroups */
        compression = new ToggleGroup();
        encoding = new ToggleGroup();

        /* Set up ToggleGroup for encoding/compression radio buttons */
        ObservableList<Node> compressionButtons = compressionHBOX.getChildren();
        ObservableList<Node> encodingnButtons = encodingHBOX.getChildren();

        for (Node n: compressionButtons) ((RadioButton)(n)).setToggleGroup(compression);
        for (Node n: encodingnButtons) ((RadioButton)(n)).setToggleGroup(encoding);

        /* `Enter` handler on `send message to` text bar */
        keyHandler = new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                if (event.getCode() == KeyCode.ENTER) {
                    String alias = sendTo.getText();
                    if (!alias.equals("")) {
                        requestHistory(alias);
                        sendTo.clear();
                    }
                }
            }
        };

        /* Attachment button handler - creates window for choosing files */
        attachment.setOnAction(
                new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(final ActionEvent e) {
                        final FileChooser fileChooser = new FileChooser();
                        final File selectedFile = fileChooser.showOpenDialog(null);
                        String format = getFilenameExtension(selectedFile);
                        if (selectedFile != null) {
                            sendData(selectedFile, format);
                        }
                    }
                }
        );
        /* Send message handler on enter :: Catches Enter key */
        sendHandler = new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                if (event.getCode() == KeyCode.ENTER) {
                    JSONObject json = new JSONObject();
                    json.put("message", textBar.getText());

                    sendData(saveObject(json), "text");
                    textBar.clear();
                }
            }
        };

        /* Closing tab handler :: removes from tabMap */
        tabCloseHandler = new EventHandler<Event>() {
            @Override
            public void handle(Event e)
            {
                Tab t = (Tab)(e.getSource());
                String name = t.getText();
                tabMap.remove(name);
            }
        };

        /* Binding handlers */
        sendTo.setOnKeyReleased(keyHandler);
        textBar.setOnKeyReleased(sendHandler);
    }

    /**
     * Get index of used algorithm in HBOX
     * @param hBox - hbox to work with
     * @return index of item
     */
    public int getVBOXindex(VBox vBox) {
        ObservableList<Node> ol = vBox.getChildren();
        for (int i = 0; i < ol.size(); i++) {
            RadioButton rb = (RadioButton) ol.get(i);
            if (rb.isSelected())
                return i;
        }
        return -1;
    }

    /**
     * Method for getting extension of the file
     * @param link - link to file
     * @return extension
     */
    private String getFilenameExtension(File link) {
        String name = link.getName();
        try {
            return name.substring(name.lastIndexOf(".") + 1);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Method for sending text message from textBar
     */
    public void submitTextMessage() {

        //TODO :: refactor
        try {
            FileWriter fw = new FileWriter("temp_text.data");
            fw.write(textBar.getText());
            fw.close();

            File link = new File("temp_text.data");
            textBar.clear();
            sendData(link, "text");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends File(link) to the user
     * @param link - file to be send
     * @param format - format of the file
     */
    private void sendData(File link, String format) {
        /* Get info about compression/encoding algorithms to use */
        int compression =  getVBOXindex(compressionHBOX);
        int encoding =  getVBOXindex(encodingHBOX);

        /* Get compression/encoding algorithms objects */
        CompressionAlgorithm cAlg = getCompressionAlgorithm(compression);
        EncodeAlgorithm eAlg = getEncodingAlgorithm(encoding);

        /* Compress & encode file */
        long compress_time = System.currentTimeMillis();
        File cFile = cAlg.compress(link);
        compress_time = System.currentTimeMillis() - compress_time;
        long encoding_time = System.currentTimeMillis();
        File eFile = eAlg.encode(cFile);
        encoding_time = System.currentTimeMillis() - encoding_time;
        //TODO :: compressed_size
        /*
            Create JSON string
            ALso transofrms file into some data for JSON
         */

        long initial = link.length();
        long compressed = cFile.length();
        long encoded = eFile.length();
        long stats_size[] = new long[]{initial, compressed, encoded};
        long stats_time[] = new long[]{compress_time, encoding_time};

        String json = createJSON(eFile, format, stats_size, stats_time);

        /* Send by socket JSON string */
        try {
            socket.write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Stores JSONObject on a disk
     * @param json - object to be stored
     * @return link to the stored file
     */
    private File saveObject(JSONObject json) {
        String s = (String) json.get("message");
        String fileName = "temporary" + Integer.toString(temporaryIndex);
        try {
            FileWriter fw = new FileWriter(fileName);
            fw.write(s);
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        File link = new File(fileName);
        return link;
    }

    /**
     * Method for external usage - called by Dialogue window on its' close
     * @param alias - name of the user of this application
     */
    public void receiveAlias(String alias) {
        this.alias.setText(alias);
    }

    /**
     * Method for requesting history about u_name
     * @param u_name - unique identifier of the user
     */
    private void requestHistory(String u_name) {

        Task<Tab> tabTask = new Task<Tab>() {
            @Override
            protected Tab call() throws Exception {
                Tab tab = new Tab(u_name);
                tab.setOnCloseRequest(tabCloseHandler);
                fillTab(u_name, tab);

                return tab;
            }
        };

        tabTask.setOnSucceeded(event -> {
            JSONObject object = createHistoryRequest(u_name);
            if (object != null) {
                try {
                    socket.write(object.toJSONString());
                } catch (IOException e) {
                    System.out.println("Wrong json");
                    e.printStackTrace();
                }
            }

            tabs.getTabs().add(tabTask.getValue());
        });

        Thread th = new Thread(tabTask);
        th.setDaemon(true);
        th.start();
    }

    /**
     * Method for creating json for requesting history of conversation from server by `alias`
     * @param alias - unique identifier of user(chat) on a server
     * @return JSONObject that contains flag `database`
     */
    private JSONObject createHistoryRequest(String alias) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("address", alias);
        jsonObject.put("database", 1);
        return jsonObject;
    }

    /**
     * Method for external service (socket) - called when message came, applies message somewhere in GUI.
     * @param text - jsonObject in a form of String
     */
    public synchronized void receiveMessage(String text) {
        JSONParser parser = new JSONParser();
        try {
            JSONObject obj = (JSONObject) parser.parse(text);
            String room = (String) obj.get("chat");
            /* Select tab to apply to */
            Tab tab = selectDialogue(room);
            if (tab != null)
                /* If tab is found - apply */
                applyMessage(tab, obj);
        } catch (ParseException pe) {
            System.err.println("Invalid json");
            pe.printStackTrace();
        }
    }

    /**
     * Performs applying of a message
     * Client recieves message and this method applies it to some tab
     * @param tab - tab to apply to
     * @param obj - message to apply
     */
    private void applyMessage(Tab tab, JSONObject obj) {
        System.out.println(obj.toJSONString());

        /* Task object is used to perform GUI changes :: necessary for JavaFX */
        Task<Node> task = new Task<Node>() {
            @Override
            public Node call() throws Exception {
                File link = saveObject(obj);
                EncodeAlgorithm eAlg = getEncodingAlgorithm(Integer.parseInt((String)obj.get("encoding")));
                File eFile = eAlg.decode(link);
                CompressionAlgorithm cAlg = getCompressionAlgorithm((int)obj.get("compression"));
                File cFile = cAlg.decompress(eFile);

                String format = (String) obj.get("format");

                Node n;
                //TODO :: New data types
                //TODO :: NOT ONLY FOR STRING SAY NAME
                switch (format)
                {
                    case "text":
                        String data = readFile(cFile);
                        // Добавить декомпрессию
                        System.out.println("Data from file :: " + data);
                        n = new Label(obj.get("address") + " :: " + data);
                        break;
                    default:
                        String fileMessage = String.format("File [%s] :: stored in [%s]", cFile.getName(), cFile.getPath());
                        n = new Label(fileMessage);
                        break;
                }
//        n = new ImageView("@../../image.jpeg");
                System.out.println("Около окончания");
                return n;
            }
        };

        /* If successful task - change the GUI */
        task.setOnSucceeded(event -> {
            ListView lv = (ListView) tab.getContent();
            lv.getItems().add(task.getValue());
        });

        /* Additional thread for running `Runnable task` in a background */
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Select dialogue to apply message
     * @param alias - name of sender
     * @return Tab object to apply to
     */
    private Tab selectDialogue(String alias) {
        if (!tabMap.containsKey(alias))
            requestHistory(alias);
        Tab tab = tabMap.get(alias);

        return tab;
    }

    /**
     * Creates fullfilment of the Tab object and stores it into the dictionary
     * @param alias - name of the tab
     * @param tab - object to fill
     */
    private void fillTab(String alias, Tab tab) {
        ListView lv = new ListView();
        tab.setContent(lv);
        tabMap.put(alias, tab);
    }

    /**
     * Creates json for sending it to server
     * @param link - File bytes of to send
     * @param format - format of the file
     * @param sizes - array of sizes
     * @param time - array of time results
     * @return json in form of a string
     */
    private String createJSON(File link, String format, long sizes[], long time[]) {
        String sendTo = tabs.getSelectionModel().getSelectedItem().getText();

        JSONObject jsonObject = new JSONObject();
        /* Meta information*/
        jsonObject.put("address", sendTo);
        jsonObject.put("compression", Long.toString(getVBOXindex(compressionHBOX)));
        jsonObject.put("encoding", Long.toString(getVBOXindex(encodingHBOX)));
        jsonObject.put("message", readFile(link));
        /* Size stats for server */
        jsonObject.put("initial_size", Long.toString(sizes[0]));
        jsonObject.put("compressed_size", Long.toString(sizes[1]));
        jsonObject.put("encoded_size", Long.toString(sizes[2]));
        /* Time stats for server */
        jsonObject.put("compression_time", Long.toString(time[0]));
        jsonObject.put("encoding_time", Long.toString(time[1]));
        jsonObject.put("format", format);

        // TODO :: LINK INTO BASE64 ?

        return jsonObject.toJSONString();
    }

    /**
     * Reads txt file and returns String
     * @param link
     * @return String
     */
    private String readFile(File link) {
        BufferedInputStream bin = null;
        try {
            bin = new BufferedInputStream(new FileInputStream(link));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        int data;
        StringBuilder factory = new StringBuilder();
        try {
            while((data = bin.read())!=-1){
                factory.append((char)data);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return factory.toString();
    }

    /**
     * Method returns compression algorithm based on `index`
     * @param compressionMethod - index of algorithm :: value is got from frontend
     * @return CompressionAlgorithm object
     */
    private CompressionAlgorithm getCompressionAlgorithm(int compressionMethod) {
        switch (compressionMethod) {
            default:
            case 0:
                return lzw;
            case 1:
                return huffman;
            case 2:
                return jpeg;
        }
    }

    /**
     * Method returns encoding algorithm based on `index`
     * @param encodingMethod - index of algorithm :: value is got from frontend
     * @return EncodeAlgorithm object
     */
    private EncodeAlgorithm getEncodingAlgorithm(int encodingMethod) {
        switch (encodingMethod){
            default:
            case 0:
                return repetition3;
            case 1:
                return repetition5;
            case 2:
                return reedMuller;
            case 3:
                return hamming;
        }
    }

    /**
     * Sets socket to negotiate with - send messages & receive from
     * @param socket - ChatClient object
     */
    public void setSocket(ChatClient socket) {
        this.socket = socket;
    }
}